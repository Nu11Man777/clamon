package mx.ipn.cic.clamon.controller;

import mx.ipn.cic.clamon.dto.response.Yo;
import mx.ipn.cic.clamon.service.UsuarioService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UsuarioController {

	private final UsuarioService servicio;

	public UsuarioController(UsuarioService servicio) {
		this.servicio = servicio;
	}

	@GetMapping("/yo")
	Yo yo(@AuthenticationPrincipal Jwt jwt) {
		return this.servicio.yo(jwt.getSubject());
	}

}
