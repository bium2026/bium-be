package com.bium.domain.ingredient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

class IngredientImageProcessorTest {

	private final IngredientImageProcessor processor = new IngredientImageProcessor();

	@Test
	void 큰_PNG는_비율을_지켜_긴_변을_256px로_줄인다() throws IOException {
		byte[] png = processor.toPng(file("photo.png", "image/png", image("png", 1000, 500)));

		BufferedImage result = ImageIO.read(new ByteArrayInputStream(png));
		assertThat(result.getWidth()).isEqualTo(256);
		assertThat(result.getHeight()).isEqualTo(128);
	}

	@Test
	void JPG도_PNG로_다시_만들고_작은_사진은_키우지_않는다() throws IOException {
		byte[] png = processor.toPng(file("photo.jpg", "image/jpeg", image("jpg", 100, 80)));

		BufferedImage result = ImageIO.read(new ByteArrayInputStream(png));
		assertThat(result.getWidth()).isEqualTo(100);
		assertThat(result.getHeight()).isEqualTo(80);
		// PNG 파일 서명
		assertThat(png).startsWith((byte)0x89, (byte)'P', (byte)'N', (byte)'G');
	}

	@Test
	void Content_Type이_이미지여도_내용이_JPG_PNG가_아니면_막는다() throws IOException {
		assertInvalid(file("photo.png", "image/png", "<svg></svg>".getBytes(StandardCharsets.UTF_8)));
		assertInvalid(file("photo.gif", "image/gif", image("gif", 10, 10)));
	}

	@Test
	void 비어_있거나_없는_파일은_막는다() {
		assertInvalid(file("empty.png", "image/png", new byte[0]));
		assertInvalid(null);
	}

	@Test
	void 가로나_세로가_4096px을_넘으면_막는다() throws IOException {
		assertInvalid(file("wide.png", "image/png", image("png", IngredientImageProcessor.MAX_SOURCE_SIDE + 1, 1)));
	}

	@Test
	void 파일이_5MB를_넘으면_크기_오류를_준다() {
		byte[] large = new byte[(int)IngredientImageProcessor.MAX_UPLOAD_BYTES + 1];

		assertThatThrownBy(() -> processor.toPng(file("large.png", "image/png", large)))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.INGREDIENT_IMAGE_TOO_LARGE);
	}

	private void assertInvalid(MockMultipartFile file) {
		assertThatThrownBy(() -> processor.toPng(file))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.INVALID_INGREDIENT_IMAGE);
	}

	static byte[] image(String format, int width, int height) throws IOException {
		int type = format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(width, height, type), format, output);
		return output.toByteArray();
	}

	private static MockMultipartFile file(String name, String contentType, byte[] bytes) {
		return new MockMultipartFile("image", name, contentType, bytes);
	}

}
