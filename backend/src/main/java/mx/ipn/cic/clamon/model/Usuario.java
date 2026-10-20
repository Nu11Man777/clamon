package mx.ipn.cic.clamon.model;

import java.time.Instant;
import java.util.List;

public record Usuario(String sub, String usuario, String hashContrasena, List<String> roles, boolean activo,
		int intentosFallidos, Instant bloqueadoHasta, Instant creadoEn, Instant ultimoAcceso) {

	public boolean bloqueado(Instant ahora) {
		return this.bloqueadoHasta != null && this.bloqueadoHasta.isAfter(ahora);
	}

}
