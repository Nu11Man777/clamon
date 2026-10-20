package mx.ipn.cic.clamon.controller;

import jakarta.validation.Valid;
import mx.ipn.cic.clamon.dto.request.PeticionSesion;
import mx.ipn.cic.clamon.dto.response.Sesion;
import mx.ipn.cic.clamon.service.SesionService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sesion")
public class SesionController {

	private final SesionService servicio;

	public SesionController(SesionService servicio) {
		this.servicio = servicio;
	}

	@PostMapping
	Sesion iniciar(@Valid @RequestBody PeticionSesion peticion) {
		return this.servicio.iniciar(peticion.usuario(), peticion.contrasena());
	}

}
