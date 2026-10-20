package mx.ipn.cic.clamon.client;

import java.util.Map;

import mx.ipn.cic.clamon.client.dto.EstadoServicio;
import mx.ipn.cic.clamon.client.dto.ResultadoScraper;
import mx.ipn.cic.clamon.exception.ServicioNoDisponible;
import mx.ipn.cic.clamon.exception.SitioNoPermitido;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class ScraperClient {

	private final RestClient cliente;

	private final RestClient diagnostico;

	public ScraperClient(RestClient cliente, RestClient diagnostico) {
		this.cliente = cliente;
		this.diagnostico = diagnostico;
	}

	public ResultadoScraper extraer(String url) {
		try {
			ResultadoScraper resultado = this.cliente.post()
				.uri("/extraer")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("url", url))
				.retrieve()
				.body(ResultadoScraper.class);
			if (resultado == null) {
				throw new ServicioNoDisponible("descarga_fallida", "el scraper respondio sin cuerpo", null);
			}
			return resultado;
		}
		catch (RestClientResponseException ex) {
			String cuerpo = ex.getResponseBodyAsString();
			if (ex.getStatusCode().value() == 403 && cuerpo.contains("\"sitio_no_permitido\"")) {
				throw new SitioNoPermitido("el scraper rechazo el sitio");
			}
			throw new ServicioNoDisponible("descarga_fallida", "el scraper respondio " + ex.getStatusCode().value(),
					ex);
		}
		catch (RestClientException ex) {
			throw new ServicioNoDisponible("descarga_fallida",
					"el scraper no respondio: " + ex.getClass().getSimpleName(), ex);
		}
	}

	public EstadoServicio salud() {
		return ConsultaSalud.consultar(this.diagnostico);
	}

}
