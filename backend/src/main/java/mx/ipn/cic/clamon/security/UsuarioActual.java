package mx.ipn.cic.clamon.security;

import org.springframework.security.oauth2.jwt.Jwt;

public final class UsuarioActual {

	private UsuarioActual() {
	}

	public static String nombre(Jwt jwt) {
		String usuario = jwt.getClaimAsString("usuario");
		return (usuario != null) ? usuario : jwt.getSubject();
	}

}
