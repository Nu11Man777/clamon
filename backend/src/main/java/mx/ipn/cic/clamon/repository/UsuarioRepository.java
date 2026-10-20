package mx.ipn.cic.clamon.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import mx.ipn.cic.clamon.model.Usuario;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UsuarioRepository {

	private static final String COLUMNAS = """
			SELECT sub, usuario, hash_contrasena, roles, activo, intentos_fallidos, bloqueado_hasta, creado_en,
			       ultimo_acceso FROM usuario""";

	private final JdbcClient jdbc;

	public UsuarioRepository(@Qualifier("jdbcUsuarios") JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<Usuario> buscarPorUsuario(String usuario) {
		return this.jdbc.sql(COLUMNAS + " WHERE usuario = :usuario")
			.param("usuario", usuario)
			.query(UsuarioRepository::mapear)
			.optional();
	}

	public Optional<Usuario> buscarPorSub(String sub) {
		return this.jdbc.sql(COLUMNAS + " WHERE sub = :sub").param("sub", sub).query(UsuarioRepository::mapear).optional();
	}

	public List<Usuario> listar() {
		return this.jdbc.sql(COLUMNAS + " ORDER BY usuario").query(UsuarioRepository::mapear).list();
	}

	public void crear(Usuario u) {
		this.jdbc.sql("""
				INSERT INTO usuario (sub, usuario, hash_contrasena, roles, activo, intentos_fallidos, creado_en)
				VALUES (:sub, :usuario, :hash, :roles, :activo, 0, :creado_en)
				""")
			.param("sub", u.sub())
			.param("usuario", u.usuario())
			.param("hash", u.hashContrasena())
			.param("roles", unir(u.roles()))
			.param("activo", u.activo())
			.param("creado_en", utc(u.creadoEn()))
			.update();
	}

	public void actualizar(String sub, String hash, List<String> roles, boolean activo) {
		this.jdbc.sql("""
				UPDATE usuario SET hash_contrasena = :hash, roles = :roles, activo = :activo,
				                   intentos_fallidos = 0, bloqueado_hasta = NULL
				WHERE sub = :sub
				""").param("hash", hash).param("roles", unir(roles)).param("activo", activo).param("sub", sub).update();
	}

	public void registrarAcceso(String sub, Instant ahora) {
		this.jdbc.sql("""
				UPDATE usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL, ultimo_acceso = :ahora
				WHERE sub = :sub
				""").param("ahora", utc(ahora)).param("sub", sub).update();
	}

	public void registrarFallo(String sub, int maximo, Instant hasta) {
		this.jdbc.sql("""
				UPDATE usuario
				SET bloqueado_hasta = CASE WHEN intentos_fallidos + 1 >= :maximo THEN :hasta ELSE bloqueado_hasta END,
				    intentos_fallidos = CASE WHEN intentos_fallidos + 1 >= :maximo THEN 0 ELSE intentos_fallidos + 1 END
				WHERE sub = :sub
				""").param("maximo", maximo).param("hasta", utc(hasta)).param("sub", sub).update();
	}

	static String unir(List<String> roles) {
		return String.join(",", roles.stream().sorted().toList());
	}

	static LocalDateTime utc(Instant instante) {
		return (instante != null) ? LocalDateTime.ofInstant(instante, ZoneOffset.UTC) : null;
	}

	static String recortar(String texto, int maximo) {
		return texto.length() <= maximo ? texto : texto.substring(0, maximo);
	}

	private static Instant instante(ResultSet rs, String columna) throws SQLException {
		LocalDateTime fecha = rs.getObject(columna, LocalDateTime.class);
		return (fecha != null) ? fecha.toInstant(ZoneOffset.UTC) : null;
	}

	private static Usuario mapear(ResultSet rs, int fila) throws SQLException {
		String roles = rs.getString("roles");
		return new Usuario(rs.getString("sub"), rs.getString("usuario"), rs.getString("hash_contrasena"),
				(roles == null || roles.isBlank()) ? List.of() : Arrays.stream(roles.split(",")).sorted().toList(),
				rs.getBoolean("activo"), rs.getInt("intentos_fallidos"), instante(rs, "bloqueado_hasta"),
				instante(rs, "creado_en"), instante(rs, "ultimo_acceso"));
	}

}
