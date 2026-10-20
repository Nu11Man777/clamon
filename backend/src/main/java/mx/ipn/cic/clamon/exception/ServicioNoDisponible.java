package mx.ipn.cic.clamon.exception;

public class ServicioNoDisponible extends RuntimeException {

	private final String codigo;

	public ServicioNoDisponible(String codigo, String detalle, Throwable causa) {
		super(detalle, causa);
		this.codigo = codigo;
	}

	public String codigo() {
		return this.codigo;
	}

}
