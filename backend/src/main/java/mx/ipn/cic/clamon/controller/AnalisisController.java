package mx.ipn.cic.clamon.controller;

import java.net.URI;

import jakarta.validation.Valid;
import mx.ipn.cic.clamon.dto.request.PeticionAnalisis;
import mx.ipn.cic.clamon.model.Analisis;
import mx.ipn.cic.clamon.security.UsuarioActual;
import mx.ipn.cic.clamon.service.AnalisisService;

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
@RequestMapping("/api/analisis")
public class AnalisisController {

	private final AnalisisService servicio;

	public AnalisisController(AnalisisService servicio) {
		this.servicio = servicio;
	}

	@PostMapping
	ResponseEntity<Analisis> lanzar(@Valid @RequestBody PeticionAnalisis peticion, @AuthenticationPrincipal Jwt jwt) {
		Analisis resultado = this.servicio.analizar(peticion.sitioId(), jwt.getSubject(), UsuarioActual.nombre(jwt));
		return ResponseEntity.created(URI.create("/api/analisis/" + resultado.id())).body(resultado);
	}

	@GetMapping("/{id}")
	Analisis consultar(@PathVariable long id) {
		return this.servicio.consultar(id);
	}

}
