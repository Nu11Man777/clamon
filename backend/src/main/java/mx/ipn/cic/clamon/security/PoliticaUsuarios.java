package mx.ipn.cic.clamon.security;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import mx.ipn.cic.clamon.exception.ErrorApi;

import org.springframework.http.HttpStatus;

public final class PoliticaUsuarios {

	public static final int MIN_CONTRASENA = 12;

	public static final int MAX_BYTES_CONTRASENA = 72;

	private static final Pattern NOMBRE = Pattern.compile("^[a-z0-9._-]{3,64}$");

	private PoliticaUsuarios() {
	}

	public static String normalizarNombre(String usuario) {
		return (usuario == null) ? "" : usuario.strip().toLowerCase(Locale.ROOT);
	}

	public static void validarNombre(String usuario) {
		if (!NOMBRE.matcher(usuario).matches()) {
			throw invalido("usuario: de 3 a 64 caracteres entre a-z, 0-9, punto, guion y guion bajo");
		}
	}

	public static void validarContrasena(String contrasena, String usuario) {
		if (contrasena == null || contrasena.length() < MIN_CONTRASENA) {
			throw invalido("contrasena: al menos " + MIN_CONTRASENA + " caracteres");
		}
		if (!cabeEnBcrypt(contrasena)) {
			throw invalido("contrasena: como maximo " + MAX_BYTES_CONTRASENA + " bytes");
		}
		if (contrasena.toLowerCase(Locale.ROOT).equals(usuario)) {
			throw invalido("contrasena: no puede ser igual al usuario");
		}
	}

	public static List<String> validarRoles(Collection<String> roles) {
		if (roles == null) {
			throw invalido("roles: se espera una lista, que puede estar vacia");
		}
		for (String rol : roles) {
			if (!RolesTokenConverter.ROLES.contains(rol)) {
				throw invalido("roles: '" + rol + "' no es un rol de CLAMON");
			}
		}
		return roles.stream().distinct().sorted().toList();
	}

	public static boolean cabeEnBcrypt(String contrasena) {
		return contrasena.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES_CONTRASENA;
	}

	private static ErrorApi invalido(String detalle) {
		return new ErrorApi(HttpStatus.BAD_REQUEST, "validacion", detalle);
	}

}
