package mx.ipn.cic.clamon.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import jakarta.servlet.DispatcherType;
import mx.ipn.cic.clamon.security.EmisorTokens;
import mx.ipn.cic.clamon.security.FiltroRegistroSolicitudes;
import mx.ipn.cic.clamon.security.RespuestasSeguridad;
import mx.ipn.cic.clamon.security.RolesTokenConverter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

	static final int MIN_BYTES_SECRETO = 32;

	@Bean
	SecurityFilterChain cadenaSeguridad(HttpSecurity http, ClamonProperties propiedades) throws Exception {
		RespuestasSeguridad respuestas = new RespuestasSeguridad();
		JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
		convertidor.setJwtGrantedAuthoritiesConverter(new RolesTokenConverter());
		convertidor.setPrincipalClaimName("usuario");

		List<String> origenes = propiedades.corsOrigenes();
		if (origenes != null && !origenes.isEmpty()) {
			http.cors(c -> c.configurationSource(cors(origenes)));
		}
		http.csrf(csrf -> csrf.disable())
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.httpBasic(b -> b.disable())
			.formLogin(f -> f.disable())
			.logout(l -> l.disable())
			.requestCache(c -> c.disable())
			.authorizeHttpRequests(a -> a.dispatcherTypeMatchers(DispatcherType.ERROR)
				.permitAll()
				.requestMatchers(HttpMethod.GET, "/api/salud", "/actuator/health")
				.permitAll()
				.requestMatchers(HttpMethod.POST, "/api/sesion")
				.permitAll()
				.requestMatchers(HttpMethod.GET, "/api/yo")
				.authenticated()
				.requestMatchers(HttpMethod.GET, "/api/sitios")
				.hasAnyRole("analista", "supervisor")
				.requestMatchers(HttpMethod.POST, "/api/sitios")
				.hasRole("supervisor")
				.requestMatchers(HttpMethod.POST, "/api/clasificacion")
				.hasRole("analista")
				.requestMatchers(HttpMethod.POST, "/api/analisis")
				.hasRole("analista")
				.requestMatchers(HttpMethod.GET, "/api/analisis/*")
				.hasAnyRole("analista", "supervisor")
				.requestMatchers(HttpMethod.GET, "/api/diagnostico")
				.hasRole("administrador")
				.requestMatchers(HttpMethod.GET, "/api/usuarios")
				.hasRole("administrador")
				.requestMatchers(HttpMethod.POST, "/api/usuarios")
				.hasRole("administrador")
				.requestMatchers(HttpMethod.POST, "/api/usuarios/*")
				.hasRole("administrador")
				.anyRequest()
				.denyAll())
			.oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(convertidor))
				.authenticationEntryPoint(respuestas)
				.accessDeniedHandler(respuestas))
			.exceptionHandling(e -> e.authenticationEntryPoint(respuestas).accessDeniedHandler(respuestas))
			.addFilterAfter(new FiltroRegistroSolicitudes(), SecurityContextHolderFilter.class);
		return http.build();
	}

	static UrlBasedCorsConfigurationSource cors(List<String> origenes) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(origenes);
		config.setAllowedMethods(List.of("GET", "POST"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		config.setMaxAge(Duration.ofHours(1));
		UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
		fuente.registerCorsConfiguration("/api/**", config);
		return fuente;
	}

	@Bean
	SecretKey llaveSesion(ClamonProperties propiedades) {
		return llave(propiedades.sesion().jwtSecreto());
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey llaveSesion) {
		NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(llaveSesion)
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		decodificador.setJwtValidator(validador(EmisorTokens.EMISOR, EmisorTokens.AUDIENCIA));
		return decodificador;
	}

	@Bean
	EmisorTokens emisorTokens(SecretKey llaveSesion, ClamonProperties propiedades) {
		JwtEncoder codificador = new NimbusJwtEncoder(new ImmutableSecret<>(llaveSesion));
		return new EmisorTokens(codificador, Duration.ofMinutes(propiedades.sesion().duracionMin()));
	}

	@Bean
	PasswordEncoder cifradorContrasenas() {
		return new BCryptPasswordEncoder(12);
	}

	static SecretKey llave(String secreto) {
		byte[] bytes = (secreto == null) ? new byte[0] : secreto.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < MIN_BYTES_SECRETO) {
			throw new IllegalStateException("JWT_SECRETO debe tener al menos " + MIN_BYTES_SECRETO + " bytes");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	public static OAuth2TokenValidator<Jwt> validador(String emisor, String audiencia) {
		return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(emisor),
				new JwtAudienceValidator(audiencia));
	}

}
