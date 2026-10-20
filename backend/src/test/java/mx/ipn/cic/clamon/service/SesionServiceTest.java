package mx.ipn.cic.clamon.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import mx.ipn.cic.clamon.config.SecurityConfigAcceso;
import mx.ipn.cic.clamon.dto.response.Sesion;
import mx.ipn.cic.clamon.exception.ErrorApi;
import mx.ipn.cic.clamon.model.Usuario;
import mx.ipn.cic.clamon.repository.BitacoraRepository;
import mx.ipn.cic.clamon.repository.UsuarioRepository;
import mx.ipn.cic.clamon.security.EmisorTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class SesionServiceTest {

	static final BCryptPasswordEncoder CIFRADOR = new BCryptPasswordEncoder(4);

	static final String HASH = CIFRADOR.encode("contrasena-correcta");

	UsuarioRepository usuarios;

	BitacoraRepository bitacora;

	SesionService servicio;

	@BeforeEach
	void preparar() {
		this.usuarios = mock(UsuarioRepository.class);
		this.bitacora = mock(BitacoraRepository.class);
		EmisorTokens emisor = new EmisorTokens(
				new NimbusJwtEncoder(new ImmutableSecret<>(SecurityConfigAcceso.llave("x".repeat(32)))),
				Duration.ofMinutes(30));
		this.servicio = new SesionService(this.usuarios, this.bitacora, CIFRADOR, emisor);
	}

	static Usuario cuenta(boolean activo, int intentos, Instant bloqueadoHasta) {
		return new Usuario("sub-1", "ana.analista", HASH, List.of("analista"), activo, intentos, bloqueadoHasta,
				Instant.now(), null);
	}

	void rechaza(String usuario, String contrasena) {
		assertThatThrownBy(() -> this.servicio.iniciar(usuario, contrasena)).isInstanceOf(ErrorApi.class)
			.extracting(e -> ((ErrorApi) e).codigo())
			.isEqualTo("credenciales_invalidas");
	}

	@Test
	void credencialesCorrectas() {
		given(this.usuarios.buscarPorUsuario("ana.analista")).willReturn(Optional.of(cuenta(true, 2, null)));
		Sesion sesion = this.servicio.iniciar("  Ana.Analista ", "contrasena-correcta");
		assertThat(sesion.token()).contains(".");
		assertThat(sesion.tipo()).isEqualTo("Bearer");
		assertThat(sesion.expiraEn()).isEqualTo(1800);
		assertThat(sesion.roles()).containsExactly("analista");
		then(this.usuarios).should().registrarAcceso(eq("sub-1"), any());
		then(this.bitacora).should().registrarSinFallar("sub-1", "iniciar_sesion", "roles: analista");
	}

	@Test
	void contrasenaIncorrectaSumaUnIntento() {
		given(this.usuarios.buscarPorUsuario("ana.analista")).willReturn(Optional.of(cuenta(true, 0, null)));
		rechaza("ana.analista", "otra-contrasena");
		then(this.usuarios).should().registrarFallo(eq("sub-1"), eq(5), any());
		then(this.usuarios).should(never()).registrarAcceso(anyString(), any());
	}

	@Test
	void cuentaBloqueadaSeRechazaAunConLaContrasenaCorrecta() {
		given(this.usuarios.buscarPorUsuario("ana.analista"))
			.willReturn(Optional.of(cuenta(true, 0, Instant.now().plusSeconds(600))));
		rechaza("ana.analista", "contrasena-correcta");
		then(this.usuarios).should(never()).registrarAcceso(anyString(), any());
	}

	@Test
	void bloqueoVencidoPermiteEntrar() {
		given(this.usuarios.buscarPorUsuario("ana.analista"))
			.willReturn(Optional.of(cuenta(true, 0, Instant.now().minusSeconds(1))));
		assertThat(this.servicio.iniciar("ana.analista", "contrasena-correcta").usuario()).isEqualTo("ana.analista");
	}

	@Test
	void cuentaInactivaSeRechaza() {
		given(this.usuarios.buscarPorUsuario("ana.analista")).willReturn(Optional.of(cuenta(false, 0, null)));
		rechaza("ana.analista", "contrasena-correcta");
	}

	@Test
	void usuarioInexistenteSeRechazaSinTocarLaBitacora() {
		given(this.usuarios.buscarPorUsuario(anyString())).willReturn(Optional.empty());
		rechaza("nadie", "contrasena-correcta");
		then(this.usuarios).should(never()).registrarFallo(anyString(), anyInt(), any());
		then(this.bitacora).shouldHaveNoInteractions();
	}

	@Test
	void contrasenaMayorQueElLimiteDeBcryptSeRechazaSinExcepcion() {
		given(this.usuarios.buscarPorUsuario("ana.analista")).willReturn(Optional.of(cuenta(true, 0, null)));
		rechaza("ana.analista", "x".repeat(100));
	}

}
