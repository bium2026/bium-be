package com.bium.domain.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.feed.dto.FeedCommentCreateRequest;
import com.bium.domain.feed.dto.FeedCommentPageResponse;
import com.bium.domain.feed.dto.FeedCommentResponse;
import com.bium.domain.feed.entity.FeedComment;
import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.user.entity.User;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class FeedCommentServiceTest {

	@Autowired
	private FeedCommentService feedCommentService;

	@Autowired
	private EntityManager em;

	private User owner;
	private User member;
	private User viewer;
	private User stranger;
	private Fridge fridge;

	@BeforeEach
	void setUp() {
		owner = em.merge(new User("owner", "password", "강희"));
		member = em.merge(new User("member", "password", "선재"));
		viewer = em.merge(new User("viewer", "password", "윤호"));
		stranger = em.merge(new User("stranger", "password", "도윤"));
		fridge = em.merge(new Fridge("강희의 냉장고"));
		em.persist(new FridgeMember(fridge, owner, MemberRole.OWNER));
		em.persist(new FridgeMember(fridge, member, MemberRole.MEMBER));
		fridge.changeVisibility(true);
	}

	@Test
	void 공개한_냉장고에_음식을_추천하는_코멘트를_남긴다() {
		FeedCommentResponse response = feedCommentService.create(viewer.getId(), fridge.getId(),
			new FeedCommentCreateRequest(" 두부가 많네요! ", " 마파두부 "));

		assertThat(response.authorNickname()).isEqualTo("윤호");
		assertThat(response.content()).isEqualTo("두부가 많네요!");
		assertThat(response.dishName()).isEqualTo("마파두부");
		assertThat(response.mine()).isTrue();
		assertThat(response.deletable()).isTrue();
		assertThat(response.createdAt()).isNotNull();
	}

	@Test
	void 음식_이름이_공백뿐이면_추천하지_않은_코멘트로_남긴다() {
		FeedCommentResponse response = feedCommentService.create(viewer.getId(), fridge.getId(),
			new FeedCommentCreateRequest("구경 잘 했어요", "　"));

		assertThat(response.dishName()).isNull();
	}

	@Test
	void 공개하지_않은_냉장고에는_코멘트를_남길_수_없고_없는_냉장고와_같은_오류를_준다() {
		fridge.changeVisibility(false);

		assertThatThrownBy(() -> feedCommentService.create(viewer.getId(), fridge.getId(),
			new FeedCommentCreateRequest("구경 잘 했어요", null)))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.FRIDGE_NOT_FOUND);
	}

	@Test
	void 코멘트를_최신순으로_페이지를_나눠_보여_준다() {
		for (int i = 1; i <= FeedCommentService.PAGE_SIZE + 1; i++) {
			comment(viewer, "코멘트 " + i);
		}

		FeedCommentPageResponse first = feedCommentService.getComments(stranger.getId(), fridge.getId(), null);
		assertThat(first.comments()).hasSize(FeedCommentService.PAGE_SIZE);
		assertThat(first.comments().get(0).content()).isEqualTo("코멘트 " + (FeedCommentService.PAGE_SIZE + 1));
		assertThat(first.nextCursor()).isNotNull();

		FeedCommentPageResponse second = feedCommentService.getComments(stranger.getId(), fridge.getId(),
			first.nextCursor());
		assertThat(second.comments()).extracting(FeedCommentResponse::content).containsExactly("코멘트 1");
		assertThat(second.nextCursor()).isNull();
	}

	@Test
	void 냉장고_주인에게만_남이_쓴_코멘트를_지울_수_있다고_알려_준다() {
		comment(viewer, "구경 잘 했어요");

		List<FeedCommentResponse> asOwner = feedCommentService.getComments(owner.getId(), fridge.getId(), null)
			.comments();
		List<FeedCommentResponse> asMember = feedCommentService.getComments(member.getId(), fridge.getId(), null)
			.comments();

		assertThat(asOwner.get(0).mine()).isFalse();
		assertThat(asOwner.get(0).deletable()).isTrue();
		assertThat(asMember.get(0).deletable()).isFalse();
	}

	@Test
	void 공개하지_않은_냉장고의_코멘트는_멤버만_본다() {
		comment(viewer, "구경 잘 했어요");
		fridge.changeVisibility(false);

		assertThat(feedCommentService.getComments(member.getId(), fridge.getId(), null).comments()).hasSize(1);
		assertThatThrownBy(() -> feedCommentService.getComments(viewer.getId(), fridge.getId(), null))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.FRIDGE_NOT_FOUND);
	}

	@Test
	void 쓴_사람은_자기_코멘트를_지운다() {
		FeedComment comment = comment(viewer, "구경 잘 했어요");

		feedCommentService.delete(viewer.getId(), fridge.getId(), comment.getId());

		assertThat(em.find(FeedComment.class, comment.getId())).isNull();
	}

	@Test
	void 냉장고_주인은_다른_사람이_쓴_코멘트도_지운다() {
		FeedComment comment = comment(viewer, "구경 잘 했어요");

		feedCommentService.delete(owner.getId(), fridge.getId(), comment.getId());

		assertThat(em.find(FeedComment.class, comment.getId())).isNull();
	}

	@Test
	void 쓴_사람도_주인도_아니면_코멘트를_지울_수_없다() {
		FeedComment comment = comment(viewer, "구경 잘 했어요");

		assertThatThrownBy(() -> feedCommentService.delete(member.getId(), fridge.getId(), comment.getId()))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.FEED_COMMENT_DELETE_FORBIDDEN);
		assertThatThrownBy(() -> feedCommentService.delete(stranger.getId(), fridge.getId(), comment.getId()))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.FEED_COMMENT_DELETE_FORBIDDEN);
		assertThat(em.find(FeedComment.class, comment.getId())).isNotNull();
	}

	@Test
	void 냉장고가_비공개로_바뀌어도_쓴_사람은_자기_코멘트를_지운다() {
		FeedComment comment = comment(viewer, "구경 잘 했어요");
		fridge.changeVisibility(false);

		feedCommentService.delete(viewer.getId(), fridge.getId(), comment.getId());

		assertThat(em.find(FeedComment.class, comment.getId())).isNull();
	}

	@Test
	void 냉장고가_비공개로_바뀌어도_주인은_다른_사람이_쓴_코멘트를_지운다() {
		FeedComment comment = comment(viewer, "구경 잘 했어요");
		fridge.changeVisibility(false);

		feedCommentService.delete(owner.getId(), fridge.getId(), comment.getId());

		assertThat(em.find(FeedComment.class, comment.getId())).isNull();
	}

	@Test
	void 비공개로_바뀐_냉장고의_남의_코멘트는_멤버가_아니면_있는지도_알_수_없다() {
		FeedComment comment = comment(viewer, "구경 잘 했어요");
		fridge.changeVisibility(false);

		assertThatThrownBy(() -> feedCommentService.delete(stranger.getId(), fridge.getId(), comment.getId()))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.FRIDGE_NOT_FOUND);
		assertThatThrownBy(() -> feedCommentService.delete(member.getId(), fridge.getId(), comment.getId()))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.FEED_COMMENT_DELETE_FORBIDDEN);
		assertThat(em.find(FeedComment.class, comment.getId())).isNotNull();
	}

	@Test
	void 비공개로_바뀐_냉장고의_자기_코멘트도_다른_냉장고_주소로는_지울_수_없다() {
		FeedComment comment = comment(viewer, "구경 잘 했어요");
		fridge.changeVisibility(false);
		Fridge other = em.merge(new Fridge("도윤의 냉장고"));
		em.persist(new FridgeMember(other, stranger, MemberRole.OWNER));

		assertThatThrownBy(() -> feedCommentService.delete(viewer.getId(), other.getId(), comment.getId()))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.FRIDGE_NOT_FOUND);
		assertThat(em.find(FeedComment.class, comment.getId())).isNotNull();
	}

	@Test
	void 다른_냉장고의_코멘트_id로는_지울_수_없다() {
		FeedComment comment = comment(viewer, "구경 잘 했어요");
		Fridge other = em.merge(new Fridge("윤호의 냉장고"));
		em.persist(new FridgeMember(other, viewer, MemberRole.OWNER));
		other.changeVisibility(true);

		assertThatThrownBy(() -> feedCommentService.delete(viewer.getId(), other.getId(), comment.getId()))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.FEED_COMMENT_NOT_FOUND);
	}

	@Test
	void 쓴_사람은_자기_코멘트의_내용과_추천한_음식을_고친다() {
		FeedComment comment = comment(viewer, "구경 잘 했어요");

		FeedCommentResponse response = feedCommentService.update(viewer.getId(), fridge.getId(), comment.getId(),
			new FeedCommentCreateRequest(" 두부가 많네요! ", " 마파두부 "));

		assertThat(response.content()).isEqualTo("두부가 많네요!");
		assertThat(response.dishName()).isEqualTo("마파두부");
		assertThat(response.mine()).isTrue();
		em.flush();
		em.clear();
		FeedComment saved = em.find(FeedComment.class, comment.getId());
		assertThat(saved.getContent()).isEqualTo("두부가 많네요!");
		assertThat(saved.getDishName()).isEqualTo("마파두부");
	}

	@Test
	void 음식_이름을_비워_고치면_추천하지_않은_코멘트가_된다() {
		FeedComment comment = new FeedComment(fridge, viewer, "두부가 많네요", "마파두부");
		em.persist(comment);

		FeedCommentResponse response = feedCommentService.update(viewer.getId(), fridge.getId(), comment.getId(),
			new FeedCommentCreateRequest("두부가 많네요", " "));

		assertThat(response.dishName()).isNull();
	}

	@Test
	void 냉장고_주인이라도_남이_쓴_코멘트는_고칠_수_없다() {
		FeedComment comment = comment(viewer, "구경 잘 했어요");

		for (User other : List.of(owner, member, stranger)) {
			assertThatThrownBy(() -> feedCommentService.update(other.getId(), fridge.getId(), comment.getId(),
				new FeedCommentCreateRequest("바꿨어요", null)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.FEED_COMMENT_EDIT_FORBIDDEN);
		}
		em.flush();
		em.clear();
		assertThat(em.find(FeedComment.class, comment.getId()).getContent()).isEqualTo("구경 잘 했어요");
	}

	@Test
	void 비공개로_바뀐_냉장고의_코멘트는_멤버만_고치고_볼_수_없는_사람에게는_없는_냉장고와_같은_오류를_준다() {
		FeedComment memberComment = comment(member, "우유가 있네요");
		FeedComment viewerComment = comment(viewer, "구경 잘 했어요");
		fridge.changeVisibility(false);

		FeedCommentResponse response = feedCommentService.update(member.getId(), fridge.getId(),
			memberComment.getId(), new FeedCommentCreateRequest("우유가 많네요", null));
		assertThat(response.content()).isEqualTo("우유가 많네요");
		assertThatThrownBy(() -> feedCommentService.update(viewer.getId(), fridge.getId(), viewerComment.getId(),
			new FeedCommentCreateRequest("바꿨어요", null)))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.FRIDGE_NOT_FOUND);
	}

	@Test
	void 다른_냉장고의_주소로는_코멘트를_고칠_수_없다() {
		FeedComment comment = comment(viewer, "구경 잘 했어요");
		Fridge other = em.merge(new Fridge("윤호의 냉장고"));
		em.persist(new FridgeMember(other, viewer, MemberRole.OWNER));
		other.changeVisibility(true);

		assertThatThrownBy(() -> feedCommentService.update(viewer.getId(), other.getId(), comment.getId(),
			new FeedCommentCreateRequest("바꿨어요", null)))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.FEED_COMMENT_NOT_FOUND);
	}

	private FeedComment comment(User author, String content) {
		FeedComment comment = new FeedComment(fridge, author, content, null);
		em.persist(comment);
		return comment;
	}

}
