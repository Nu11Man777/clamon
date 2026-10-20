package mx.ipn.cic.clamon.dto.request;

import java.util.List;

public record PeticionModificarUsuario(String contrasena, List<String> roles, Boolean activo) {
}
