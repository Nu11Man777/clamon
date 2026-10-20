package mx.ipn.cic.clamon.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PeticionClasificacion(@NotNull @Size(min = 1, max = 20_000) String texto) {
}
