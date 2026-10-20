package mx.ipn.cic.clamon.client;

import mx.ipn.cic.clamon.client.dto.ResultadoScraper;
import mx.ipn.cic.clamon.exception.ServicioNoDisponible;
import mx.ipn.cic.clamon.exception.SitioNoPermitido;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClientesTest {

	@Test
	void scraperDistingueElRechazoDeLaListaDeUnRechazoDeIam() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://scraper");
		MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
		ScraperClient cliente = new ScraperClient(builder.build(), builder.build());

		servidor.expect(requestTo("http://scraper/extraer"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json("{\"url\":\"https://www.wikipedia.org/\"}"))
			.andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"sitio_no_permitido\",\"detalle\":\"x\"}"));
		servidor.expect(requestTo("http://scraper/extraer"))
			.andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_HTML).body("<html>403</html>"));

		assertThatThrownBy(() -> cliente.extraer("https://www.wikipedia.org/")).isInstanceOf(SitioNoPermitido.class);
		assertThatThrownBy(() -> cliente.extraer("https://example.com/")).isInstanceOf(ServicioNoDisponible.class)
			.extracting(e -> ((ServicioNoDisponible) e).codigo())
			.isEqualTo("descarga_fallida");
		servidor.verify();
	}

	@Test
	void scraperLeeLaRespuestaEnSnakeCase() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://scraper");
		MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
		servidor.expect(requestTo("http://scraper/extraer"))
			.andRespond(withSuccess("""
					{"url":"https://example.com/","host":"example.com","estado_http":200,
					 "tipo_contenido":"text/html","bytes":10,"texto":"hola","truncado":false}
					""", MediaType.APPLICATION_JSON));
		ResultadoScraper r = new ScraperClient(builder.build(), builder.build()).extraer("https://example.com/");
		assertThat(r.texto()).isEqualTo("hola");
		assertThat(r.host()).isEqualTo("example.com");
		assertThat(r.estadoHttp()).isEqualTo(200);
		assertThat(r.tipoContenido()).isEqualTo("text/html");
	}

	@Test
	void clasificadorNoDisponible() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://clasificador");
		MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
		servidor.expect(requestTo("http://clasificador/clasificar")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		assertThatThrownBy(() -> new ClasificadorClient(builder.build(), builder.build()).clasificar("x"))
			.isInstanceOf(ServicioNoDisponible.class);
	}

}
