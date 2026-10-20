package mx.ipn.cic.clamon.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import mx.ipn.cic.clamon.model.Usuario;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

public class EmisorTokens {

	public static final String EMISOR = "clamon-backend";

	public static final String AUDIENCIA = "clamon-api";

	private final JwtEncoder codificador;

	private final Duration duracion;

	public EmisorTokens(JwtEncoder codificador, Duration duracion) {
		this.codificador = codificador;
		this.duracion = duracion;
	}

	public Duration duracion() {
		return this.duracion;
	}

	public String emitir(Usuario usuario, Instant ahora) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(EMISOR)
			.audience(List.of(AUDIENCIA))
			.subject(usuario.sub())
			.id(UUID.randomUUID().toString())
			.issuedAt(ahora)
			.expiresAt(ahora.plus(this.duracion))
			.claim("usuario", usuario.usuario())
			.claim("roles", usuario.roles())
			.build();
		JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
		return this.codificador.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
	}

}
