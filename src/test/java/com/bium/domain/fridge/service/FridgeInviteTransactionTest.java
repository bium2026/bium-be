package com.bium.domain.fridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import com.bium.domain.fridge.dto.FridgeJoinRequest;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.user.entity.User;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import jakarta.persistence.EntityManager;

/**
 * 막은 초대 코드가 실제로 저장돼서 다음 요청에서도 막히는지 본다.
 * 테스트 트랜잭션 하나로 돌리면 메모리에 남은 엔티티를 다시 읽어 저장 여부를 알 수 없어서, 서비스 호출마다 트랜잭션을 따로 연다.
 */
@SpringBootTest
class FridgeInviteTransactionTest {

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private EntityManager em;

	@Autowired
	private FridgeInviteService fridgeInviteService;

	@Autowired
	private FridgeMemberService fridgeMemberService;

	private Long ownerId;
	private Long guestId;
	private Long fridgeId;

	@BeforeEach
	void setUp() {
		transactionTemplate.executeWithoutResult(status -> {
			User owner = em.merge(new User("invite-owner", "password", "윤호"));
			User guest = em.merge(new User("invite-guest", "password", "선재"));
			Fridge fridge = em.merge(new Fridge("윤호의 냉장고"));
			em.persist(new FridgeMember(fridge, owner, MemberRole.OWNER));
			ownerId = owner.getId();
			guestId = guest.getId();
			fridgeId = fridge.getId();
		});
	}

	@Test
	void 새_코드를_만들면_전에_만든_코드는_저장된_뒤에도_막혀_있다() {
		String oldCode = fridgeInviteService.create(ownerId, fridgeId).code();
		String newCode = fridgeInviteService.create(ownerId, fridgeId).code();

		assertThatThrownBy(() -> fridgeInviteService.join(guestId, new FridgeJoinRequest(oldCode)))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.INVITE_EXPIRED);
		assertThat(fridgeInviteService.join(guestId, new FridgeJoinRequest(newCode)).role())
			.isEqualTo(MemberRole.MEMBER);
		assertThat(fridgeInviteService.getActive(ownerId, fridgeId).code()).isEqualTo(newCode);
	}

	@Test
	void 내보낸_멤버는_그때_쓰던_코드로_다시_들어올_수_없다() {
		String code = fridgeInviteService.create(ownerId, fridgeId).code();
		fridgeInviteService.join(guestId, new FridgeJoinRequest(code));

		fridgeMemberService.remove(ownerId, fridgeId, guestId);

		assertThatThrownBy(() -> fridgeInviteService.join(guestId, new FridgeJoinRequest(code)))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.INVITE_EXPIRED);
		assertThat(fridgeInviteService.getActive(ownerId, fridgeId)).isNull();
	}

	@AfterEach
	void tearDown() {
		transactionTemplate.executeWithoutResult(status -> {
			em.createQuery("delete from ActivityLog where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from FridgeInvite where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from FridgeMember where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from Fridge where id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from User where id in (:ids)")
				.setParameter("ids", List.of(ownerId, guestId))
				.executeUpdate();
		});
	}

}
