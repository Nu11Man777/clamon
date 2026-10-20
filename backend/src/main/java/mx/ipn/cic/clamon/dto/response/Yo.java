package mx.ipn.cic.clamon.dto.response;

import java.time.Instant;
import java.util.List;

public record Yo(String sub, String usuario, List<String> roles, Instant ultimoAcceso) {
}
