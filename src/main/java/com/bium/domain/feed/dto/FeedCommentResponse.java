package com.bium.domain.feed.dto;

import java.time.LocalDateTime;

import com.bium.domain.feed.entity.FeedComment;

/**
 * 피드 냉장고에 남긴 코멘트 하나. 음식을 추천하지 않은 코멘트는 dishName이 null이다.
 * mine은 내가 쓴 코멘트인지, deletable은 내가 지울 수 있는지(내가 썼거나 냉장고 주인)다.
 */
public record FeedCommentResponse(
	Long commentId,
	String authorNickname,
	String content,
	String dishName,
	LocalDateTime createdAt,
	boolean mine,
	boolean deletable
) {

	public static FeedCommentResponse of(FeedComment comment, Long userId, boolean owner) {
		boolean mine = comment.getUser().getId().equals(userId);
		return new FeedCommentResponse(comment.getId(), comment.getUser().getNickname(), comment.getContent(),
			comment.getDishName(), comment.getCreatedAt(), mine, mine || owner);
	}

}
