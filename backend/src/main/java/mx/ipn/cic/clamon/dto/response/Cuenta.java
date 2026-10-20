package mx.ipn.cic.clamon.dto.response;

import java.time.Instant;
import java.util.List;

import mx.ipn.cic.clamon.model.Usuario;

public record Cuenta(String usuario, List<String> roles, boolean activo, boolean bloqueado, Instant creadoEn,
		Instant ultimoAcceso) {

	public static Cuenta de(Usuario u, Instant ahora) {
		return new Cuenta(u.usuario(), u.roles(), u.activo(), u.bloqueado(ahora), u.creadoEn(), u.ultimoAcceso());
	}

}
