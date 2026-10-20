package mx.ipn.cic.clamon.client.dto;

public record EstadoServicio(int codigo) {

	public boolean ok() {
		return this.codigo >= 200 && this.codigo < 300;
	}

}
