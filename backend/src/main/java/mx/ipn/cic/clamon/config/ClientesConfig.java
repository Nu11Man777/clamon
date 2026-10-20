package mx.ipn.cic.clamon.config;

import java.time.Duration;

import mx.ipn.cic.clamon.client.ClasificadorClient;
import mx.ipn.cic.clamon.client.ScraperClient;
import mx.ipn.cic.clamon.client.TokenIdentidadInterceptor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class ClientesConfig {

	public static final Duration ESPERA_OPERACION = Duration.ofSeconds(10);

	public static final Duration ESPERA_DIAGNOSTICO = Duration.ofSeconds(3);

	@Bean
	ClasificadorClient clasificadorClient(RestClient.Builder builder, ClamonProperties propiedades) {
		String url = propiedades.servicios().clasificadorUrl();
		TokenIdentidadInterceptor token = new TokenIdentidadInterceptor(url,
				propiedades.servicios().autenticacionGoogle());
		return new ClasificadorClient(cliente(builder, url, ESPERA_OPERACION, token),
				cliente(builder, url, ESPERA_DIAGNOSTICO, token));
	}

	@Bean
	ScraperClient scraperClient(RestClient.Builder builder, ClamonProperties propiedades) {
		String url = propiedades.servicios().scraperUrl();
		TokenIdentidadInterceptor token = new TokenIdentidadInterceptor(url,
				propiedades.servicios().autenticacionGoogle());
		return new ScraperClient(cliente(builder, url, ESPERA_OPERACION.plusSeconds(2), token),
				cliente(builder, url, ESPERA_DIAGNOSTICO, token));
	}

	private static RestClient cliente(RestClient.Builder builder, String url, Duration espera,
			TokenIdentidadInterceptor token) {
		return builder.clone()
			.baseUrl(url)
			.requestFactory(fabrica(espera))
			.requestInterceptor(token)
			.build();
	}

	static SimpleClientHttpRequestFactory fabrica(Duration espera) {
		SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
		fabrica.setConnectTimeout(espera);
		fabrica.setReadTimeout(espera);
		return fabrica;
	}

}
