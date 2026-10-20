package mx.ipn.cic.clamon.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

public final class RespuestasSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

	private static final Logger log = LoggerFactory.getLogger(RespuestasSeguridad.class);

	@Override
	public void commence(HttpServletRequest peticion, HttpServletResponse respuesta, AuthenticationException ex)
			throws IOException {
		log.atWarn()
			.addKeyValue("evento", "auth.no_autenticado")
			.addKeyValue("metodo", peticion.getMethod())
			.addKeyValue("ruta", peticion.getRequestURI())
			.addKeyValue("motivo", ex.getClass().getSimpleName())
			.log("solicitud sin token valido");
		respuesta.setHeader("WWW-Authenticate", "Bearer");
		escribir(respuesta, HttpServletResponse.SC_UNAUTHORIZED, "no_autenticado",
				"se requiere un token de acceso valido");
	}

	@Override
	public void handle(HttpServletRequest peticion, HttpServletResponse respuesta, AccessDeniedException ex)
			throws IOException {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		log.atWarn()
			.addKeyValue("evento", "auth.sin_permiso")
			.addKeyValue("usuario", autenticacion != null ? autenticacion.getName() : null)
			.addKeyValue("roles", roles(autenticacion))
			.addKeyValue("metodo", peticion.getMethod())
			.addKeyValue("ruta", peticion.getRequestURI())
			.log("solicitud sin el rol requerido");
		escribir(respuesta, HttpServletResponse.SC_FORBIDDEN, "sin_permiso",
				"el usuario no tiene el rol requerido para esta operacion");
	}

	static List<String> roles(Authentication autenticacion) {
		if (autenticacion == null) {
			return List.of();
		}
		return autenticacion.getAuthorities()
			.stream()
			.map(a -> a.getAuthority())
			.filter(a -> a.startsWith("ROLE_"))
			.map(a -> a.substring(5))
			.filter(RolesTokenConverter.ROLES::contains)
			.sorted()
			.toList();
	}

	private static void escribir(HttpServletResponse respuesta, int estado, String codigo, String detalle)
			throws IOException {
		respuesta.setStatus(estado);
		respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
		respuesta.setCharacterEncoding("UTF-8");
		respuesta.getWriter().write("{\"error\":\"" + codigo + "\",\"detalle\":\"" + detalle + "\"}");
	}

}
