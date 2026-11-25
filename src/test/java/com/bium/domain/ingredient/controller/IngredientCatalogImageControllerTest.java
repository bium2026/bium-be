package com.bium.domain.ingredient.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.auth.service.JwtProvider;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IngredientCatalogImageControllerTest {

	private static final String IMAGE_URL_PREFIX = "/api/ingredient-images/";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager em;

	@Autowired
	private JwtProvider jwtProvider;

	private User user;
	private IngredientCatalog tofu;
	private String token;
	private String otherToken;

	@BeforeEach
	void setUp() {
		user = em.merge(new User("user", "password", "사용자"));
		User other = em.merge(new User("other", "password", "다른사람"));
		tofu = catalog(user, "두부");
		token = jwtProvider.createAccessToken(user.getId());
		otherToken = jwtProvider.createAccessToken(other.getId());
	}

	@Test
	void 내_도감_재료에_사진을_올리면_로그인_없이_받을_수_있는_PNG_주소를_준다() throws Exception {
		String imageUrl = upload(tofu.getId(), token, png(600, 600))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.emoji").value("🥬"))
			.andExpect(jsonPath("$.data.imageUrl").value(startsWith(IMAGE_URL_PREFIX)))
			.andReturn().getResponse().getContentAsString();

		String path = readImageUrl(imageUrl);
		byte[] body = mockMvc.perform(get(path))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, startsWith("max-age=")))
			.andReturn().getResponse().getContentAsByteArray();
		BufferedImage stored = ImageIO.read(new ByteArrayInputStream(body));
		assertThat(stored.getWidth()).isEqualTo(256);
	}

	@Test
	void 사진을_바꾸면_새_주소를_주고_옛_사진은_지운다() throws Exception {
		String first = readImageUrl(upload(tofu.getId(), token, png(10, 10)).andReturn().getResponse()
			.getContentAsString());
		String second = readImageUrl(upload(tofu.getId(), token, png(20, 20)).andReturn().getResponse()
			.getContentAsString());

		assertThat(second).isNotEqualTo(first);
		mockMvc.perform(get(first)).andExpect(status().isNotFound());
		mockMvc.perform(get(second)).andExpect(status().isOk());
		assertThat(countImages()).isEqualTo(1);
	}

	@Test
	void 사진을_지우면_imageUrl이_null이_되고_사진도_지운다() throws Exception {
		String path = readImageUrl(upload(tofu.getId(), token, png(10, 10)).andReturn().getResponse()
			.getContentAsString());

		mockMvc.perform(delete("/api/ingredient-catalogs/{catalogId}/image", tofu.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.imageUrl").isEmpty())
			.andExpect(jsonPath("$.data.emoji").value("🥬"));
		mockMvc.perform(get(path)).andExpect(status().isNotFound());
	}

	@Test
	void 다른_사람의_재료에는_사진을_올릴_수_없다() throws Exception {
		upload(tofu.getId(), otherToken, png(10, 10))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("CATALOG_NOT_FOUND"));
		assertThat(countImages()).isZero();
	}

	@Test
	void 기본_재료에_올린_사진은_올린_사람의_도감에만_보인다() throws Exception {
		IngredientCatalog onion = catalog(null, "기본양파");

		String path = readImageUrl(upload(onion.getId(), token, png(10, 10))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.basic").value(true))
			.andReturn().getResponse().getContentAsString());
		em.flush();
		em.clear();

		mockMvc.perform(get("/api/ingredient-catalogs").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(jsonPath("$.data.ingredients[?(@.name == '기본양파')].imageUrl").value(path));
		mockMvc.perform(get("/api/ingredient-catalogs").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
			.andExpect(jsonPath("$.data.ingredients[?(@.name == '기본양파')].imageUrl").value(contains(nullValue())))
			.andExpect(jsonPath("$.data.ingredients[?(@.name == '기본양파')].emoji").value("🥬"));
	}

	@Test
	void 같은_냉장고의_기본_재료도_사진을_올린_사람에게만_사진으로_보인다() throws Exception {
		IngredientCatalog onion = catalog(null, "기본양파");
		User other = em.createQuery("select u from User u where u.email = 'other'", User.class).getSingleResult();
		Fridge fridge = em.merge(new Fridge("함께 쓰는 냉장고"));
		em.persist(new FridgeMember(fridge, user, MemberRole.OWNER));
		em.persist(new FridgeMember(fridge, other, MemberRole.MEMBER));
		persistItem(fridge, onion, "양파");
		String path = readImageUrl(upload(onion.getId(), token, png(10, 10)).andReturn().getResponse()
			.getContentAsString());
		em.flush();
		em.clear();

		mockMvc.perform(get("/api/fridges/{fridgeId}/items", fridge.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(jsonPath("$.data.fridge.center[0].imageUrl").value(path));
		mockMvc.perform(get("/api/fridges/{fridgeId}/items", fridge.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
			.andExpect(jsonPath("$.data.fridge.center[0].imageUrl").isEmpty())
			.andExpect(jsonPath("$.data.fridge.center[0].emoji").value("🥬"));
	}

	@Test
	void 기본_재료_사진을_바꾸거나_지우면_내_사진만_바뀌고_다른_사람의_사진은_남는다() throws Exception {
		IngredientCatalog onion = catalog(null, "기본양파");
		String othersPath = readImageUrl(upload(onion.getId(), otherToken, png(10, 10)).andReturn().getResponse()
			.getContentAsString());
		String first = readImageUrl(upload(onion.getId(), token, png(10, 10)).andReturn().getResponse()
			.getContentAsString());
		String second = readImageUrl(upload(onion.getId(), token, png(20, 20)).andReturn().getResponse()
			.getContentAsString());

		assertThat(second).isNotEqualTo(first);
		mockMvc.perform(get(first)).andExpect(status().isNotFound());
		assertThat(countImages()).isEqualTo(2);

		mockMvc.perform(delete("/api/ingredient-catalogs/{catalogId}/image", onion.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.imageUrl").isEmpty());
		mockMvc.perform(get(second)).andExpect(status().isNotFound());
		mockMvc.perform(get(othersPath)).andExpect(status().isOk());
	}

	@Test
	void JPG_PNG가_아니면_400을_주고_사진이_없어도_400을_준다() throws Exception {
		upload(tofu.getId(), token, new MockMultipartFile("image", "a.png", "image/png", "not image".getBytes()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INGREDIENT_IMAGE"));
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/ingredient-catalogs/{catalogId}/image", tofu.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INGREDIENT_IMAGE"));
	}

	@Test
	void 로그인하지_않으면_사진을_올릴_수_없다() throws Exception {
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/ingredient-catalogs/{catalogId}/image", tofu.getId())
				.file(png(10, 10)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void 없는_사진_주소는_만료된_토큰을_붙여도_404를_준다() throws Exception {
		mockMvc.perform(get(IMAGE_URL_PREFIX + "no-such-key")
				.header(HttpHeaders.AUTHORIZATION, "Bearer expired.token.value"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("INGREDIENT_IMAGE_NOT_FOUND"));
	}

	@Test
	void 사진을_올린_도감_재료로_넣은_냉장고_재료에도_사진_주소를_준다() throws Exception {
		Fridge fridge = em.merge(new Fridge("사용자의 냉장고"));
		em.persist(new FridgeMember(fridge, user, MemberRole.OWNER));
		persistItem(fridge, tofu, "두부");
		String path = readImageUrl(upload(tofu.getId(), token, png(10, 10)).andReturn().getResponse()
			.getContentAsString());
		em.flush();
		em.clear();

		mockMvc.perform(get("/api/fridges/{fridgeId}/items", fridge.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.fridge.center[0].imageUrl").value(path))
			.andExpect(jsonPath("$.data.fridge.center[0].emoji").value("🥬"));
	}

	@Test
	void 도감_재료를_지우면_사진도_지운다() throws Exception {
		String path = readImageUrl(upload(tofu.getId(), token, png(10, 10)).andReturn().getResponse()
			.getContentAsString());

		mockMvc.perform(delete("/api/ingredient-catalogs/{catalogId}", tofu.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(get(path)).andExpect(status().isNotFound());
	}

	private void persistItem(Fridge fridge, IngredientCatalog catalog, String name) {
		em.persist(FridgeItem.builder()
			.fridge(fridge)
			.catalog(catalog)
			.name(name)
			.emoji("🥬")
			.quantity(BigDecimal.ONE)
			.storage(StorageType.FRIDGE)
			.section(SectionType.CENTER)
			.sortOrder(0)
			.expiresAt(LocalDate.of(2026, 12, 1))
			.build());
	}

	private ResultActions upload(Long catalogId, String accessToken, MockMultipartFile file) throws Exception {
		return mockMvc.perform(multipart(HttpMethod.PUT, "/api/ingredient-catalogs/{catalogId}/image", catalogId)
			.file(file)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
	}

	private String readImageUrl(String responseBody) {
		int start = responseBody.indexOf(IMAGE_URL_PREFIX);
		return responseBody.substring(start, responseBody.indexOf('"', start));
	}

	private long countImages() {
		em.flush();
		return em.createQuery("select count(i) from IngredientImage i", Long.class).getSingleResult();
	}

	private IngredientCatalog catalog(User owner, String name) {
		IngredientCatalog catalog = IngredientCatalog.builder().owner(owner).name(name).emoji("🥬").build();
		em.persist(catalog);
		return catalog;
	}

	private static MockMultipartFile png(int width, int height) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", output);
		return new MockMultipartFile("image", "photo.png", "image/png", output.toByteArray());
	}

}
