package mx.ipn.cic.clamon.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import mx.ipn.cic.clamon.client.AlmacenArchivos;
import mx.ipn.cic.clamon.client.ClasificadorClient;
import mx.ipn.cic.clamon.client.ScraperClient;
import mx.ipn.cic.clamon.client.dto.ResultadoClasificador;
import mx.ipn.cic.clamon.client.dto.ResultadoScraper;
import mx.ipn.cic.clamon.exception.ErrorApi;
import mx.ipn.cic.clamon.exception.SitioNoPermitido;
import mx.ipn.cic.clamon.model.Analisis;
import mx.ipn.cic.clamon.model.Sitio;
import mx.ipn.cic.clamon.repository.AnalisisRepository;
import mx.ipn.cic.clamon.repository.BitacoraRepository;
import mx.ipn.cic.clamon.repository.SitioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.exception.SdkException;

@Service
public class AnalisisService {

	private static final Logger log = LoggerFactory.getLogger(AnalisisService.class);

	private final SitioRepository sitios;

	private final AnalisisRepository analisis;

	private final BitacoraRepository bitacora;

	private final ScraperClient scraper;

	private final ClasificadorClient clasificador;

	private final AlmacenArchivos archivos;

	public AnalisisService(SitioRepository sitios, AnalisisRepository analisis, BitacoraRepository bitacora,
			ScraperClient scraper, ClasificadorClient clasificador, AlmacenArchivos archivos) {
		this.sitios = sitios;
		this.analisis = analisis;
		this.bitacora = bitacora;
		this.scraper = scraper;
		this.clasificador = clasificador;
		this.archivos = archivos;
	}

	public Analisis consultar(long id) {
		return this.analisis.buscar(id)
			.orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "analisis_no_encontrado", "el analisis no existe"));
	}

	public Analisis analizar(long sitioId, String sub, String usuario) {
		long inicio = System.nanoTime();
		Sitio sitio = this.sitios.buscar(sitioId)
			.orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "sitio_no_encontrado", "el sitio no existe"));
		log.atInfo()
			.addKeyValue("evento", "analisis.inicio")
			.addKeyValue("usuario", usuario)
			.addKeyValue("sitio_id", sitioId)
			.addKeyValue("host", sitio.host())
			.log("analisis iniciado");

		ResultadoScraper extraccion;
		try {
			extraccion = this.scraper.extraer(sitio.url());
		}
		catch (SitioNoPermitido ex) {
			log.atWarn()
				.addKeyValue("evento", "analisis.sitio_no_permitido")
				.addKeyValue("usuario", usuario)
				.addKeyValue("sitio_id", sitioId)
				.addKeyValue("host", sitio.host())
				.log("el scraper rechazo el sitio");
			this.bitacora.registrarSinFallar(sub, "analisis_rechazado", "sitio " + sitioId + ": sitio_no_permitido");
			throw ex;
		}

		String texto = (extraccion.texto() != null) ? extraccion.texto() : "";
		ResultadoClasificador clasificacion = texto.isEmpty() ? ResultadoClasificador.vacio() : this.clasificador.clasificar(texto);

		long id = this.analisis.crear(sitioId, clasificacion.nivel(), clasificacion.hallazgos(), usuario,
				Instant.now().truncatedTo(ChronoUnit.MILLIS));
		String objeto = "analisis/" + id + "/contenido.txt";
		int bytes;
		try {
			bytes = this.archivos.guardarTexto(objeto, texto);
		}
		catch (SdkException ex) {
			this.analisis.actualizar(id, Analisis.FALLIDO, "", 0);
			log.atError()
				.addKeyValue("evento", "analisis.fallido")
				.addKeyValue("usuario", usuario)
				.addKeyValue("analisis_id", id)
				.addKeyValue("tipo", ex.getClass().getSimpleName())
				.log("no se pudo guardar el contenido en MinIO");
			throw ex;
		}
		this.analisis.actualizar(id, Analisis.COMPLETADO, objeto, bytes);
		this.bitacora.registrarSinFallar(sub, "lanzar_analisis",
				"analisis " + id + " del sitio " + sitioId + ": nivel " + clasificacion.nivel());

		log.atInfo()
			.addKeyValue("evento", "analisis.completado")
			.addKeyValue("usuario", usuario)
			.addKeyValue("analisis_id", id)
			.addKeyValue("nivel", clasificacion.nivel())
			.addKeyValue("total_hallazgos", clasificacion.hallazgos().size())
			.addKeyValue("duracion_ms", (System.nanoTime() - inicio) / 1_000_000)
			.log("analisis completado");
		return this.analisis.buscar(id)
			.orElseThrow(() -> new ErrorApi(HttpStatus.INTERNAL_SERVER_ERROR, "error_interno", "analisis perdido"));
	}

}
