package mx.ipn.cic.clamon.config;

import javax.crypto.SecretKey;

public final class SecurityConfigAcceso {

	private SecurityConfigAcceso() {
	}

	public static SecretKey llave(String secreto) {
		return SecurityConfig.llave(secreto);
	}

}
