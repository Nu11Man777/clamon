package mx.ipn.cic.clamon.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import mx.ipn.cic.clamon.model.Sitio;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class SitioRepository {

	private final JdbcClient jdbc;

	public SitioRepository(@Qualifier("jdbcNegocio") JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<Sitio> listar() {
		return this.jdbc.sql("SELECT id, url, host, creado_por, creado_en FROM sitio ORDER BY id")
			.query(SitioRepository::mapear)
			.list();
	}

	public Optional<Sitio> buscar(long id) {
		return this.jdbc.sql("SELECT id, url, host, creado_por, creado_en FROM sitio WHERE id = :id")
			.param("id", id)
			.query(SitioRepository::mapear)
			.optional();
	}

	public Sitio crear(String url, String host, String creadoPor, Instant creadoEn) {
		KeyHolder llave = new GeneratedKeyHolder();
		this.jdbc.sql("""
				INSERT INTO sitio (url, host, creado_por, creado_en)
				VALUES (:url, :host, :creado_por, :creado_en)
				""")
			.param("url", url)
			.param("host", host)
			.param("creado_por", creadoPor)
			.param("creado_en", LocalDateTime.ofInstant(creadoEn, ZoneOffset.UTC))
			.update(llave);
		return new Sitio(llave.getKey().longValue(), url, host, creadoPor, creadoEn);
	}

	static Instant instante(ResultSet rs, String columna) throws SQLException {
		LocalDateTime fecha = rs.getObject(columna, LocalDateTime.class);
		return (fecha != null) ? fecha.toInstant(ZoneOffset.UTC) : null;
	}

	private static Sitio mapear(ResultSet rs, int fila) throws SQLException {
		return new Sitio(rs.getLong("id"), rs.getString("url"), rs.getString("host"), rs.getString("creado_por"),
				instante(rs, "creado_en"));
	}

}
