package com.bium.domain.feed.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import com.bium.domain.feed.dto.FeedReactionResponse;
import com.bium.domain.feed.entity.FeedLike;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

/**
 * 같은 사용자가 좋아요를 동시에 두 번 보내도 오류 없이 한 번만 저장돼야 한다.
 * 요청마다 트랜잭션을 따로 열어야 해서 테스트 트랜잭션 없이 직접 연다.
 */
@SpringBootTest
class FeedReactionConcurrencyTest {

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private EntityManager em;

	@Autowired
	private FeedReactionService feedReactionService;

	private Long ownerId;
	private Long viewerId;
	private Long fridgeId;

	@BeforeEach
	void setUp() {
		transactionTemplate.executeWithoutResult(status -> {
			User owner = em.merge(new User("feed-owner", "password", "강희"));
			User viewer = em.merge(new User("feed-viewer", "password", "윤호"));
			Fridge fridge = em.merge(new Fridge("강희의 냉장고"));
			em.persist(new FridgeMember(fridge, owner, MemberRole.OWNER));
			fridge.changeVisibility(true);
			ownerId = owner.getId();
			viewerId = viewer.getId();
			fridgeId = fridge.getId();
		});
	}

	@Test
	void 다른_요청이_먼저_넣은_좋아요와_겹쳐도_오류_없이_한_번만_저장한다() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch inserted = new CountDownLatch(1);
		CountDownLatch commit = new CountDownLatch(1);
		// 같은 사용자의 앞선 요청이 좋아요를 넣고 커밋하기 직전에 멈춘다
		Future<?> first = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
			em.persist(new FeedLike(em.getReference(User.class, viewerId), em.getReference(Fridge.class, fridgeId)));
			em.flush();
			inserted.countDown();
			try {
				commit.await(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}));
		assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();

		// 늦은 요청은 아직 커밋되지 않은 좋아요를 보지 못하고 넣다가, 앞선 요청이 커밋되면 기본 키에 걸린다
		Future<FeedReactionResponse> second = executor.submit(() -> feedReactionService.like(viewerId, fridgeId));
		Thread.sleep(300);
		commit.countDown();
		first.get(10, TimeUnit.SECONDS);
		FeedReactionResponse response = second.get(10, TimeUnit.SECONDS);
		executor.shutdown();

		assertThat(response.likeCount()).isEqualTo(1);
		assertThat(response.liked()).isTrue();
		Long likes = transactionTemplate.execute(status -> em.createQuery(
				"select count(l) from FeedLike l where l.fridge.id = :id", Long.class)
			.setParameter("id", fridgeId)
			.getSingleResult());
		assertThat(likes).isEqualTo(1);
	}

	@AfterEach
	void tearDown() {
		transactionTemplate.executeWithoutResult(status -> {
			em.createQuery("delete from FeedLike where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from FeedSave where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from FridgeMember where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from Fridge where id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from User where id in (:ids)").setParameter("ids", List.of(ownerId, viewerId))
				.executeUpdate();
		});
	}

}
