package mx.ipn.cic.clamon.config;

import java.net.URI;
import java.time.Duration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class S3Config {

	static final Duration ESPERA_CONEXION = Duration.ofSeconds(3);

	static final Duration ESPERA_OPERACION = Duration.ofSeconds(10);

	@Bean(destroyMethod = "close")
	S3Client s3Client(ClamonProperties propiedades) {
		ClamonProperties.S3 s3 = propiedades.s3();
		Apache5HttpClient.Builder http = Apache5HttpClient.builder()
			.connectionTimeout(ESPERA_CONEXION)
			.socketTimeout(ESPERA_OPERACION)
			.maxConnections(10);
		AwsCredentialsProvider credenciales = () -> AwsBasicCredentials.create(s3.accessKey(), s3.secretKey());
		return S3Client.builder()
			.endpointOverride(URI.create(s3.endpoint()))
			.forcePathStyle(true)
			.region(Region.US_EAST_1)
			.credentialsProvider(credenciales)
			.httpClientBuilder(http)
			.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
			.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
			.overrideConfiguration(c -> c.apiCallTimeout(ESPERA_OPERACION).apiCallAttemptTimeout(ESPERA_OPERACION))
			.build();
	}

}
