package mx.ipn.cic.clamon.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("clamon")
public record ClamonProperties(String version, Servicios servicios, Db db, S3 s3, Sesion sesion,
		AdminInicial adminInicial, List<String> corsOrigenes) {

	public record Servicios(String clasificadorUrl, String scraperUrl, String auth) {

		public boolean autenticacionGoogle() {
			return !"none".equalsIgnoreCase(this.auth);
		}

	}

	public record Db(String host, int port, String tlsMode, String serverCaPath, boolean inicializar, Base usuarios,
			Base negocio) {
	}

	public record Base(String name, String user, String password) {
	}

	public record S3(String endpoint, String bucket, String accessKey, String secretKey, boolean inicializarBucket) {
	}

	public record Sesion(String jwtSecreto, int duracionMin) {
	}

	public record AdminInicial(String usuario, String password) {
	}

}
