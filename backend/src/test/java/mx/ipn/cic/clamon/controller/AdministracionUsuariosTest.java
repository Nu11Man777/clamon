package mx.ipn.cic.clamon.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import mx.ipn.cic.clamon.TokensDePrueba;
import mx.ipn.cic.clamon.model.Usuario;
import mx.ipn.cic.clamon.repository.BitacoraRepository;
import mx.ipn.cic.clamon.repository.UsuarioRepository;
import static mx.ipn.cic.clamon.TokensDePrueba.ADMINISTRADOR;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdministracionUsuariosTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	PasswordEncoder cifrador;

	@MockitoBean
	JwtDecoder decodificador;

	@MockitoBean
	UsuarioRepository usuarios;

	@MockitoBean
	BitacoraRepository bitacora;

	static final Instant AHORA = Instant.parse("2026-10-14T12:00:00Z");

	@BeforeEach
	void preparar() {
		TokensDePrueba.configurar(this.decodificador);
	}

	ResultActions crear(String cuerpo) throws Exception {
		return this.mvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + ADMINISTRADOR)
			.contentType(MediaType.APPLICATION_JSON)
			.content(cuerpo));
	}

	ResultActions modificar(String usuario, String cuerpo) throws Exception {
		return this.mvc.perform(post("/api/usuarios/" + usuario).header("Authorization", "Bearer " + ADMINISTRADOR)
			.contentType(MediaType.APPLICATION_JSON)
			.content(cuerpo));
	}

	@Test
	void crearGuardaElHashYNuncaLoDevuelve() throws Exception {
		crear("{\"usuario\":\" Ana.Analista \",\"contrasena\":\"una-contrasena-larga\",\"roles\":[\"analista\"]}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.usuario").value("ana.analista"))
			.andExpect(jsonPath("$.roles[0]").value("analista"))
			.andExpect(jsonPath("$.activo").value(true))
			.andExpect(jsonPath("$.hash_contrasena").doesNotExist())
			.andExpect(jsonPath("$.contrasena").doesNotExist());
		ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
		then(this.usuarios).should().crear(guardado.capture());
		assertThat(this.cifrador.matches("una-contrasena-larga", guardado.getValue().hashContrasena())).isTrue();
		then(this.bitacora).should().registrarSinFallar(anyString(), eq("crear_usuario"), anyString());
	}

	@Test
	void usuarioSinRolesEsValido() throws Exception {
		crear("{\"usuario\":\"sin.rol\",\"contrasena\":\"una-contrasena-larga\",\"roles\":[]}")
			.andExpect(status().isCreated());
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{\"usuario\":\"ab\",\"contrasena\":\"una-contrasena-larga\",\"roles\":[]}",
			"{\"usuario\":\"con espacio\",\"contrasena\":\"una-contrasena-larga\",\"roles\":[]}",
			"{\"usuario\":\"ana.analista\",\"contrasena\":\"corta\",\"roles\":[]}",
			"{\"usuario\":\"ana.analista.larga\",\"contrasena\":\"ana.analista.larga\",\"roles\":[]}",
			"{\"usuario\":\"ana.analista\",\"contrasena\":\"una-contrasena-larga\",\"roles\":[\"root\"]}",
			"{\"usuario\":\"ana.analista\",\"contrasena\":\"una-contrasena-larga\"}" })
	void politicaDeCuentas(String cuerpo) throws Exception {
		crear(cuerpo).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("validacion"));
		then(this.usuarios).should(never()).crear(any());
	}

	@Test
	void usuarioDuplicadoResponde409() throws Exception {
		willThrow(new DuplicateKeyException("uq_usuario_usuario")).given(this.usuarios).crear(any());
		crear("{\"usuario\":\"ana.analista\",\"contrasena\":\"una-contrasena-larga\",\"roles\":[]}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("usuario_duplicado"));
	}

	@Test
	void restablecerContrasenaQuitaElBloqueo() throws Exception {
		given(this.usuarios.buscarPorUsuario("ana.analista")).willReturn(Optional.of(new Usuario("sub-ana",
				"ana.analista", "$2a$12$viejo", List.of("analista"), true, 0, AHORA.plusSeconds(9_999_999), AHORA, null)));
		modificar("ana.analista", "{\"contrasena\":\"contrasena-nueva-1\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.bloqueado").value(false))
			.andExpect(jsonPath("$.roles[0]").value("analista"));
		ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
		then(this.usuarios).should().actualizar(eq("sub-ana"), hash.capture(), eq(List.of("analista")), eq(true));
		assertThat(this.cifrador.matches("contrasena-nueva-1", hash.getValue())).isTrue();
	}

	@Test
	void usuarioInexistenteResponde404() throws Exception {
		given(this.usuarios.buscarPorUsuario(anyString())).willReturn(Optional.empty());
		modificar("nadie", "{\"activo\":false}").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("usuario_no_encontrado"));
	}

	@Test
	void unAdministradorNoPuedeQuitarseElAcceso() throws Exception {
		String sub = TokensDePrueba.sub(ADMINISTRADOR);
		given(this.usuarios.buscarPorUsuario("administrador.prueba")).willReturn(Optional.of(new Usuario(sub,
				"administrador.prueba", "$2a$12$x", List.of("administrador"), true, 0, null, AHORA, null)));
		modificar("administrador.prueba", "{\"activo\":false}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("operacion_no_permitida"));
		modificar("administrador.prueba", "{\"roles\":[\"analista\"]}").andExpect(status().isBadRequest());
		then(this.usuarios).should(never()).actualizar(anyString(), anyString(), anyList(), anyBoolean());
	}

	@Test
	void listarNoExponeHashes() throws Exception {
		given(this.usuarios.listar()).willReturn(List.of(new Usuario("s", "ana.analista", "$2a$12$secreto",
				List.of("analista"), true, 0, null, AHORA, AHORA)));
		this.mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + ADMINISTRADOR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].usuario").value("ana.analista"))
			.andExpect(jsonPath("$[0].hash_contrasena").doesNotExist())
			.andExpect(jsonPath("$[0].sub").doesNotExist());
	}

}
