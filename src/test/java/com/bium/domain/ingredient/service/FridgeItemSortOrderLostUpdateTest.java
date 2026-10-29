package com.bium.domain.ingredient.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.ingredient.dto.FridgeItemPositionRequest;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.ingredient.repository.FridgeItemRepository;
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

/**
 * 순번 변경은 버전을 올리지 않으므로, 순번이 바뀌기 전에 재료를 불러 이름만 고쳐도 바뀐 순번이 그대로 남아야 한다.
 * 두 트랜잭션이 필요해서 테스트 트랜잭션 없이 직접 연다.
 */
@SpringBootTest
class FridgeItemSortOrderLostUpdateTest {

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private EntityManager em;

	@Autowired
	private FridgeItemRepository fridgeItemRepository;

	@Autowired
	private FridgeItemService fridgeItemService;

	private TransactionTemplate otherMember;
	private Long userId;
	private Long fridgeId;
	private Long tofuId;
	private Long milkId;

	@BeforeEach
	void setUp() {
		transactionTemplate.executeWithoutResult(status -> {
			User user = em.merge(new User("sort-member", "password", "멤버"));
			Fridge fridge = em.merge(new Fridge("공유 냉장고"));
			em.persist(new FridgeMember(fridge, user, MemberRole.MEMBER));
			FridgeItem tofu = item(fridge, "두부", 0);
			FridgeItem milk = item(fridge, "우유", 1);
			userId = user.getId();
			fridgeId = fridge.getId();
			tofuId = tofu.getId();
			milkId = milk.getId();
		});

		otherMember = new TransactionTemplate(transactionTemplate.getTransactionManager());
		otherMember.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Test
	void 순번이_바뀌기_전에_불러온_재료의_이름을_고쳐도_바뀐_순번을_덮어쓰지_않는다() {
		transactionTemplate.executeWithoutResult(status -> {
			FridgeItem tofu = fridgeItemRepository.findById(tofuId).orElseThrow();
			// 내가 두부 이름을 고치는 사이 다른 멤버가 우유를 맨 앞으로 옮겨 두부가 두 번째로 밀린다
			otherMember.executeWithoutResult(otherStatus -> fridgeItemService.changePosition(userId, milkId,
				new FridgeItemPositionRequest(StorageType.FRIDGE, SectionType.CENTER, 0, 0)));
			tofu.update("순두부", null, null);
		});

		List<String> names = transactionTemplate.execute(status -> em.createQuery(
				"select i.name from FridgeItem i where i.fridge.id = :fridgeId order by i.sortOrder, i.id", String.class)
			.setParameter("fridgeId", fridgeId)
			.getResultList());
		assertThat(names).containsExactly("우유", "순두부");
		List<Integer> sortOrders = transactionTemplate.execute(status -> em.createQuery(
				"select i.sortOrder from FridgeItem i where i.fridge.id = :fridgeId", Integer.class)
			.setParameter("fridgeId", fridgeId)
			.getResultList());
		assertThat(sortOrders).doesNotHaveDuplicates();
	}

	private FridgeItem item(Fridge fridge, String name, int sortOrder) {
		FridgeItem item = FridgeItem.builder()
			.fridge(fridge)
			.name(name)
			.quantity(BigDecimal.ONE)
			.storage(StorageType.FRIDGE)
			.section(SectionType.CENTER)
			.sortOrder(sortOrder)
			.build();
		em.persist(item);
		return item;
	}

	@AfterEach
	void tearDown() {
		transactionTemplate.executeWithoutResult(status -> {
			em.createQuery("delete from ActivityLog where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from FridgeItem where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from FridgeMember where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from Fridge where id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from User where id = :id").setParameter("id", userId).executeUpdate();
		});
	}

}
