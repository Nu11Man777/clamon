package mx.ipn.cic.clamon.controller;

import jakarta.validation.Valid;
import mx.ipn.cic.clamon.client.dto.ResultadoClasificador;
import mx.ipn.cic.clamon.dto.request.PeticionClasificacion;
import mx.ipn.cic.clamon.service.ClasificacionService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clasificacion")
public class ClasificacionController {

	private final ClasificacionService servicio;

	public ClasificacionController(ClasificacionService servicio) {
		this.servicio = servicio;
	}

	@PostMapping
	ResultadoClasificador clasificar(@Valid @RequestBody PeticionClasificacion peticion, @AuthenticationPrincipal Jwt jwt) {
		return this.servicio.clasificar(peticion.texto(), jwt.getSubject());
	}

}
