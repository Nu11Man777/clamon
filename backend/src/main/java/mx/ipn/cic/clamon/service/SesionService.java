package mx.ipn.cic.clamon.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import mx.ipn.cic.clamon.dto.response.Sesion;
import mx.ipn.cic.clamon.exception.ErrorApi;
import mx.ipn.cic.clamon.model.Usuario;
import mx.ipn.cic.clamon.repository.BitacoraRepository;
import mx.ipn.cic.clamon.repository.UsuarioRepository;
import mx.ipn.cic.clamon.security.EmisorTokens;
import mx.ipn.cic.clamon.security.PoliticaUsuarios;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class SesionService {

	public static final int MAX_INTENTOS = 5;

	public static final Duration BLOQUEO = Duration.ofMinutes(15);

	private static final Logger log = LoggerFactory.getLogger(SesionService.class);

	private final UsuarioRepository usuarios;

	private final BitacoraRepository bitacora;

	private final PasswordEncoder cifrador;

	private final EmisorTokens emisor;

	private final String hashFicticio;

	public SesionService(UsuarioRepository usuarios, BitacoraRepository bitacora, PasswordEncoder cifrador,
			EmisorTokens emisor) {
		this.usuarios = usuarios;
		this.bitacora = bitacora;
		this.cifrador = cifrador;
		this.emisor = emisor;
		this.hashFicticio = cifrador.encode(UUID.randomUUID().toString());
	}

	public Sesion iniciar(String nombre, String contrasena) {
		String usuario = PoliticaUsuarios.normalizarNombre(nombre);
		Instant ahora = Instant.now();
		boolean contrasenaUsable = contrasena != null && PoliticaUsuarios.cabeEnBcrypt(contrasena);
		Optional<Usuario> cuenta = this.usuarios.buscarPorUsuario(usuario);

		if (cuenta.isEmpty() || !cuenta.get().activo() || cuenta.get().bloqueado(ahora) || !contrasenaUsable) {
			if (contrasenaUsable) {
				this.cifrador.matches(contrasena, this.hashFicticio);
			}
			String motivo = cuenta.isEmpty() ? "usuario_inexistente"
					: !cuenta.get().activo() ? "cuenta_inactiva"
							: cuenta.get().bloqueado(ahora) ? "cuenta_bloqueada" : "contrasena_invalida";
			rechazar(cuenta.orElse(null), motivo);
		}
		Usuario u = cuenta.get();
		if (!this.cifrador.matches(contrasena, u.hashContrasena())) {
			this.usuarios.registrarFallo(u.sub(), MAX_INTENTOS, ahora.plus(BLOQUEO));
			rechazar(u, (u.intentosFallidos() + 1 >= MAX_INTENTOS) ? "contrasena_invalida_bloqueo"
					: "contrasena_invalida");
		}
		this.usuarios.registrarAcceso(u.sub(), ahora);
		this.bitacora.registrarSinFallar(u.sub(), "iniciar_sesion", "roles: " + String.join(",", u.roles()));
		log.atInfo()
			.addKeyValue("evento", "auth.sesion_iniciada")
			.addKeyValue("usuario", u.usuario())
			.addKeyValue("roles", u.roles())
			.log("sesion iniciada");
		return new Sesion(this.emisor.emitir(u, ahora), "Bearer", this.emisor.duracion().toSeconds(), u.usuario(),
				u.roles());
	}

	private void rechazar(Usuario cuenta, String motivo) {
		log.atWarn()
			.addKeyValue("evento", "auth.sesion_rechazada")
			.addKeyValue("usuario", (cuenta != null) ? cuenta.usuario() : null)
			.addKeyValue("motivo", motivo)
			.log("inicio de sesion rechazado");
		if (cuenta != null) {
			this.bitacora.registrarSinFallar(cuenta.sub(), "sesion_rechazada", motivo);
		}
		throw new ErrorApi(HttpStatus.UNAUTHORIZED, "credenciales_invalidas", "usuario o contrasena incorrectos");
	}

}
