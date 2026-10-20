package mx.ipn.cic.clamon.client.dto;

import java.util.List;
import java.util.Map;

import mx.ipn.cic.clamon.model.Hallazgo;

public record ResultadoClasificador(String nivel, int total, Map<String, Integer> categorias, List<Hallazgo> hallazgos) {

	public static ResultadoClasificador vacio() {
		return new ResultadoClasificador("ninguno", 0, Map.of(), List.of());
	}

}
