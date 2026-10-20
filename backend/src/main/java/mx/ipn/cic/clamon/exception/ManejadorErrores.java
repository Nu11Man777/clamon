package mx.ipn.cic.clamon.exception;

import java.util.stream.Collectors;

import mx.ipn.cic.clamon.dto.response.RespuestaError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import software.amazon.awssdk.core.exception.SdkException;

@RestControllerAdvice
public class ManejadorErrores {

	private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

	@ExceptionHandler(ErrorApi.class)
	ResponseEntity<RespuestaError> errorApi(ErrorApi ex) {
		return responder(ex.estado(), ex.codigo(), ex.getMessage());
	}

	@ExceptionHandler(SitioNoPermitido.class)
	ResponseEntity<RespuestaError> sitioNoPermitido(SitioNoPermitido ex) {
		return responder(HttpStatus.UNPROCESSABLE_CONTENT, "sitio_no_permitido", ex.getMessage());
	}

	@ExceptionHandler(ServicioNoDisponible.class)
	ResponseEntity<RespuestaError> servicio(ServicioNoDisponible ex) {
		return responder(HttpStatus.BAD_GATEWAY, ex.codigo(), ex.getMessage());
	}

	@ExceptionHandler(DataAccessException.class)
	ResponseEntity<RespuestaError> baseDeDatos(DataAccessException ex) {
		log.atError()
			.addKeyValue("evento", "db.error")
			.addKeyValue("tipo", ex.getClass().getSimpleName())
			.log("fallo una operacion de base de datos");
		return responder(HttpStatus.BAD_GATEWAY, "db_no_disponible", "la base de datos no respondio");
	}

	@ExceptionHandler(SdkException.class)
	ResponseEntity<RespuestaError> archivos(SdkException ex) {
		log.atError()
			.addKeyValue("evento", "archivos.error")
			.addKeyValue("tipo", ex.getClass().getSimpleName())
			.log("fallo una operacion de archivos");
		return responder(HttpStatus.BAD_GATEWAY, "archivos_no_disponible", "el almacen de archivos no respondio");
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<RespuestaError> validacion(MethodArgumentNotValidException ex) {
		String detalle = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(e -> e.getField() + ": " + e.getDefaultMessage())
			.collect(Collectors.joining("; "));
		return responder(HttpStatus.BAD_REQUEST, "validacion", detalle);
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	ResponseEntity<RespuestaError> cuerpoInvalido(Exception ex) {
		return responder(HttpStatus.BAD_REQUEST, "peticion_invalida", "la peticion no tiene el formato esperado");
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<RespuestaError> tipoNoSoportado(HttpMediaTypeNotSupportedException ex) {
		return responder(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "tipo_no_soportado", "se espera application/json");
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<RespuestaError> noEncontrado(NoResourceFoundException ex) {
		return responder(HttpStatus.NOT_FOUND, "no_encontrado", "la ruta no existe");
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<RespuestaError> metodo(HttpRequestMethodNotSupportedException ex) {
		return responder(HttpStatus.METHOD_NOT_ALLOWED, "metodo_no_permitido", "metodo no permitido");
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<RespuestaError> inesperado(Exception ex) {
		log.atError()
			.addKeyValue("evento", "api.error_interno")
			.setCause(ex)
			.log("error no controlado");
		return responder(HttpStatus.INTERNAL_SERVER_ERROR, "error_interno", "error interno");
	}

	private static ResponseEntity<RespuestaError> responder(HttpStatus estado, String codigo, String detalle) {
		return ResponseEntity.status(estado).body(new RespuestaError(codigo, detalle));
	}

}
