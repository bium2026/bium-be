package com.bium.domain.ingredient.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.bium.domain.user.entity.User;

import jakarta.persistence.PersistenceException;

@DataJpaTest
class IngredientCatalogTest {

	@Autowired
	private TestEntityManager em;

	@Test
	void 공용_도감에는_같은_이름을_두_번_저장할_수_없다() {
		em.persistAndFlush(catalog(null, "양파"));

		assertThatThrownBy(() -> em.persistAndFlush(catalog(null, "양파")))
			.isInstanceOf(PersistenceException.class);
	}

	@Test
	void 사용자_커스텀_도감은_공용_도감이나_다른_사용자와_이름이_같아도_저장된다() {
		User user1 = em.persist(new User("user1", "password", "유저1"));
		User user2 = em.persist(new User("user2", "password", "유저2"));

		em.persistAndFlush(catalog(null, "양파"));
		em.persistAndFlush(catalog(user1, "양파"));
		em.persistAndFlush(catalog(user2, "양파"));
	}

	@Test
	void 같은_사용자의_커스텀_도감에는_같은_이름을_두_번_저장할_수_없다() {
		User user = em.persist(new User("user1", "password", "유저1"));
		em.persistAndFlush(catalog(user, "양파"));

		assertThatThrownBy(() -> em.persistAndFlush(catalog(user, "양파")))
			.isInstanceOf(PersistenceException.class);
	}

	@Test
	void owner_key는_공용_도감이면_0_커스텀이면_사용자_id로_채워진다() {
		User user = em.persist(new User("user1", "password", "유저1"));
		Long publicId = em.persistAndGetId(catalog(null, "양파"), Long.class);
		Long customId = em.persistAndGetId(catalog(user, "대파"), Long.class);
		em.flush();
		em.clear();

		assertThat(em.find(IngredientCatalog.class, publicId).getOwnerKey()).isZero();
		assertThat(em.find(IngredientCatalog.class, customId).getOwnerKey()).isEqualTo(user.getId());
	}

	private IngredientCatalog catalog(User owner, String name) {
		return IngredientCatalog.builder()
			.owner(owner)
			.name(name)
			.defaultStorage(StorageType.FRIDGE)
			.build();
	}

}
