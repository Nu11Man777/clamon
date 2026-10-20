package mx.ipn.cic.clamon.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PeticionCrearUsuario(@NotBlank String usuario, @NotBlank String contrasena, @NotNull List<String> roles) {
}
