package mx.ipn.cic.clamon.dto.response;

import java.util.List;

public record Sesion(String token, String tipo, long expiraEn, String usuario, List<String> roles) {
}
