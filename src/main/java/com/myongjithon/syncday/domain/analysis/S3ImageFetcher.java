package com.myongjithon.syncday.domain.analysis;

import com.myongjithon.syncday.global.exception.AnalysisErrorCode;
import com.myongjithon.syncday.global.exception.AnalysisException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.util.Base64;

/**
 * syncday-photos 버킷은 퍼블릭 액세스가 차단되어 있어(직접 URL 접근 불가),
 * ai-service가 Photo.imageUrl로 직접 다운로드할 수 없다. 그래서 BE가 S3에서
 * 직접 바이트를 읽어 base64 data URI로 변환해서 ai-service에 넘긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3ImageFetcher {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    public String toDataUri(String s3Key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        try {
            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(request);
            String contentType = objectBytes.response().contentType();
            if (contentType == null) {
                contentType = "image/jpeg";
            }
            String base64 = Base64.getEncoder().encodeToString(objectBytes.asByteArray());
            return "data:" + contentType + ";base64," + base64;
        } catch (SdkException e) {
            log.error("S3 이미지 다운로드 실패: key={}", s3Key, e);
            throw new AnalysisException(AnalysisErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }
}
