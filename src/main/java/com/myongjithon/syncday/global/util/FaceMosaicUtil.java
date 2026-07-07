package com.myongjithon.syncday.global.util;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;
import software.amazon.awssdk.services.rekognition.model.Image;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public class FaceMosaicUtil {

    public static byte[] applyMosaic(byte[] originalBytes, RekognitionClient rekognitionClient) throws IOException {
        int orientation = getExifOrientation(originalBytes);
        BufferedImage correctedImage = correctOrientation(originalBytes, orientation);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(correctedImage, "jpg", baos);
        byte[] correctedBytes = baos.toByteArray();

        Image awsImage = Image.builder()
                .bytes(SdkBytes.fromByteArray(correctedBytes))
                .build();

        DetectFacesRequest request = DetectFacesRequest.builder()
                .image(awsImage)
                .build();

        DetectFacesResponse response = rekognitionClient.detectFaces(request);
        List<FaceDetail> faces = response.faceDetails();

        if (faces.isEmpty()) {
            return correctedBytes;
        }

        int imgWidth = correctedImage.getWidth();
        int imgHeight = correctedImage.getHeight();
        Graphics2D g2d = correctedImage.createGraphics();

        for (FaceDetail face : faces) {
            BoundingBox box = face.boundingBox();

            int rawX = (int) (box.left() * imgWidth);
            int rawY = (int) (box.top() * imgHeight);
            int rawWidth = (int) (box.width() * imgWidth);
            int rawHeight = (int) (box.height() * imgHeight);

            int paddingX = (int) (rawWidth * 0.25);
            int paddingY = (int) (rawHeight * 0.35);

            int x = Math.max(0, rawX - paddingX);
            int y = Math.max(0, rawY - paddingY);
            int width = Math.min(rawWidth + paddingX * 2, imgWidth - x);
            int height = Math.min(rawHeight + paddingY * 2, imgHeight - y);

            BufferedImage faceRegion = correctedImage.getSubimage(x, y, width, height);
            BufferedImage blurredFace = applyBlur(faceRegion);
            g2d.drawImage(blurredFace, x, y, null);
        }

        g2d.dispose();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(correctedImage, "jpg", outputStream);
        return outputStream.toByteArray();
    }

    private static BufferedImage applyBlur(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        // 1단계: 크게 다운스케일 (블록화 방지를 위해 너무 작게는 안 줄임)
        int divisor = 15;
        int smallWidth = Math.max(1, width / divisor);
        int smallHeight = Math.max(1, height / divisor);

        BufferedImage small = new BufferedImage(smallWidth, smallHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g1 = small.createGraphics();
        g1.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g1.drawImage(image, 0, 0, smallWidth, smallHeight, null);
        g1.dispose();

        // 2단계: 작아진 이미지에 부드러운 컨볼루션 블러 적용 (연산량 적음, 빠름)
        int kernelSize = 5;
        float[] matrix = new float[kernelSize * kernelSize];
        for (int i = 0; i < matrix.length; i++) {
            matrix[i] = 1.0f / matrix.length;
        }
        BufferedImageOp convolve = new ConvolveOp(new Kernel(kernelSize, kernelSize, matrix), ConvolveOp.EDGE_NO_OP, null);
        BufferedImage smallBlurred = convolve.filter(small, null);

        // 3단계: 다시 원본 크기로 확대 (부드럽게 보간되어 자연스러운 블러 완성)
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = result.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(smallBlurred, 0, 0, width, height, null);
        g2.dispose();

        return result;
    }

    private static int getExifOrientation(byte[] imageBytes) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(imageBytes));
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception e) {
        }
        return 1;
    }

    private static BufferedImage correctOrientation(byte[] imageBytes, int orientation) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));

        switch (orientation) {
            case 6: return rotate(image, 90);
            case 3: return rotate(image, 180);
            case 8: return rotate(image, 270);
            default: return image;
        }
    }

    private static BufferedImage rotate(BufferedImage image, int degrees) {
        double radians = Math.toRadians(degrees);
        int w = image.getWidth();
        int h = image.getHeight();

        BufferedImage rotated = (degrees == 90 || degrees == 270)
                ? new BufferedImage(h, w, image.getType())
                : new BufferedImage(w, h, image.getType());

        Graphics2D g2d = rotated.createGraphics();
        AffineTransform transform = new AffineTransform();

        if (degrees == 90) {
            transform.translate(h, 0);
        } else if (degrees == 180) {
            transform.translate(w, h);
        } else if (degrees == 270) {
            transform.translate(0, w);
        }
        transform.rotate(radians);
        g2d.setTransform(transform);
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

        return rotated;
    }
}