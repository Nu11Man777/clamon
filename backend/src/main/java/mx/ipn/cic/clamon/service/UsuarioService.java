package mx.ipn.cic.clamon.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import mx.ipn.cic.clamon.dto.request.PeticionCrearUsuario;
import mx.ipn.cic.clamon.dto.request.PeticionModificarUsuario;
import mx.ipn.cic.clamon.dto.response.Cuenta;
import mx.ipn.cic.clamon.dto.response.Yo;
import mx.ipn.cic.clamon.exception.ErrorApi;
import mx.ipn.cic.clamon.model.Usuario;
import mx.ipn.cic.clamon.repository.BitacoraRepository;
import mx.ipn.cic.clamon.repository.UsuarioRepository;
import mx.ipn.cic.clamon.security.PoliticaUsuarios;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UsuarioService {

	private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

	private final UsuarioRepository usuarios;

	private final BitacoraRepository bitacora;

	private final PasswordEncoder cifrador;

	public UsuarioService(UsuarioRepository usuarios, BitacoraRepository bitacora, PasswordEncoder cifrador) {
		this.usuarios = usuarios;
		this.bitacora = bitacora;
		this.cifrador = cifrador;
	}

	public Yo yo(String sub) {
		Usuario u = this.usuarios.buscarPorSub(sub)
			.filter(Usuario::activo)
			.orElseThrow(() -> new ErrorApi(HttpStatus.UNAUTHORIZED, "no_autenticado", "la cuenta no esta activa"));
		return new Yo(u.sub(), u.usuario(), u.roles(), u.ultimoAcceso());
	}

	public List<Cuenta> listar() {
		Instant ahora = Instant.now();
		return this.usuarios.listar().stream().map(u -> Cuenta.de(u, ahora)).toList();
	}

	public Cuenta crear(PeticionCrearUsuario peticion, String subAdmin, String admin) {
		String usuario = PoliticaUsuarios.normalizarNombre(peticion.usuario());
		PoliticaUsuarios.validarNombre(usuario);
		PoliticaUsuarios.validarContrasena(peticion.contrasena(), usuario);
		List<String> roles = PoliticaUsuarios.validarRoles(peticion.roles());
		Usuario nuevo = new Usuario(UUID.randomUUID().toString(), usuario, this.cifrador.encode(peticion.contrasena()),
				roles, true, 0, null, Instant.now().truncatedTo(ChronoUnit.MILLIS), null);
		try {
			this.usuarios.crear(nuevo);
		}
		catch (DuplicateKeyException ex) {
			throw new ErrorApi(HttpStatus.CONFLICT, "usuario_duplicado", "el usuario ya existe");
		}
		auditar(subAdmin, admin, "crear_usuario", usuario + " roles: " + String.join(",", roles));
		return Cuenta.de(nuevo, Instant.now());
	}

	public Cuenta modificar(String nombre, PeticionModificarUsuario peticion, String subAdmin, String admin) {
		Usuario actual = this.usuarios.buscarPorUsuario(PoliticaUsuarios.normalizarNombre(nombre))
			.orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "usuario_no_encontrado", "el usuario no existe"));
		List<String> roles = (peticion.roles() != null) ? PoliticaUsuarios.validarRoles(peticion.roles())
				: actual.roles();
		boolean activo = (peticion.activo() != null) ? peticion.activo() : actual.activo();
		if (actual.sub().equals(subAdmin) && (!activo || !roles.contains("administrador"))) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "operacion_no_permitida",
					"un administrador no puede desactivarse ni quitarse el rol administrador");
		}
		String hash = actual.hashContrasena();
		List<String> cambios = new ArrayList<>();
		if (peticion.contrasena() != null) {
			PoliticaUsuarios.validarContrasena(peticion.contrasena(), actual.usuario());
			hash = this.cifrador.encode(peticion.contrasena());
			cambios.add("contrasena");
		}
		if (peticion.roles() != null) {
			cambios.add("roles: " + String.join(",", roles));
		}
		if (peticion.activo() != null) {
			cambios.add("activo: " + activo);
		}
		this.usuarios.actualizar(actual.sub(), hash, roles, activo);
		auditar(subAdmin, admin, "modificar_usuario", actual.usuario() + " " + String.join("; ", cambios));
		return Cuenta.de(new Usuario(actual.sub(), actual.usuario(), hash, roles, activo, 0, null, actual.creadoEn(),
				actual.ultimoAcceso()), Instant.now());
	}

	private void auditar(String subAdmin, String admin, String accion, String detalle) {
		this.bitacora.registrarSinFallar(subAdmin, accion, detalle);
		log.atInfo()
			.addKeyValue("evento", "usuarios." + accion)
			.addKeyValue("usuario", admin)
			.addKeyValue("detalle", detalle)
			.log("cuenta administrada");
	}

}
