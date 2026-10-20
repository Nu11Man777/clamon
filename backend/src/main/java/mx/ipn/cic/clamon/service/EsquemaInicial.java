package mx.ipn.cic.clamon.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import mx.ipn.cic.clamon.config.ClamonProperties;
import mx.ipn.cic.clamon.model.Usuario;
import mx.ipn.cic.clamon.repository.UsuarioRepository;
import mx.ipn.cic.clamon.security.PoliticaUsuarios;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class EsquemaInicial {

	private static final Logger log = LoggerFactory.getLogger(EsquemaInicial.class);

	static final Duration REINTENTO = Duration.ofSeconds(30);

	static final List<String> TABLAS_USUARIOS = List.of("""
			CREATE TABLE IF NOT EXISTS usuario (
			  sub               VARCHAR(64)  NOT NULL PRIMARY KEY,
			  usuario           VARCHAR(64)  NOT NULL,
			  hash_contrasena   VARCHAR(100) NOT NULL,
			  roles             VARCHAR(255) NOT NULL,
			  activo            BOOLEAN      NOT NULL DEFAULT TRUE,
			  intentos_fallidos INT          NOT NULL DEFAULT 0,
			  bloqueado_hasta   DATETIME(3)  NULL,
			  creado_en         DATETIME(3)  NOT NULL,
			  ultimo_acceso     DATETIME(3)  NULL,
			  UNIQUE KEY uq_usuario_usuario (usuario)
			)""", """
			CREATE TABLE IF NOT EXISTS bitacora (
			  id      BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
			  sub     VARCHAR(64)  NOT NULL,
			  accion  VARCHAR(64)  NOT NULL,
			  detalle VARCHAR(512) NOT NULL,
			  fecha   DATETIME(3)  NOT NULL,
			  INDEX idx_bitacora_sub (sub)
			)""");

	static final List<String> TABLAS_NEGOCIO = List.of("""
			CREATE TABLE IF NOT EXISTS sitio (
			  id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
			  url        VARCHAR(2048) NOT NULL,
			  host       VARCHAR(255)  NOT NULL,
			  creado_por VARCHAR(128)  NOT NULL,
			  creado_en  DATETIME(3)   NOT NULL,
			  UNIQUE KEY uq_sitio_url (url(255))
			)""", """
			CREATE TABLE IF NOT EXISTS analisis (
			  id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
			  sitio_id        BIGINT       NOT NULL,
			  estado          VARCHAR(16)  NOT NULL,
			  nivel           VARCHAR(16)  NOT NULL,
			  total_hallazgos INT          NOT NULL,
			  hallazgos       JSON         NOT NULL,
			  objeto          VARCHAR(255) NOT NULL,
			  bytes           INT          NOT NULL,
			  creado_por      VARCHAR(128) NOT NULL,
			  creado_en       DATETIME(3)  NOT NULL,
			  CONSTRAINT fk_analisis_sitio FOREIGN KEY (sitio_id) REFERENCES sitio (id)
			)""");

	private final JdbcClient jdbcUsuarios;

	private final JdbcClient jdbcNegocio;

	private final UsuarioRepository usuarios;

	private final PasswordEncoder cifrador;

	private final ClamonProperties.AdminInicial admin;

	private final boolean inicializar;

	public EsquemaInicial(@Qualifier("jdbcUsuarios") JdbcClient jdbcUsuarios,
			@Qualifier("jdbcNegocio") JdbcClient jdbcNegocio, UsuarioRepository usuarios, PasswordEncoder cifrador,
			ClamonProperties propiedades) {
		this.jdbcUsuarios = jdbcUsuarios;
		this.jdbcNegocio = jdbcNegocio;
		this.usuarios = usuarios;
		this.cifrador = cifrador;
		this.admin = propiedades.adminInicial();
		this.inicializar = propiedades.db().inicializar();
		if (this.inicializar) {
			String nombre = PoliticaUsuarios.normalizarNombre(this.admin.usuario());
			PoliticaUsuarios.validarNombre(nombre);
			PoliticaUsuarios.validarContrasena(this.admin.password(), nombre);
		}
	}

	@EventListener(ApplicationReadyEvent.class)
	void alIniciar() {
		if (!this.inicializar) {
			return;
		}
		Thread.ofVirtual().name("esquema-inicial").start(() -> {
			for (int intento = 1;; intento++) {
				try {
					crear();
					return;
				}
				catch (DataAccessException ex) {
					log.atWarn()
						.addKeyValue("evento", "db.esquema_pendiente")
						.addKeyValue("intento", intento)
						.addKeyValue("tipo", ex.getClass().getSimpleName())
						.log("la base no respondio; se reintenta en 30 s");
					try {
						Thread.sleep(REINTENTO);
					}
					catch (InterruptedException interrumpido) {
						Thread.currentThread().interrupt();
						return;
					}
				}
			}
		});
	}

	void crear() {
		TABLAS_USUARIOS.forEach(sql -> this.jdbcUsuarios.sql(sql).update());
		TABLAS_NEGOCIO.forEach(sql -> this.jdbcNegocio.sql(sql).update());
		String nombre = PoliticaUsuarios.normalizarNombre(this.admin.usuario());
		boolean creado = false;
		if (this.usuarios.buscarPorUsuario(nombre).isEmpty()) {
			try {
				this.usuarios.crear(new Usuario(UUID.randomUUID().toString(), nombre,
						this.cifrador.encode(this.admin.password()), List.of("administrador"), true, 0, null,
						Instant.now(), null));
				creado = true;
			}
			catch (DuplicateKeyException ex) {
			}
		}
		log.atInfo()
			.addKeyValue("evento", "db.esquema_listo")
			.addKeyValue("administrador_inicial", nombre)
			.addKeyValue("creado", creado)
			.log("tablas y administrador inicial listos");
	}

}
