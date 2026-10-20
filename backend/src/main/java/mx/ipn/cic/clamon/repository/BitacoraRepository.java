package mx.ipn.cic.clamon.repository;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BitacoraRepository {

	private static final Logger log = LoggerFactory.getLogger(BitacoraRepository.class);

	private final JdbcClient jdbc;

	public BitacoraRepository(@Qualifier("jdbcUsuarios") JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public void registrar(String sub, String accion, String detalle) {
		this.jdbc.sql("INSERT INTO bitacora (sub, accion, detalle, fecha) VALUES (:sub, :accion, :detalle, :fecha)")
			.param("sub", sub)
			.param("accion", UsuarioRepository.recortar(accion, 64))
			.param("detalle", UsuarioRepository.recortar(detalle, 512))
			.param("fecha", UsuarioRepository.utc(Instant.now()))
			.update();
	}

	public void registrarSinFallar(String sub, String accion, String detalle) {
		try {
			registrar(sub, accion, detalle);
		}
		catch (DataAccessException ex) {
			log.atWarn()
				.addKeyValue("evento", "bitacora.error")
				.addKeyValue("accion", accion)
				.addKeyValue("tipo", ex.getClass().getSimpleName())
				.log("no se pudo escribir la bitacora");
		}
	}

}
