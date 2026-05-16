package com.najacks.backend.infra.s3;

import com.najacks.backend.global.exception.CustomException;
import com.najacks.backend.global.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.UUID;

@Slf4j
@Service
public class S3Service {

    @Autowired(required = false)
    private S3Client s3Client;

    @Value("${cloud.aws.s3.bucket:}")
    private String bucket;

    @Value("${cloud.aws.region.static:ap-northeast-2}")
    private String region;

    @Value("${app.upload.local-dir:${java.io.tmpdir}/najacks-uploads}")
    private String localUploadDir;

    @Value("${app.upload.public-base:/uploads}")
    private String publicBase;

    @PostConstruct
    void init() {
        if (s3Client == null) {
            try {
                Files.createDirectories(Paths.get(localUploadDir));
                log.info("S3 not configured. Using local file storage at: {}", localUploadDir);
            } catch (IOException e) {
                log.error("Failed to create local upload dir {}: {}", localUploadDir, e.getMessage());
            }
        }
    }

    private static final int MAX_WIDTH = 800;
    private static final int MAX_HEIGHT = 600;
    private static final float JPEG_QUALITY = 0.75f;

    public String upload(MultipartFile file, String directory) {
        // 이미지 파일이면 자동 압축
        byte[] data;
        String contentType = file.getContentType();
        String originalName = sanitize(file.getOriginalFilename());
        boolean isImage = contentType != null && contentType.startsWith("image/");

        if (isImage) {
            try {
                byte[] compressed = compressImage(file.getInputStream(), contentType);
                data = compressed;
                contentType = "image/jpeg";
                originalName = originalName.replaceAll("\\.[^.]+$", "") + ".jpg";
                log.info("Image compressed: {}KB -> {}KB", file.getSize() / 1024, data.length / 1024);
            } catch (Exception e) {
                log.warn("Image compression failed, uploading original: {}", e.getMessage());
                try { data = file.getBytes(); } catch (IOException ex) { throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED); }
            }
        } else {
            try { data = file.getBytes(); } catch (IOException e) { throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED); }
        }

        if (s3Client == null) {
            return uploadLocal(data, directory, originalName, contentType);
        }

        String fileName = directory + "/" + UUID.randomUUID() + "_" + originalName;
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(data));

            return String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, fileName);
        } catch (Exception e) {
            log.error("S3 upload failed: {}", e.getMessage());
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    private byte[] compressImage(InputStream inputStream, String contentType) throws IOException {
        BufferedImage original = ImageIO.read(inputStream);
        if (original == null) throw new IOException("Cannot read image");

        int origW = original.getWidth();
        int origH = original.getHeight();

        // 리사이즈 계산
        int newW = origW;
        int newH = origH;
        if (origW > MAX_WIDTH || origH > MAX_HEIGHT) {
            double ratio = Math.min((double) MAX_WIDTH / origW, (double) MAX_HEIGHT / origH);
            newW = (int) (origW * ratio);
            newH = (int) (origH * ratio);
        }

        // 리사이즈 수행
        BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, newW, newH);
        g.drawImage(original, 0, 0, newW, newH, null);
        g.dispose();

        // JPEG 압축
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) throw new IOException("No JPEG writer found");

        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(resized, null, null), param);
        }
        writer.dispose();

        return baos.toByteArray();
    }

    private String uploadLocal(byte[] data, String directory, String fileName, String contentType) {
        try {
            String safeDir = sanitize(directory);
            String safeName = UUID.randomUUID() + "_" + fileName;

            Path targetDir = Paths.get(localUploadDir, safeDir);
            Files.createDirectories(targetDir);

            Path target = targetDir.resolve(safeName);
            Files.copy(new ByteArrayInputStream(data), target, StandardCopyOption.REPLACE_EXISTING);

            String publicUrl = publicBase + "/" + safeDir + "/" + safeName;
            log.info("Local upload saved: {} -> {} ({}KB)", target, publicUrl, data.length / 1024);
            return publicUrl;
        } catch (IOException e) {
            log.error("Local upload failed: {}", e.getMessage());
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    // 기존 MultipartFile 직접 로컬 업로드 (하위호환)
    private String uploadLocal(MultipartFile file, String directory) {
        try { return uploadLocal(file.getBytes(), directory, sanitize(file.getOriginalFilename()), file.getContentType()); }
        catch (IOException e) { throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED); }
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) return "file";
        // strip directory traversal and odd characters
        String base = name.replace("\\", "/");
        int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash + 1);
        return base.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /**
     * 기존 로컬 이미지 파일을 압축하고 새 URL을 반환한다.
     * 압축 불가능하면 원본 URL을 그대로 반환.
     */
    public String recompressLocal(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith(publicBase + "/")) return fileUrl;
        try {
            String relative = fileUrl.substring(publicBase.length() + 1);
            Path source = Paths.get(localUploadDir, relative).normalize();
            if (!Files.exists(source)) return fileUrl;

            long originalSize = Files.size(source);
            if (originalSize < 50_000) return fileUrl; // 50KB 이하는 스킵

            byte[] compressed = compressImage(Files.newInputStream(source), "image/png");
            if (compressed.length >= originalSize) return fileUrl; // 더 커지면 스킵

            String newName = source.getFileName().toString().replaceAll("\\.[^.]+$", "") + ".jpg";
            Path newFile = source.getParent().resolve(newName);
            Files.write(newFile, compressed);

            // 기존 파일 삭제 (다른 파일이면)
            if (!source.equals(newFile)) Files.deleteIfExists(source);

            String newUrl = publicBase + "/" + relative.substring(0, relative.lastIndexOf('/') + 1) + newName;
            log.info("Recompressed: {}KB -> {}KB ({})", originalSize / 1024, compressed.length / 1024, newUrl);
            return newUrl;
        } catch (Exception e) {
            log.warn("Recompress failed for {}: {}", fileUrl, e.getMessage());
            return fileUrl;
        }
    }

    public void delete(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;

        // Local file
        if (fileUrl.startsWith(publicBase + "/")) {
            try {
                String relative = fileUrl.substring(publicBase.length() + 1);
                Path target = Paths.get(localUploadDir, relative).normalize();
                if (target.startsWith(Paths.get(localUploadDir).normalize())) {
                    Files.deleteIfExists(target);
                }
            } catch (Exception e) {
                log.warn("Local delete failed: {}", e.getMessage());
            }
            return;
        }

        // S3
        if (s3Client == null) return;
        try {
            String key = fileUrl.substring(fileUrl.indexOf(".com/") + 5);
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            s3Client.deleteObject(deleteRequest);
        } catch (Exception e) {
            log.warn("S3 delete failed: {}", e.getMessage());
        }
    }
}
