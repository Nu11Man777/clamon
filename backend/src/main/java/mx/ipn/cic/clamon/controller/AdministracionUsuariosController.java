package mx.ipn.cic.clamon.controller;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import mx.ipn.cic.clamon.dto.request.PeticionCrearUsuario;
import mx.ipn.cic.clamon.dto.request.PeticionModificarUsuario;
import mx.ipn.cic.clamon.dto.response.Cuenta;
import mx.ipn.cic.clamon.security.UsuarioActual;
import mx.ipn.cic.clamon.service.UsuarioService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
public class AdministracionUsuariosController {

	private final UsuarioService servicio;

	public AdministracionUsuariosController(UsuarioService servicio) {
		this.servicio = servicio;
	}

	@GetMapping
	List<Cuenta> listar() {
		return this.servicio.listar();
	}

	@PostMapping
	ResponseEntity<Cuenta> crear(@Valid @RequestBody PeticionCrearUsuario peticion, @AuthenticationPrincipal Jwt jwt) {
		Cuenta cuenta = this.servicio.crear(peticion, jwt.getSubject(), UsuarioActual.nombre(jwt));
		return ResponseEntity.created(URI.create("/api/usuarios/" + cuenta.usuario())).body(cuenta);
	}

	@PostMapping("/{usuario}")
	Cuenta modificar(@PathVariable String usuario, @RequestBody PeticionModificarUsuario peticion,
			@AuthenticationPrincipal Jwt jwt) {
		return this.servicio.modificar(usuario, peticion, jwt.getSubject(), UsuarioActual.nombre(jwt));
	}

}
