package cn.lx.worldcoffee.common.storage;

import cn.lx.worldcoffee.common.exception.ServiceException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final ObjectProvider<MinioClient> minioClientProvider;
    private final MinioProperties properties;

    public String upload(MultipartFile file, String objectName) {
        if (!properties.isEnabled()) {
            throw new ServiceException("MinIO 未启用，请检查 minio.enabled 配置");
        }
        if (file == null || file.isEmpty()) {
            throw new ServiceException("文件不能为空");
        }

        MinioClient client = minioClientProvider.getIfAvailable();
        if (client == null) {
            throw new ServiceException("MinIO 客户端未初始化");
        }

        String normalizedObjectName = normalizeObjectName(objectName);
        try (InputStream inputStream = file.getInputStream()) {
            ensureBucket(client);
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(normalizedObjectName)
                    .contentType(file.getContentType())
                    .stream(inputStream, file.getSize(), -1)
                    .build());
            return "/" + normalizedObjectName;
        } catch (Exception e) {
            throw new ServiceException("文件上传到 MinIO 失败: " + e.getMessage());
        }
    }

    public String publicUrl(String objectName) {
        String endpoint = trimTrailingSlash(properties.getPublicEndpoint());
        return endpoint + "/" + properties.getBucket() + "/" + normalizeObjectName(objectName);
    }

    private void ensureBucket(MinioClient client) throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder()
                .bucket(properties.getBucket())
                .build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder()
                    .bucket(properties.getBucket())
                    .build());
        }

        client.setBucketPolicy(SetBucketPolicyArgs.builder()
                .bucket(properties.getBucket())
                .config(publicReadPolicy(properties.getBucket()))
                .build());
    }

    private String publicReadPolicy(String bucket) {
        return """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": {"AWS": ["*"]},
                      "Action": ["s3:GetObject"],
                      "Resource": ["arn:aws:s3:::%s/*"]
                    }
                  ]
                }
                """.formatted(bucket);
    }

    private String normalizeObjectName(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            throw new ServiceException("对象路径不能为空");
        }
        return objectName.replace("\\", "/").replaceAll("^/+", "");
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "";
        return value.replaceAll("/+$", "");
    }
}
