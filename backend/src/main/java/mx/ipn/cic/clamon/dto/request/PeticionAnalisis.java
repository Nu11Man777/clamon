package mx.ipn.cic.clamon.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PeticionAnalisis(@NotNull @Positive Long sitioId) {
}
