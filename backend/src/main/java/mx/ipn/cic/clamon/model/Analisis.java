package mx.ipn.cic.clamon.model;

import java.time.Instant;
import java.util.List;

public record Analisis(long id, long sitioId, String estado, String nivel, int totalHallazgos,
		List<Hallazgo> hallazgos, String objeto, int bytes, String creadoPor, Instant creadoEn) {

	public static final String EN_PROCESO = "en_proceso";

	public static final String COMPLETADO = "completado";

	public static final String FALLIDO = "fallido";

}
