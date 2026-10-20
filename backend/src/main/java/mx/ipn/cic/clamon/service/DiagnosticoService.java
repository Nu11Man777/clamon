package mx.ipn.cic.clamon.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import javax.sql.DataSource;

import mx.ipn.cic.clamon.client.AlmacenArchivos;
import mx.ipn.cic.clamon.client.ClasificadorClient;
import mx.ipn.cic.clamon.client.ScraperClient;
import mx.ipn.cic.clamon.client.dto.EstadoServicio;
import mx.ipn.cic.clamon.dto.response.Dependencia;
import mx.ipn.cic.clamon.dto.response.Diagnostico;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class DiagnosticoService {

	static final long ESPERA_MS = 3_000;

	static final long LIMITE_MS = ESPERA_MS + 1_000;

	private final DataSource dsUsuarios;

	private final DataSource dsNegocio;

	private final AlmacenArchivos archivos;

	private final ClasificadorClient clasificador;

	private final ScraperClient scraper;

	public DiagnosticoService(@Qualifier("dsUsuarios") DataSource dsUsuarios,
			@Qualifier("dsNegocio") DataSource dsNegocio, AlmacenArchivos archivos, ClasificadorClient clasificador,
			ScraperClient scraper) {
		this.dsUsuarios = dsUsuarios;
		this.dsNegocio = dsNegocio;
		this.archivos = archivos;
		this.clasificador = clasificador;
		this.scraper = scraper;
	}

	public Diagnostico diagnosticar() {
		Map<String, Supplier<String>> comprobaciones = new LinkedHashMap<>();
		comprobaciones.put("db_usuarios", () -> baseDeDatos(this.dsUsuarios));
		comprobaciones.put("db_negocio", () -> baseDeDatos(this.dsNegocio));
		comprobaciones.put("archivos", () -> {
			this.archivos.comprobar();
			return "bucket " + this.archivos.bucket();
		});
		comprobaciones.put("clasificador", () -> servicio(this.clasificador.salud()));
		comprobaciones.put("scraper", () -> servicio(this.scraper.salud()));

		Map<String, CompletableFuture<Dependencia>> futuros = new LinkedHashMap<>();
		try (ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor()) {
			comprobaciones.forEach((nombre, comprobacion) -> futuros.put(nombre,
					CompletableFuture.supplyAsync(() -> medir(comprobacion), hilos)
						.completeOnTimeout(new Dependencia("error", LIMITE_MS, "tiempo agotado"), LIMITE_MS,
								TimeUnit.MILLISECONDS)));
			Map<String, Dependencia> dependencias = new LinkedHashMap<>();
			futuros.forEach((nombre, futuro) -> dependencias.put(nombre, futuro.join()));
			hilos.shutdownNow();
			boolean ok = dependencias.values().stream().allMatch(d -> "ok".equals(d.estado()));
			return new Diagnostico(ok ? "ok" : "degradado", dependencias);
		}
	}

	static Dependencia medir(Supplier<String> comprobacion) {
		long inicio = System.nanoTime();
		try {
			String detalle = comprobacion.get();
			return new Dependencia("ok", (System.nanoTime() - inicio) / 1_000_000, detalle);
		}
		catch (RuntimeException ex) {
			return new Dependencia("error", (System.nanoTime() - inicio) / 1_000_000, describir(ex));
		}
	}

	static String describir(Throwable ex) {
		Throwable causa = ex;
		while (causa.getCause() != null && causa.getCause() != causa) {
			causa = causa.getCause();
		}
		if (causa instanceof TimeoutException || causa instanceof java.net.SocketTimeoutException) {
			return "tiempo agotado";
		}
		return causa.getClass().getSimpleName();
	}

	private static String baseDeDatos(DataSource ds) {
		try (Connection conexion = ds.getConnection(); Statement sentencia = conexion.createStatement()) {
			sentencia.setQueryTimeout((int) (ESPERA_MS / 1000));
			sentencia.execute("SELECT 1");
			try (ResultSet rs = sentencia.executeQuery("SHOW SESSION STATUS LIKE 'Ssl_version'")) {
				String version = rs.next() ? rs.getString(2) : null;
				if (version == null || version.isBlank()) {
					throw new IllegalStateException("conexion sin TLS");
				}
				return version;
			}
		}
		catch (java.sql.SQLException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String servicio(EstadoServicio estado) {
		if (!estado.ok()) {
			throw new IllegalStateException(String.valueOf(estado.codigo()));
		}
		return String.valueOf(estado.codigo());
	}

}
