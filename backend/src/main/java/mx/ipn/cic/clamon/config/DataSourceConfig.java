package mx.ipn.cic.clamon.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
public class DataSourceConfig {

	static final int MAX_CONEXIONES = 5;

	static final long ESPERA_CONEXION_MS = 3_000;

	@Bean(destroyMethod = "close")
	DataSource dsUsuarios(ClamonProperties propiedades) {
		return crear("clamon-usuarios", propiedades.db(), propiedades.db().usuarios());
	}

	@Bean(destroyMethod = "close")
	DataSource dsNegocio(ClamonProperties propiedades) {
		return crear("clamon-negocio", propiedades.db(), propiedades.db().negocio());
	}

	@Bean
	JdbcClient jdbcUsuarios(@Qualifier("dsUsuarios") DataSource ds) {
		return JdbcClient.create(ds);
	}

	@Bean
	JdbcClient jdbcNegocio(@Qualifier("dsNegocio") DataSource ds) {
		return JdbcClient.create(ds);
	}

	static String url(ClamonProperties.Db db, String base) {
		String tls = switch (db.tlsMode()) {
			case "verify-ca" -> {
				if (db.serverCaPath() == null || db.serverCaPath().isBlank()) {
					throw new IllegalStateException("DB_TLS_MODE=verify-ca requiere DB_SERVER_CA_PATH");
				}
				yield "sslMode=verify-ca&serverSslCert=" + db.serverCaPath();
			}
			case "trust" -> "sslMode=trust";
			default -> throw new IllegalStateException(
					"DB_TLS_MODE debe ser verify-ca o trust, no '" + db.tlsMode() + "'");
		};
		return "jdbc:mariadb://" + db.host() + ":" + db.port() + "/" + base + "?" + tls
				+ "&connectTimeout=3000&socketTimeout=10000";
	}

	private static HikariDataSource crear(String nombre, ClamonProperties.Db db, ClamonProperties.Base base) {
		HikariDataSource ds = new HikariDataSource();
		ds.setPoolName(nombre);
		ds.setJdbcUrl(url(db, base.name()));
		ds.setUsername(base.user());
		ds.setPassword(base.password());
		ds.setMaximumPoolSize(MAX_CONEXIONES);
		ds.setMinimumIdle(1);
		ds.setInitializationFailTimeout(-1);
		ds.setConnectionTimeout(ESPERA_CONEXION_MS);
		ds.setValidationTimeout(ESPERA_CONEXION_MS);
		return ds;
	}

}
