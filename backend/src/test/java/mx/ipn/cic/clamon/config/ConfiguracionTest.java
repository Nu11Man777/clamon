package mx.ipn.cic.clamon.config;

import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import mx.ipn.cic.clamon.service.EsquemaInicial;

import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;

import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguracionTest {

	static ClamonProperties.Db db(String modo, String ca) {
		return new ClamonProperties.Db("db.c1.clamon.internal", 3306, modo, ca, false, null, null);
	}

	@Test
	void urlVerifyCaUsaElCertificadoDelServidor() {
		assertThat(DataSourceConfig.url(db("verify-ca", "/secrets/cloudsql/server-ca.pem"), "clamon_usuarios"))
			.startsWith("jdbc:mariadb://db.c1.clamon.internal:3306/clamon_usuarios?")
			.contains("sslMode=verify-ca&serverSslCert=/secrets/cloudsql/server-ca.pem")
			.contains("connectTimeout=3000");
	}

	@Test
	void urlTrustSoloCifra() {
		assertThat(DataSourceConfig.url(db("trust", ""), "clamon_negocio")).contains("sslMode=trust")
			.doesNotContain("serverSslCert");
	}

	@Test
	void sinTlsOVerifyCaSinAutoridadNoArranca() {
		assertThatThrownBy(() -> DataSourceConfig.url(db("disable", ""), "x"))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> DataSourceConfig.url(db("verify-ca", ""), "x"))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void esquemaInicialRechazaUnaContrasenaDeAdministradorInvalida() {
		ClamonProperties.AdminInicial admin = new ClamonProperties.AdminInicial("adan.admin", "corta");
		ClamonProperties sinInicializar = new ClamonProperties("0.1.0", null, db("trust", ""), null, null, admin,
				List.of());
		ClamonProperties conInicializar = new ClamonProperties("0.1.0", null,
				new ClamonProperties.Db("h", 3306, "trust", "", true, null, null), null, null, admin, List.of());
		new EsquemaInicial(null, null, null, null, sinInicializar);
		assertThatThrownBy(() -> new EsquemaInicial(null, null, null, null, conInicializar))
			.hasMessageContaining("contrasena");
	}

	@Test
	void formatoRegistroEscribeUnaLineaJson() {
		MockEnvironment entorno = new MockEnvironment().withProperty("spring.application.name", "clamon-backend");
		FormatoRegistro formato = new FormatoRegistro(entorno);
		Logger logger = new LoggerContext().getLogger("prueba");
		LoggingEvent evento = new LoggingEvent("x", logger, Level.WARN, "solicitud sin token valido", null, null);
		evento.addKeyValuePair(new KeyValuePair("evento", "auth.no_autenticado"));
		String linea = formato.format(evento);
		assertThat(linea).endsWith("\n").doesNotContain("\n\n");
		assertThat(linea.strip()).doesNotContain("\n")
			.contains("\"severity\":\"WARNING\"")
			.contains("\"message\":\"solicitud sin token valido\"")
			.contains("\"servicio\":\"clamon-backend\"")
			.contains("\"evento\":\"auth.no_autenticado\"");
	}

}
