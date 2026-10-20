package mx.ipn.cic.clamon.client;

import java.io.IOException;
import java.util.List;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class TokenIdentidadInterceptor implements ClientHttpRequestInterceptor {

	private final String audiencia;

	private final boolean habilitado;

	private volatile IdTokenCredentials credenciales;

	public TokenIdentidadInterceptor(String audiencia, boolean habilitado) {
		this.audiencia = audiencia;
		this.habilitado = habilitado;
	}

	@Override
	public ClientHttpResponse intercept(HttpRequest peticion, byte[] cuerpo, ClientHttpRequestExecution ejecucion)
			throws IOException {
		if (this.habilitado) {
			peticion.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + token());
		}
		return ejecucion.execute(peticion, cuerpo);
	}

	String token() throws IOException {
		IdTokenCredentials actuales = this.credenciales;
		if (actuales == null) {
			synchronized (this) {
				actuales = this.credenciales;
				if (actuales == null) {
					GoogleCredentials base = GoogleCredentials.getApplicationDefault();
					if (!(base instanceof IdTokenProvider proveedor)) {
						throw new IOException("las credenciales de la aplicacion no emiten tokens de identidad");
					}
					actuales = IdTokenCredentials.newBuilder()
						.setIdTokenProvider(proveedor)
						.setTargetAudience(this.audiencia)
						.setOptions(List.of(IdTokenProvider.Option.FORMAT_FULL))
						.build();
					this.credenciales = actuales;
				}
			}
		}
		actuales.refreshIfExpired();
		return actuales.getIdToken().getTokenValue();
	}

}
