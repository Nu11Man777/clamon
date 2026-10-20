package mx.ipn.cic.clamon;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import mx.ipn.cic.clamon.client.ClasificadorClient;
import mx.ipn.cic.clamon.client.ScraperClient;
import mx.ipn.cic.clamon.client.dto.ResultadoClasificador;
import mx.ipn.cic.clamon.dto.response.Diagnostico;
import mx.ipn.cic.clamon.dto.response.Sesion;
import mx.ipn.cic.clamon.model.Analisis;
import mx.ipn.cic.clamon.model.Sitio;
import mx.ipn.cic.clamon.model.Usuario;
import mx.ipn.cic.clamon.repository.AnalisisRepository;
import mx.ipn.cic.clamon.repository.BitacoraRepository;
import mx.ipn.cic.clamon.repository.SitioRepository;
import mx.ipn.cic.clamon.repository.UsuarioRepository;
import mx.ipn.cic.clamon.service.AnalisisService;
import mx.ipn.cic.clamon.service.DiagnosticoService;
import mx.ipn.cic.clamon.service.SesionService;
import static mx.ipn.cic.clamon.TokensDePrueba.ADMINISTRADOR;
import static mx.ipn.cic.clamon.TokensDePrueba.ANALISTA;
import static mx.ipn.cic.clamon.TokensDePrueba.SIN_ROL;
import static mx.ipn.cic.clamon.TokensDePrueba.SUPERVISOR;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AutorizacionTest {

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
	AnalisisService servicioAnalisis;

	@MockitoBean
	ClasificadorClient clasificador;

	@MockitoBean
	ScraperClient scraper;

	@MockitoBean
	DiagnosticoService diagnostico;

	@MockitoBean
	SesionService sesion;

	@BeforeEach
	void preparar() {
		TokensDePrueba.configurar(this.decodificador);
		Instant ahora = Instant.parse("2026-10-14T12:00:00Z");
		Sitio sitio = new Sitio(1, "https://example.com/", "example.com", "rol-supervisor", ahora);
		Analisis resultado = new Analisis(7, 1, "completado", "ninguno", 0, List.of(), "analisis/7/contenido.txt",
				10, "analista.prueba", ahora);
		given(this.sitios.listar()).willReturn(List.of(sitio));
		given(this.sitios.crear(anyString(), anyString(), anyString(), any())).willReturn(sitio);
		given(this.clasificador.clasificar(anyString())).willReturn(ResultadoClasificador.vacio());
		given(this.servicioAnalisis.analizar(anyLong(), anyString(), anyString())).willReturn(resultado);
		given(this.analisis.buscar(anyLong())).willReturn(Optional.of(resultado));
		given(this.diagnostico.diagnosticar())
			.willReturn(new Diagnostico("ok", Map.of()));
		given(this.usuarios.buscarPorSub(anyString())).willAnswer(i -> Optional.of(cuenta(i.getArgument(0))));
		given(this.usuarios.buscarPorUsuario(anyString()))
			.willReturn(Optional.of(new Usuario("sub-otro", "ana.analista", "$2a$12$x", List.of("analista"), true, 0,
					null, ahora, null)));
		given(this.sesion.iniciar(anyString(), anyString()))
			.willReturn(new Sesion("t", "Bearer", 1800, "ana.analista", List.of("analista")));
	}

	static Usuario cuenta(String sub) {
		String nombre = sub.substring(4);
		List<String> roles = nombre.equals("sin.rol") ? List.of() : List.of(nombre.substring(0, nombre.indexOf('.')));
		return new Usuario(sub, nombre, "$2a$12$x", roles, true, 0, null, Instant.parse("2026-10-01T00:00:00Z"),
				Instant.parse("2026-10-14T12:00:00Z"));
	}

	record Ruta(HttpMethod metodo, String ruta, String cuerpo) {

		MockHttpServletRequestBuilder peticion() {
			MockHttpServletRequestBuilder p = request(this.metodo, this.ruta);
			return (this.cuerpo != null) ? p.contentType(MediaType.APPLICATION_JSON).content(this.cuerpo) : p;
		}

		@Override
		public String toString() {
			return this.metodo + " " + this.ruta;
		}

	}

	static final Ruta YO = new Ruta(HttpMethod.GET, "/api/yo", null);

	static final Ruta LISTAR_SITIOS = new Ruta(HttpMethod.GET, "/api/sitios", null);

	static final Ruta REGISTRAR_SITIO = new Ruta(HttpMethod.POST, "/api/sitios", "{\"url\":\"https://example.com/\"}");

	static final Ruta CLASIFICAR = new Ruta(HttpMethod.POST, "/api/clasificacion", "{\"texto\":\"hola\"}");

	static final Ruta LANZAR_ANALISIS = new Ruta(HttpMethod.POST, "/api/analisis", "{\"sitio_id\":1}");

	static final Ruta CONSULTAR_ANALISIS = new Ruta(HttpMethod.GET, "/api/analisis/7", null);

	static final Ruta DIAGNOSTICO = new Ruta(HttpMethod.GET, "/api/diagnostico", null);

	static final Ruta LISTAR_USUARIOS = new Ruta(HttpMethod.GET, "/api/usuarios", null);

	static final Ruta CREAR_USUARIO = new Ruta(HttpMethod.POST, "/api/usuarios",
			"{\"usuario\":\"nuevo.usuario\",\"contrasena\":\"contrasena-larga-1\",\"roles\":[\"analista\"]}");

	static final Ruta MODIFICAR_USUARIO = new Ruta(HttpMethod.POST, "/api/usuarios/ana.analista",
			"{\"activo\":false}");

	static final List<Ruta> PROTEGIDAS = List.of(YO, LISTAR_SITIOS, REGISTRAR_SITIO, CLASIFICAR, LANZAR_ANALISIS,
			CONSULTAR_ANALISIS, DIAGNOSTICO, LISTAR_USUARIOS, CREAR_USUARIO, MODIFICAR_USUARIO);

	static Stream<Arguments> matriz() {
		return Stream.of(
				Arguments.of(YO, ANALISTA, 200), Arguments.of(YO, SUPERVISOR, 200),
				Arguments.of(YO, ADMINISTRADOR, 200), Arguments.of(YO, SIN_ROL, 200),
				Arguments.of(LISTAR_SITIOS, ANALISTA, 200), Arguments.of(LISTAR_SITIOS, SUPERVISOR, 200),
				Arguments.of(LISTAR_SITIOS, ADMINISTRADOR, 403), Arguments.of(LISTAR_SITIOS, SIN_ROL, 403),
				Arguments.of(REGISTRAR_SITIO, ANALISTA, 403), Arguments.of(REGISTRAR_SITIO, SUPERVISOR, 201),
				Arguments.of(REGISTRAR_SITIO, ADMINISTRADOR, 403), Arguments.of(REGISTRAR_SITIO, SIN_ROL, 403),
				Arguments.of(CLASIFICAR, ANALISTA, 200), Arguments.of(CLASIFICAR, SUPERVISOR, 403),
				Arguments.of(CLASIFICAR, ADMINISTRADOR, 403), Arguments.of(CLASIFICAR, SIN_ROL, 403),
				Arguments.of(LANZAR_ANALISIS, ANALISTA, 201), Arguments.of(LANZAR_ANALISIS, SUPERVISOR, 403),
				Arguments.of(LANZAR_ANALISIS, ADMINISTRADOR, 403), Arguments.of(LANZAR_ANALISIS, SIN_ROL, 403),
				Arguments.of(CONSULTAR_ANALISIS, ANALISTA, 200), Arguments.of(CONSULTAR_ANALISIS, SUPERVISOR, 200),
				Arguments.of(CONSULTAR_ANALISIS, ADMINISTRADOR, 403), Arguments.of(CONSULTAR_ANALISIS, SIN_ROL, 403),
				Arguments.of(DIAGNOSTICO, ANALISTA, 403), Arguments.of(DIAGNOSTICO, SUPERVISOR, 403),
				Arguments.of(DIAGNOSTICO, ADMINISTRADOR, 200), Arguments.of(DIAGNOSTICO, SIN_ROL, 403),
				Arguments.of(LISTAR_USUARIOS, ANALISTA, 403), Arguments.of(LISTAR_USUARIOS, SUPERVISOR, 403),
				Arguments.of(LISTAR_USUARIOS, ADMINISTRADOR, 200), Arguments.of(LISTAR_USUARIOS, SIN_ROL, 403),
				Arguments.of(CREAR_USUARIO, ANALISTA, 403), Arguments.of(CREAR_USUARIO, SUPERVISOR, 403),
				Arguments.of(CREAR_USUARIO, ADMINISTRADOR, 201), Arguments.of(CREAR_USUARIO, SIN_ROL, 403),
				Arguments.of(MODIFICAR_USUARIO, ANALISTA, 403), Arguments.of(MODIFICAR_USUARIO, SUPERVISOR, 403),
				Arguments.of(MODIFICAR_USUARIO, ADMINISTRADOR, 200), Arguments.of(MODIFICAR_USUARIO, SIN_ROL, 403));
	}

	@ParameterizedTest(name = "{0} con {1} -> {2}")
	@MethodSource("matriz")
	void autorizacionPorRol(Ruta ruta, String token, int esperado) throws Exception {
		var resultado = this.mvc.perform(ruta.peticion().header("Authorization", "Bearer " + token))
			.andExpect(status().is(esperado));
		if (esperado == 403) {
			resultado.andExpect(jsonPath("$.error").value("sin_permiso"));
		}
	}

	static Stream<Ruta> protegidas() {
		return PROTEGIDAS.stream();
	}

	@ParameterizedTest(name = "{0} sin token -> 401")
	@MethodSource("protegidas")
	void sinTokenResponde401(Ruta ruta) throws Exception {
		this.mvc.perform(ruta.peticion())
			.andExpect(status().isUnauthorized())
			.andExpect(header().string("WWW-Authenticate", "Bearer"))
			.andExpect(jsonPath("$.error").value("no_autenticado"));
	}

	@ParameterizedTest(name = "{0} con token invalido -> 401")
	@MethodSource("protegidas")
	void tokenInvalidoResponde401(Ruta ruta) throws Exception {
		this.mvc.perform(ruta.peticion().header("Authorization", "Bearer eyJ.firma.alterada"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("no_autenticado"));
	}

	@Test
	void inicioDeSesionEsPublico() throws Exception {
		this.mvc
			.perform(request(HttpMethod.POST, "/api/sesion").contentType(MediaType.APPLICATION_JSON)
				.content("{\"usuario\":\"ana.analista\",\"contrasena\":\"una-contrasena-larga\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tipo").value("Bearer"))
			.andExpect(jsonPath("$.expira_en").value(1800));
	}

	@Test
	void saludEsPublica() throws Exception {
		this.mvc.perform(request(HttpMethod.GET, "/api/salud"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.estado").value("ok"))
			.andExpect(jsonPath("$.servicio").value("clamon-backend"))
			.andExpect(jsonPath("$.version").value("0.1.0"));
	}

	@Test
	void corsSoloParaElOrigenConfigurado() throws Exception {
		this.mvc.perform(request(HttpMethod.OPTIONS, "/api/sesion").header("Origin", "http://localhost:8081")
			.header("Access-Control-Request-Method", "POST")
			.header("Access-Control-Request-Headers", "content-type"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8081"));
		this.mvc.perform(request(HttpMethod.OPTIONS, "/api/sesion").header("Origin", "https://otro.example")
			.header("Access-Control-Request-Method", "POST"))
			.andExpect(status().isForbidden());
	}

	@Test
	void rutaNoDeclaradaSeNiega() throws Exception {
		this.mvc.perform(request(HttpMethod.DELETE, "/api/sitios").header("Authorization", "Bearer " + SUPERVISOR))
			.andExpect(status().isForbidden());
		this.mvc.perform(request(HttpMethod.GET, "/api/otra")).andExpect(status().isUnauthorized());
	}

	@Test
	void yoMuestraLaCuentaVigente() throws Exception {
		this.mvc.perform(request(HttpMethod.GET, "/api/yo").header("Authorization", "Bearer " + ANALISTA))
			.andExpect(jsonPath("$.usuario").value("analista.prueba"))
			.andExpect(jsonPath("$.roles.length()").value(1))
			.andExpect(jsonPath("$.roles[0]").value("analista"))
			.andExpect(jsonPath("$.ultimo_acceso").exists());
	}

	@Test
	void yoRechazaUnaCuentaDesactivada() throws Exception {
		org.mockito.Mockito.doReturn(Optional.empty()).when(this.usuarios).buscarPorSub(anyString());
		this.mvc.perform(request(HttpMethod.GET, "/api/yo").header("Authorization", "Bearer " + ANALISTA))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("no_autenticado"));
	}

}
