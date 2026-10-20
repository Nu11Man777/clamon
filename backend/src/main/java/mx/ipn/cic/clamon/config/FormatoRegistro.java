package mx.ipn.cic.clamon.config;

import java.util.LinkedHashMap;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import org.slf4j.event.KeyValuePair;

import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import org.springframework.core.env.Environment;

public class FormatoRegistro implements StructuredLogFormatter<ILoggingEvent> {

	private static final JsonWriter<Map<String, Object>> JSON = JsonWriter.standard();

	private final String servicio;

	public FormatoRegistro(Environment entorno) {
		this.servicio = entorno.getProperty("spring.application.name", "clamon-backend");
	}

	@Override
	public String format(ILoggingEvent evento) {
		Map<String, Object> entrada = new LinkedHashMap<>();
		entrada.put("severity", severidad(evento.getLevel()));
		entrada.put("message", evento.getFormattedMessage());
		entrada.put("servicio", this.servicio);
		entrada.put("logger", evento.getLoggerName());
		if (evento.getKeyValuePairs() != null) {
			for (KeyValuePair par : evento.getKeyValuePairs()) {
				entrada.put(par.key, par.value);
			}
		}
		IThrowableProxy excepcion = evento.getThrowableProxy();
		if (excepcion != null) {
			entrada.put("excepcion", ThrowableProxyUtil.asString(excepcion));
		}
		return JSON.writeToString(entrada) + "\n";
	}

	static String severidad(Level nivel) {
		return switch (nivel.toInt()) {
			case Level.ERROR_INT -> "ERROR";
			case Level.WARN_INT -> "WARNING";
			case Level.INFO_INT -> "INFO";
			default -> "DEBUG";
		};
	}

}
