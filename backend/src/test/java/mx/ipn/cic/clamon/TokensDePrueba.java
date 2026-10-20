package mx.ipn.cic.clamon;

import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

public final class TokensDePrueba {

	public static final String ANALISTA = "rol-analista";

	public static final String SUPERVISOR = "rol-supervisor";

	public static final String ADMINISTRADOR = "rol-administrador";

	public static final String SIN_ROL = "sin-rol";

	private TokensDePrueba() {
	}

	public static void configurar(JwtDecoder decodificador) {
		given(decodificador.decode(anyString())).willAnswer(i -> jwt(i.getArgument(0)));
	}

	public static String sub(String token) {
		return "sub-" + (token.equals(SIN_ROL) ? "sin.rol" : token.substring(4) + ".prueba");
	}

	static Jwt jwt(String token) {
		List<String> roles;
		if (token.startsWith("rol-")) {
			roles = List.of(token.substring(4));
		}
		else if (token.equals(SIN_ROL)) {
			roles = List.of();
		}
		else {
			throw new BadJwtException("token invalido");
		}
		String usuario = token.equals(SIN_ROL) ? "sin.rol" : token.substring(4) + ".prueba";
		return Jwt.withTokenValue(token)
			.header("alg", "RS256")
			.subject("sub-" + usuario)
			.claim("usuario", usuario)
			.claim("roles", roles)
			.audience(List.of("clamon-api"))
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(300))
			.build();
	}

}
