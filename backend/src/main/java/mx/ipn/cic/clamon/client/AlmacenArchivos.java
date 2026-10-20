package mx.ipn.cic.clamon.client;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import mx.ipn.cic.clamon.config.ClamonProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
public class AlmacenArchivos {

	private static final Logger log = LoggerFactory.getLogger(AlmacenArchivos.class);

	static final Duration ESPERA_DIAGNOSTICO = Duration.ofSeconds(3);

	static final Duration REINTENTO_BUCKET = Duration.ofSeconds(30);

	private final S3Client s3;

	private final String bucket;

	private final boolean inicializar;

	private final AtomicBoolean bucketListo = new AtomicBoolean();

	public AlmacenArchivos(S3Client s3, ClamonProperties propiedades) {
		this.s3 = s3;
		this.bucket = propiedades.s3().bucket();
		this.inicializar = propiedades.s3().inicializarBucket();
	}

	@EventListener(ApplicationReadyEvent.class)
	void alIniciar() {
		if (!this.inicializar) {
			return;
		}
		Thread.ofVirtual().name("bucket-inicial").start(() -> {
			for (int intento = 1; !this.bucketListo.get(); intento++) {
				try {
					asegurarBucket();
				}
				catch (SdkException ex) {
					log.atWarn()
						.addKeyValue("evento", "archivos.bucket_pendiente")
						.addKeyValue("bucket", this.bucket)
						.addKeyValue("intento", intento)
						.addKeyValue("tipo", ex.getClass().getSimpleName())
						.addKeyValue("causa", causaRaiz(ex))
						.log("MinIO no respondio; se reintenta en 30 s y en el primer uso");
					try {
						Thread.sleep(REINTENTO_BUCKET);
					}
					catch (InterruptedException interrumpido) {
						Thread.currentThread().interrupt();
						return;
					}
				}
			}
		});
	}

	public static String causaRaiz(Throwable ex) {
		Throwable causa = ex;
		while (causa.getCause() != null && causa.getCause() != causa) {
			causa = causa.getCause();
		}
		String mensaje = (causa.getMessage() != null) ? causa.getMessage() : "";
		return causa.getClass().getSimpleName() + ": " + mensaje.substring(0, Math.min(mensaje.length(), 200));
	}

	public String bucket() {
		return this.bucket;
	}

	public void asegurarBucket() {
		if (this.bucketListo.get()) {
			return;
		}
		try {
			this.s3.headBucket(b -> b.bucket(this.bucket));
		}
		catch (NoSuchBucketException ex) {
			crearBucket();
		}
		catch (S3Exception ex) {
			if (ex.statusCode() != 404) {
				throw ex;
			}
			crearBucket();
		}
		this.bucketListo.set(true);
	}

	private void crearBucket() {
		this.s3.createBucket(b -> b.bucket(this.bucket));
		log.atInfo().addKeyValue("evento", "archivos.bucket_creado").addKeyValue("bucket", this.bucket).log("bucket creado");
	}

	public int guardarTexto(String clave, String texto) {
		asegurarBucket();
		byte[] contenido = texto.getBytes(StandardCharsets.UTF_8);
		this.s3.putObject(p -> p.bucket(this.bucket).key(clave).contentType("text/plain; charset=utf-8"),
				RequestBody.fromBytes(contenido));
		return contenido.length;
	}

	public void comprobar() {
		this.s3.headBucket(b -> b.bucket(this.bucket)
			.overrideConfiguration(c -> c.apiCallTimeout(ESPERA_DIAGNOSTICO).apiCallAttemptTimeout(ESPERA_DIAGNOSTICO)));
	}

}
