package mx.ipn.cic.clamon.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import mx.ipn.cic.clamon.model.Analisis;
import mx.ipn.cic.clamon.model.Hallazgo;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class AnalisisRepository {

	private static final TypeReference<List<Hallazgo>> LISTA_HALLAZGOS = new TypeReference<>() {
	};

	private final JdbcClient jdbc;

	private final JsonMapper json;

	public AnalisisRepository(@Qualifier("jdbcNegocio") JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	public long crear(long sitioId, String nivel, List<Hallazgo> hallazgos, String creadoPor, Instant creadoEn) {
		KeyHolder llave = new GeneratedKeyHolder();
		this.jdbc.sql("""
				INSERT INTO analisis (sitio_id, estado, nivel, total_hallazgos, hallazgos, objeto, bytes,
				                      creado_por, creado_en)
				VALUES (:sitio_id, :estado, :nivel, :total, :hallazgos, '', 0, :creado_por, :creado_en)
				""")
			.param("sitio_id", sitioId)
			.param("estado", Analisis.EN_PROCESO)
			.param("nivel", nivel)
			.param("total", hallazgos.size())
			.param("hallazgos", this.json.writeValueAsString(hallazgos))
			.param("creado_por", creadoPor)
			.param("creado_en", LocalDateTime.ofInstant(creadoEn, ZoneOffset.UTC))
			.update(llave);
		return llave.getKey().longValue();
	}

	public void actualizar(long id, String estado, String objeto, int bytes) {
		this.jdbc.sql("UPDATE analisis SET estado = :estado, objeto = :objeto, bytes = :bytes WHERE id = :id")
			.param("estado", estado)
			.param("objeto", objeto)
			.param("bytes", bytes)
			.param("id", id)
			.update();
	}

	public Optional<Analisis> buscar(long id) {
		return this.jdbc.sql("""
				SELECT id, sitio_id, estado, nivel, total_hallazgos, hallazgos, objeto, bytes, creado_por, creado_en
				FROM analisis WHERE id = :id
				""").param("id", id).query(this::mapear).optional();
	}

	private Analisis mapear(ResultSet rs, int fila) throws SQLException {
		LocalDateTime creado = rs.getObject("creado_en", LocalDateTime.class);
		return new Analisis(rs.getLong("id"), rs.getLong("sitio_id"), rs.getString("estado"), rs.getString("nivel"),
				rs.getInt("total_hallazgos"), this.json.readValue(rs.getString("hallazgos"), LISTA_HALLAZGOS),
				rs.getString("objeto"), rs.getInt("bytes"), rs.getString("creado_por"),
				(creado != null) ? creado.toInstant(ZoneOffset.UTC) : null);
	}

}
