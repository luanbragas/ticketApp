package com.festa.event.infra;

import com.festa.event.app.MediaStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

/**
 * Assinatura de URLs no R2 (ou S3Mock em dev) pela API S3. Só assina: não faz chamada de rede,
 * então a API não depende do bucket estar no ar para responder.
 */
@Component
class S3MediaStorage implements MediaStorage, AutoCloseable {

	private final S3Presigner presigner;
	private final String bucket;
	private final String publicBaseUrl;

	S3MediaStorage(@Value("${festa.storage.endpoint}") String endpoint,
			@Value("${festa.storage.region}") String region,
			@Value("${festa.storage.bucket}") String bucket,
			@Value("${festa.storage.access-key}") String accessKey,
			@Value("${festa.storage.secret-key}") String secretKey,
			@Value("${festa.storage.public-base-url}") String publicBaseUrl) {
		this.presigner = S3Presigner.builder()
			.endpointOverride(URI.create(endpoint))
			.region(Region.of(region))
			.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
			// R2 e S3Mock aceitam caminho (endpoint/bucket/chave), não subdomínio por bucket.
			.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
			.build();
		this.bucket = bucket;
		this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl : publicBaseUrl + "/";
	}

	@Override
	public URI presignPut(String key, String contentType, long contentLength, Duration ttl) {
		PutObjectRequest put = PutObjectRequest.builder()
			.bucket(bucket)
			.key(key)
			.contentType(contentType)
			.contentLength(contentLength)
			.build();
		try {
			return presigner.presignPutObject(PutObjectPresignRequest.builder()
				.signatureDuration(ttl)
				.putObjectRequest(put)
				.build()).url().toURI();
		}
		catch (URISyntaxException ex) {
			throw new IllegalStateException("URL pré-assinada inválida", ex);
		}
	}

	@Override
	public URI publicUrl(String key) {
		return URI.create(publicBaseUrl + key);
	}

	@Override
	public void close() {
		presigner.close();
	}

}
