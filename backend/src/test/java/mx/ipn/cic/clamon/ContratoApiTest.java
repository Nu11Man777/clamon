package mx.ipn.cic.clamon;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import mx.ipn.cic.clamon.client.AlmacenArchivos;
import mx.ipn.cic.clamon.client.ClasificadorClient;
import mx.ipn.cic.clamon.client.ScraperClient;
import mx.ipn.cic.clamon.client.dto.ResultadoClasificador;
import mx.ipn.cic.clamon.client.dto.ResultadoScraper;
import mx.ipn.cic.clamon.exception.ServicioNoDisponible;
import mx.ipn.cic.clamon.exception.SitioNoPermitido;
import mx.ipn.cic.clamon.model.Analisis;
import mx.ipn.cic.clamon.model.Hallazgo;
import mx.ipn.cic.clamon.model.Sitio;
import mx.ipn.cic.clamon.repository.AnalisisRepository;
import mx.ipn.cic.clamon.repository.BitacoraRepository;
import mx.ipn.cic.clamon.repository.SitioRepository;
import mx.ipn.cic.clamon.repository.UsuarioRepository;
import static mx.ipn.cic.clamon.TokensDePrueba.ANALISTA;
import static mx.ipn.cic.clamon.TokensDePrueba.SUPERVISOR;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import software.amazon.awssdk.core.exception.SdkClientException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContratoApiTest {

	static final Instant AHORA = Instant.parse("2026-10-14T12:00:00Z");

	static final Sitio SITIO = new Sitio(1, "https://example.com/", "example.com", "supervisor.prueba", AHORA);

	@Autowired
	MockMvc mvc;

	@MockitoBean
	JwtDecoder decodificador;

	@MockitoBean
	UsuarioRepository usuarios;

	@MockitoBean
	BitacoraRepository bitacora;

	@MockitoBean
	SitioRepository sitios;

	@MockitoBean
	AnalisisRepository analisis;

	@MockitoBean
	ClasificadorClient clasificador;

	@MockitoBean
	ScraperClient scraper;

	@MockitoBean
	AlmacenArchivos archivos;

	@BeforeEach
	void preparar() {
		TokensDePrueba.configurar(this.decodificador);
		given(this.sitios.buscar(1)).willReturn(Optional.of(SITIO));
		given(this.sitios.buscar(99)).willReturn(Optional.empty());
	}

	private org.springframework.test.web.servlet.ResultActions lanzar(long sitioId) throws Exception {
		return this.mvc.perform(post("/api/analisis").header("Authorization", "Bearer " + ANALISTA)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"sitio_id\":" + sitioId + "}"));
	}

	@Test
	void analisisCompleto() throws Exception {
		List<Hallazgo> hallazgos = List.of(new Hallazgo("correo", "c****@example.com", 512, 531));
		given(this.scraper.extraer("https://example.com/"))
			.willReturn(new ResultadoScraper("https://example.com/", "example.com", 200, "text/html", 1256, "texto",
					false));
		given(this.clasificador.clasificar("texto"))
			.willReturn(new ResultadoClasificador("medio", 1, Map.of("correo", 1), hallazgos));
		given(this.analisis.crear(eq(1L), eq("medio"), eq(hallazgos), eq("analista.prueba"), any())).willReturn(7L);
		given(this.archivos.guardarTexto("analisis/7/contenido.txt", "texto")).willReturn(5);
		given(this.analisis.buscar(7)).willReturn(Optional.of(new Analisis(7, 1, "completado", "medio", 1, hallazgos,
				"analisis/7/contenido.txt", 5, "analista.prueba", AHORA)));

		lanzar(1).andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(7))
			.andExpect(jsonPath("$.sitio_id").value(1))
			.andExpect(jsonPath("$.estado").value("completado"))
			.andExpect(jsonPath("$.nivel").value("medio"))
			.andExpect(jsonPath("$.total_hallazgos").value(1))
			.andExpect(jsonPath("$.hallazgos[0].valor").value("c****@example.com"))
			.andExpect(jsonPath("$.objeto").value("analisis/7/contenido.txt"))
			.andExpect(jsonPath("$.bytes").value(5));
		then(this.analisis).should().actualizar(7, "completado", "analisis/7/contenido.txt", 5);
	}

	@Test
	void sitioInexistenteResponde404() throws Exception {
		lanzar(99).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("sitio_no_encontrado"));
	}

	@Test
	void sitioRechazadoPorElScraperResponde422() throws Exception {
		given(this.scraper.extraer(anyString())).willThrow(new SitioNoPermitido("rechazado"));
		lanzar(1).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.error").value("sitio_no_permitido"));
		then(this.clasificador).should(never()).clasificar(anyString());
	}

	@Test
	void fallaDeDescargaResponde502() throws Exception {
		given(this.scraper.extraer(anyString()))
			.willThrow(new ServicioNoDisponible("descarga_fallida", "el scraper respondio 403", null));
		lanzar(1).andExpect(status().isBadGateway()).andExpect(jsonPath("$.error").value("descarga_fallida"));
	}

	@Test
	void fallaDeClasificadorResponde502() throws Exception {
		given(this.scraper.extraer(anyString()))
			.willReturn(new ResultadoScraper("u", "example.com", 200, "text/plain", 1, "x", false));
		given(this.clasificador.clasificar(anyString()))
			.willThrow(new ServicioNoDisponible("clasificador_no_disponible", "clasificador no respondio", null));
		lanzar(1).andExpect(status().isBadGateway()).andExpect(jsonPath("$.error").value("clasificador_no_disponible"));
	}

	@Test
	void fallaDeMinioResponde502YMarcaElAnalisis() throws Exception {
		given(this.scraper.extraer(anyString()))
			.willReturn(new ResultadoScraper("u", "example.com", 200, "text/plain", 1, "x", false));
		given(this.clasificador.clasificar(anyString())).willReturn(ResultadoClasificador.vacio());
		given(this.analisis.crear(anyLong(), anyString(), any(), anyString(), any())).willReturn(8L);
		given(this.archivos.guardarTexto(anyString(), anyString())).willThrow(SdkClientException.create("sin red"));
		lanzar(1).andExpect(status().isBadGateway()).andExpect(jsonPath("$.error").value("archivos_no_disponible"));
		then(this.analisis).should().actualizar(8, "fallido", "", 0);
	}

	@Test
	void sitioIdInvalidoResponde400() throws Exception {
		lanzar(0).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("validacion"));
	}

	@Test
	void analisisInexistenteResponde404() throws Exception {
		given(this.analisis.buscar(anyLong())).willReturn(Optional.empty());
		this.mvc.perform(get("/api/analisis/5").header("Authorization", "Bearer " + ANALISTA))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("analisis_no_encontrado"));
	}

	@Test
	void registrarSitio() throws Exception {
		given(this.sitios.crear(eq("https://Example.com/"), eq("example.com"), eq("supervisor.prueba"), any()))
			.willReturn(new Sitio(3, "https://Example.com/", "example.com", "supervisor.prueba", AHORA));
		this.mvc
			.perform(post("/api/sitios").header("Authorization", "Bearer " + SUPERVISOR)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"url\":\" https://Example.com/ \"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(3))
			.andExpect(jsonPath("$.host").value("example.com"))
			.andExpect(jsonPath("$.creado_por").value("supervisor.prueba"))
			.andExpect(jsonPath("$.creado_en").value("2026-10-14T12:00:00Z"));
	}

	@Test
	void sitioConEsquemaNoPermitidoResponde400() throws Exception {
		for (String url : List.of("file:///etc/passwd", "ftp://example.com/", "https://", "no es url")) {
			this.mvc
				.perform(post("/api/sitios").header("Authorization", "Bearer " + SUPERVISOR)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"url\":\"" + url + "\"}"))
				.andExpect(status().isBadRequest());
		}
		then(this.sitios).should(never()).crear(anyString(), anyString(), anyString(), any());
	}

	@Test
	void sitioDuplicadoResponde409() throws Exception {
		given(this.sitios.crear(anyString(), anyString(), anyString(), any()))
			.willThrow(new DuplicateKeyException("uq_sitio_url"));
		this.mvc
			.perform(post("/api/sitios").header("Authorization", "Bearer " + SUPERVISOR)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"url\":\"https://example.com/\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("sitio_duplicado"));
	}

	@Test
	void textoVacioOExcesivoResponde400() throws Exception {
		for (String texto : List.of("", "a".repeat(20_001))) {
			this.mvc
				.perform(post("/api/clasificacion").header("Authorization", "Bearer " + ANALISTA)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"texto\":\"" + texto + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validacion"));
		}
		then(this.clasificador).should(never()).clasificar(anyString());
	}

	@Test
	void clasificacionDevuelveLaRespuestaDelClasificador() throws Exception {
		given(this.clasificador.clasificar("CURP GOMR800101HDFRRN09")).willReturn(new ResultadoClasificador("alto", 1,
				Map.of("curp", 1), List.of(new Hallazgo("curp", "GOMR**************", 5, 23))));
		this.mvc
			.perform(post("/api/clasificacion").header("Authorization", "Bearer " + ANALISTA)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"texto\":\"CURP GOMR800101HDFRRN09\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nivel").value("alto"))
			.andExpect(jsonPath("$.categorias.curp").value(1))
			.andExpect(jsonPath("$.hallazgos[0].valor").value("GOMR**************"));
		then(this.bitacora).should().registrarSinFallar(anyString(), eq("clasificar_texto"), anyString());
	}

	@Test
	void cuerpoNoJsonResponde400() throws Exception {
		this.mvc
			.perform(post("/api/analisis").header("Authorization", "Bearer " + ANALISTA)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{no es json"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("peticion_invalida"));
	}

}
