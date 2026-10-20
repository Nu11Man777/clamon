package mx.ipn.cic.clamon.controller;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import mx.ipn.cic.clamon.dto.request.PeticionSitio;
import mx.ipn.cic.clamon.model.Sitio;
import mx.ipn.cic.clamon.security.UsuarioActual;
import mx.ipn.cic.clamon.service.SitioService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sitios")
public class SitioController {

	private final SitioService servicio;

	public SitioController(SitioService servicio) {
		this.servicio = servicio;
	}

	@GetMapping
	List<Sitio> listar() {
		return this.servicio.listar();
	}

	@PostMapping
	ResponseEntity<Sitio> registrar(@Valid @RequestBody PeticionSitio peticion, @AuthenticationPrincipal Jwt jwt) {
		Sitio sitio = this.servicio.registrar(peticion.url(), jwt.getSubject(), UsuarioActual.nombre(jwt));
		return ResponseEntity.created(URI.create("/api/sitios/" + sitio.id())).body(sitio);
	}

}
