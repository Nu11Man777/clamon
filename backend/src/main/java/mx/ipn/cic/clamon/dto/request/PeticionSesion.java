package mx.ipn.cic.clamon.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PeticionSesion(@NotNull @Size(min = 1, max = 64) String usuario,
		@NotNull @Size(min = 1, max = 128) String contrasena) {
}
