package mx.ipn.cic.clamon.client.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ResultadoScraper(String url, String host, int estadoHttp, String tipoContenido, int bytes,
		String texto, boolean truncado) {
}
