package mx.ipn.cic.clamon.client;

import java.util.Map;

import mx.ipn.cic.clamon.client.dto.EstadoServicio;
import mx.ipn.cic.clamon.client.dto.ResultadoClasificador;
import mx.ipn.cic.clamon.exception.ServicioNoDisponible;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class ClasificadorClient {

	private final RestClient cliente;

	private final RestClient diagnostico;

	public ClasificadorClient(RestClient cliente, RestClient diagnostico) {
		this.cliente = cliente;
		this.diagnostico = diagnostico;
	}

	public ResultadoClasificador clasificar(String texto) {
		try {
			ResultadoClasificador resultado = this.cliente.post()
				.uri("/clasificar")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("texto", texto))
				.retrieve()
				.body(ResultadoClasificador.class);
			if (resultado == null) {
				throw new ServicioNoDisponible("clasificador_no_disponible", "clasificador respondio sin cuerpo", null);
			}
			return resultado;
		}
		catch (RestClientResponseException ex) {
			throw new ServicioNoDisponible("clasificador_no_disponible", "clasificador respondio " + ex.getStatusCode().value(), ex);
		}
		catch (RestClientException ex) {
			throw new ServicioNoDisponible("clasificador_no_disponible", "clasificador no respondio: " + ex.getClass().getSimpleName(),
					ex);
		}
	}

	public EstadoServicio salud() {
		return ConsultaSalud.consultar(this.diagnostico);
	}

}
