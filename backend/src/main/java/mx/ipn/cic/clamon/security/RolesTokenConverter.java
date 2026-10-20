package mx.ipn.cic.clamon.security;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class RolesTokenConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

	public static final Set<String> ROLES = Set.of("analista", "supervisor", "coordinador", "administrador",
			"responsable");

	@Override
	public Collection<GrantedAuthority> convert(Jwt jwt) {
		return roles(jwt).stream().map(rol -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol)).toList();
	}

	public static List<String> roles(Jwt jwt) {
		Object roles = jwt.getClaims().get("roles");
		if (!(roles instanceof Collection<?> lista)) {
			return List.of();
		}
		return lista.stream()
			.filter(String.class::isInstance)
			.map(String.class::cast)
			.filter(ROLES::contains)
			.sorted()
			.toList();
	}

}
