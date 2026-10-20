package mx.ipn.cic.clamon.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PeticionSitio(@NotBlank @Size(max = 2048) String url) {
}
