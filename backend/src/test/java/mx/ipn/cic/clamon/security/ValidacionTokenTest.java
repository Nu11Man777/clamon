package mx.ipn.cic.clamon.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import mx.ipn.cic.clamon.model.Usuario;
import org.junit.jupiter.api.Test;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidacionTokenTest {

	static final String SECRETO = "secreto-de-pruebas-de-al-menos-32-bytes";

	static final SecretKey LLAVE = TokenesSeguridad.llave(SECRETO);

	static final NimbusJwtDecoder DECODIFICADOR = TokenesSeguridad.decodificador(LLAVE);

	static final EmisorTokens EMISOR = new EmisorTokens(new NimbusJwtEncoder(new ImmutableSecret<>(LLAVE)),
			Duration.ofMinutes(30));

	static final Usuario ANA = new Usuario("sub-1", "ana.analista", "x", List.of("analista"), true, 0, null,
			Instant.now(), null);

	static String token(String emisor, List<String> audiencia, Instant expira, String secreto) throws Exception {
		JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(emisor)
			.audience(audiencia)
			.subject("sub-1")
			.claim("usuario", "ana.analista")
			.claim("roles", List.of("analista", "rol-ajeno"))
			.issueTime(Date.from(expira.minusSeconds(1800)))
			.expirationTime(Date.from(expira))
			.build();
		SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
		jwt.sign(new MACSigner(secreto.getBytes()));
		return jwt.serialize();
	}

	@Test
	void elTokenEmitidoSeValidaYTraeLosRoles() {
		Jwt jwt = DECODIFICADOR.decode(EMISOR.emitir(ANA, Instant.now()));
		assertThat(jwt.getSubject()).isEqualTo("sub-1");
		assertThat(jwt.getClaimAsString("usuario")).isEqualTo("ana.analista");
		assertThat(jwt.getAudience()).containsExactly("clamon-api");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("clamon-backend");
		assertThat(jwt.getExpiresAt()).isAfter(Instant.now().plusSeconds(1700));
		assertThat(new RolesTokenConverter().convert(jwt)).extracting(a -> a.getAuthority())
			.containsExactly("ROLE_analista");
	}

	@Test
	void rolesAjenosSeIgnoran() throws Exception {
		Jwt jwt = DECODIFICADOR.decode(token("clamon-backend", List.of("clamon-api"), Instant.now().plusSeconds(60),
				SECRETO));
		assertThat(RolesTokenConverter.roles(jwt)).containsExactly("analista");
	}

	@Test
	void audienciaDistintaSeRechaza() {
		assertThatThrownBy(() -> DECODIFICADOR
			.decode(token("clamon-backend", List.of("otra-api"), Instant.now().plusSeconds(300), SECRETO)))
			.isInstanceOf(JwtValidationException.class);
	}

	@Test
	void emisorDistintoSeRechaza() {
		assertThatThrownBy(() -> DECODIFICADOR
			.decode(token("https://otro/emisor", List.of("clamon-api"), Instant.now().plusSeconds(300), SECRETO)))
			.isInstanceOf(JwtValidationException.class);
	}

	@Test
	void tokenVencidoSeRechazaFueraDeLaTolerancia() throws Exception {
		assertThatThrownBy(() -> DECODIFICADOR
			.decode(token("clamon-backend", List.of("clamon-api"), Instant.now().minusSeconds(120), SECRETO)))
			.isInstanceOf(JwtValidationException.class);
		DECODIFICADOR.decode(token("clamon-backend", List.of("clamon-api"), Instant.now().minusSeconds(30), SECRETO));
	}

	@Test
	void firmaConOtroSecretoSeRechaza() {
		assertThatThrownBy(() -> DECODIFICADOR.decode(token("clamon-backend", List.of("clamon-api"),
				Instant.now().plusSeconds(300), "otro-secreto-de-al-menos-32-bytes-xx")))
			.isInstanceOf(BadJwtException.class);
	}

	@Test
	void secretoCortoNoArranca() {
		assertThatThrownBy(() -> TokenesSeguridad.llave("corto")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> TokenesSeguridad.llave(null)).isInstanceOf(IllegalStateException.class);
	}

	static final class TokenesSeguridad {

		static SecretKey llave(String secreto) {
			return mx.ipn.cic.clamon.config.SecurityConfigAcceso.llave(secreto);
		}

		static NimbusJwtDecoder decodificador(SecretKey llave) {
			NimbusJwtDecoder d = NimbusJwtDecoder.withSecretKey(llave).macAlgorithm(MacAlgorithm.HS256).build();
			d.setJwtValidator(
					mx.ipn.cic.clamon.config.SecurityConfig.validador(EmisorTokens.EMISOR, EmisorTokens.AUDIENCIA));
			return d;
		}

	}

}
