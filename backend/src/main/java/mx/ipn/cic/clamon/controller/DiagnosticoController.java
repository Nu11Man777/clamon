package mx.ipn.cic.clamon.controller;

import mx.ipn.cic.clamon.dto.response.Diagnostico;
import mx.ipn.cic.clamon.dto.response.Salud;
import mx.ipn.cic.clamon.service.DiagnosticoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DiagnosticoController {

	private static final Logger log = LoggerFactory.getLogger(DiagnosticoController.class);

	private final DiagnosticoService servicio;

	private final String version;

	public DiagnosticoController(DiagnosticoService servicio, @Value("${clamon.version}") String version) {
		this.servicio = servicio;
		this.version = version;
	}

	@GetMapping("/salud")
	Salud salud() {
		return new Salud("ok", "clamon-backend", this.version);
	}

	@GetMapping("/diagnostico")
	Diagnostico diagnostico() {
		Diagnostico resultado = this.servicio.diagnosticar();
		var evento = log.atInfo()
			.addKeyValue("evento", "diagnostico.resultado")
			.addKeyValue("estado_general", resultado.estadoGeneral());
		resultado.dependencias().forEach((nombre, d) -> evento.addKeyValue(nombre, d.estado()));
		evento.log("diagnostico completado");
		return resultado;
	}

}
