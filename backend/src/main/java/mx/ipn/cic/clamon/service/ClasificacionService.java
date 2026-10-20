package mx.ipn.cic.clamon.service;

import mx.ipn.cic.clamon.client.ClasificadorClient;
import mx.ipn.cic.clamon.client.dto.ResultadoClasificador;
import mx.ipn.cic.clamon.repository.BitacoraRepository;

import org.springframework.stereotype.Service;

@Service
public class ClasificacionService {

	private final ClasificadorClient clasificador;

	private final BitacoraRepository bitacora;

	public ClasificacionService(ClasificadorClient clasificador, BitacoraRepository bitacora) {
		this.clasificador = clasificador;
		this.bitacora = bitacora;
	}

	public ResultadoClasificador clasificar(String texto, String sub) {
		ResultadoClasificador resultado = this.clasificador.clasificar(texto);
		this.bitacora.registrarSinFallar(sub, "clasificar_texto",
				"nivel " + resultado.nivel() + ", hallazgos " + resultado.total());
		return resultado;
	}

}
