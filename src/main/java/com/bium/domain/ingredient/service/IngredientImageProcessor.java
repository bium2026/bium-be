package com.bium.domain.ingredient.service;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

/**
 * 재료 사진을 검사하고 아이콘 크기의 PNG로 다시 만든다.
 * 확장자나 Content-Type은 믿지 않고 내용으로 JPG·PNG인지 확인한다.
 * 다시 그려서 저장하므로 원본의 위치 정보(EXIF)나 사진 뒤에 붙은 다른 데이터는 남지 않는다.
 */
@Component
public class IngredientImageProcessor {

	public static final String CONTENT_TYPE = "image/png";
	static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
	// 크기를 읽기 전에 막지 않으면 작은 파일이 아주 큰 그림으로 풀려 메모리를 다 쓸 수 있다
	static final int MAX_SOURCE_SIDE = 4096;
	static final int MAX_OUTPUT_SIDE = 256;

	private static final Set<String> SUPPORTED_FORMATS = Set.of("jpeg", "png");

	public byte[] toPng(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INGREDIENT_IMAGE);
		}
		if (file.getSize() > MAX_UPLOAD_BYTES) {
			throw new BusinessException(ErrorCode.INGREDIENT_IMAGE_TOO_LARGE);
		}
		try {
			BufferedImage resized = resize(read(file.getBytes()));
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			ImageIO.write(resized, "png", output);
			return output.toByteArray();
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.INVALID_INGREDIENT_IMAGE, e);
		}
	}

	private BufferedImage read(byte[] bytes) throws IOException {
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
			if (readers == null || !readers.hasNext()) {
				throw new BusinessException(ErrorCode.INVALID_INGREDIENT_IMAGE);
			}
			ImageReader reader = readers.next();
			try {
				if (!SUPPORTED_FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
					throw new BusinessException(ErrorCode.INVALID_INGREDIENT_IMAGE);
				}
				reader.setInput(input, true, true);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width > MAX_SOURCE_SIDE || height > MAX_SOURCE_SIDE) {
					throw new BusinessException(ErrorCode.INVALID_INGREDIENT_IMAGE);
				}
				return reader.read(0);
			} catch (BusinessException e) {
				throw e;
			} catch (RuntimeException e) {
				// 깨진 파일은 읽는 도중 IOException이 아닌 예외로 실패하기도 한다
				throw new BusinessException(ErrorCode.INVALID_INGREDIENT_IMAGE, e);
			} finally {
				reader.dispose();
			}
		}
	}

	// 비율을 지킨 채 긴 변을 MAX_OUTPUT_SIDE에 맞춘다. 이미 작은 사진은 키우지 않는다
	private BufferedImage resize(BufferedImage source) {
		int width = source.getWidth();
		int height = source.getHeight();
		double scale = Math.min(1.0, (double)MAX_OUTPUT_SIDE / Math.max(width, height));
		int targetWidth = Math.max(1, (int)Math.round(width * scale));
		int targetHeight = Math.max(1, (int)Math.round(height * scale));

		BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = target.createGraphics();
		try {
			// 크게 줄일 때 계단 현상이 덜한 평균 방식으로 줄인다
			graphics.drawImage(source.getScaledInstance(targetWidth, targetHeight, Image.SCALE_AREA_AVERAGING), 0, 0,
				null);
		} finally {
			graphics.dispose();
		}
		return target;
	}

}
