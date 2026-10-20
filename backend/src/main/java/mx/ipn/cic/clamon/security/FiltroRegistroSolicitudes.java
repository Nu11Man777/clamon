package mx.ipn.cic.clamon.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class FiltroRegistroSolicitudes extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(FiltroRegistroSolicitudes.class);

	@Override
	protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
			throws ServletException, IOException {
		long inicio = System.nanoTime();
		try {
			cadena.doFilter(peticion, respuesta);
		}
		finally {
			Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
			boolean autenticado = autenticacion != null && autenticacion.isAuthenticated()
					&& !"anonymousUser".equals(autenticacion.getName());
			log.atInfo()
				.addKeyValue("evento", "api.solicitud")
				.addKeyValue("metodo", peticion.getMethod())
				.addKeyValue("ruta", peticion.getRequestURI())
				.addKeyValue("codigo", respuesta.getStatus())
				.addKeyValue("duracion_ms", (System.nanoTime() - inicio) / 1_000_000)
				.addKeyValue("usuario", autenticado ? autenticacion.getName() : null)
				.addKeyValue("roles", autenticado ? RespuestasSeguridad.roles(autenticacion) : null)
				.log("solicitud de la API");
		}
	}

}
