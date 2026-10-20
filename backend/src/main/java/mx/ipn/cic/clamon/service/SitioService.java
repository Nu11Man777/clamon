package mx.ipn.cic.clamon.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import mx.ipn.cic.clamon.exception.ErrorApi;
import mx.ipn.cic.clamon.model.Sitio;
import mx.ipn.cic.clamon.repository.BitacoraRepository;
import mx.ipn.cic.clamon.repository.SitioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SitioService {

	private static final Logger log = LoggerFactory.getLogger(SitioService.class);

	private final SitioRepository sitios;

	private final BitacoraRepository bitacora;

	public SitioService(SitioRepository sitios, BitacoraRepository bitacora) {
		this.sitios = sitios;
		this.bitacora = bitacora;
	}

	public List<Sitio> listar() {
		return this.sitios.listar();
	}

	public Sitio registrar(String urlRecibida, String sub, String usuario) {
		String url = urlRecibida.strip();
		String host = hostValido(url);
		Sitio sitio;
		try {
			sitio = this.sitios.crear(url, host, usuario, Instant.now().truncatedTo(ChronoUnit.MILLIS));
		}
		catch (DuplicateKeyException ex) {
			throw new ErrorApi(HttpStatus.CONFLICT, "sitio_duplicado", "el sitio ya esta registrado");
		}
		this.bitacora.registrarSinFallar(sub, "registrar_sitio", "sitio " + sitio.id() + ": " + host);
		log.atInfo()
			.addKeyValue("evento", "sitio.registrado")
			.addKeyValue("usuario", usuario)
			.addKeyValue("sitio_id", sitio.id())
			.addKeyValue("host", host)
			.log("sitio registrado");
		return sitio;
	}

	static String hostValido(String url) {
		URI uri;
		try {
			uri = new URI(url);
		}
		catch (URISyntaxException ex) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "url_invalida", "la URL no es valida");
		}
		String esquema = (uri.getScheme() != null) ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
		if (!esquema.equals("http") && !esquema.equals("https")) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "url_invalida", "el esquema debe ser http o https");
		}
		if (uri.getHost() == null || uri.getHost().isBlank()) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "url_invalida", "la URL no tiene nombre de servidor");
		}
		if (uri.getRawUserInfo() != null) {
			throw new ErrorApi(HttpStatus.BAD_REQUEST, "url_invalida", "la URL no puede incluir credenciales");
		}
		return uri.getHost().toLowerCase(Locale.ROOT);
	}

}
