package mx.ipn.cic.clamon.client;

import mx.ipn.cic.clamon.client.dto.EstadoServicio;

import org.springframework.web.client.RestClient;

final class ConsultaSalud {

	private ConsultaSalud() {
	}

	static EstadoServicio consultar(RestClient cliente) {
		return cliente.get()
			.uri("/salud")
			.exchange((peticion, respuesta) -> new EstadoServicio(respuesta.getStatusCode().value()));
	}

}
