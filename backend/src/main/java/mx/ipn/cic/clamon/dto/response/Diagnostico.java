package mx.ipn.cic.clamon.dto.response;

import java.util.Map;

public record Diagnostico(String estadoGeneral, Map<String, Dependencia> dependencias) {
}
