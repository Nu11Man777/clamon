package mx.ipn.cic.clamon.exception;

import org.springframework.http.HttpStatus;

public class ErrorApi extends RuntimeException {

	private final HttpStatus estado;

	private final String codigo;

	public ErrorApi(HttpStatus estado, String codigo, String detalle) {
		super(detalle);
		this.estado = estado;
		this.codigo = codigo;
	}

	public HttpStatus estado() {
		return this.estado;
	}

	public String codigo() {
		return this.codigo;
	}

}
