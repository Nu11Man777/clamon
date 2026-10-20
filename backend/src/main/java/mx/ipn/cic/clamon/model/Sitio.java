package mx.ipn.cic.clamon.model;

import java.time.Instant;

public record Sitio(long id, String url, String host, String creadoPor, Instant creadoEn) {
}
