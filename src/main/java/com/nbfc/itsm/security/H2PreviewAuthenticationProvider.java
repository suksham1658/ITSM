package com.nbfc.itsm.security;

import com.nbfc.itsm.config.ItsmProperties;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.identity.PortalUserService;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Local H2 preview login only. Compares the submitted password to
 * {@code H2_PREVIEW_PASSWORD} with BCrypt. The hash is not stored on {@code employee}.
 */
@Component
@Profile("h2")
public class H2PreviewAuthenticationProvider implements AuthenticationProvider {

    private final ItsmProperties properties;
    private final EmployeeRepository employeeRepository;
    private final PortalUserService portalUserService;
    private final PasswordEncoder passwordEncoder;
    private final String previewHash;

    public H2PreviewAuthenticationProvider(ItsmProperties properties,
                                           EmployeeRepository employeeRepository,
                                           PortalUserService portalUserService,
                                           PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.employeeRepository = employeeRepository;
        this.portalUserService = portalUserService;
        this.passwordEncoder = passwordEncoder;
        String raw = properties.getSecurity().getPreviewPassword();
        this.previewHash = StringUtils.hasText(raw) ? passwordEncoder.encode(raw) : null;
    }

    @Override
    @Transactional
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!properties.getSecurity().isTestLoginEnabled() || previewHash == null) {
            return null;
        }
        String username = authentication.getName();
        String password = authentication.getCredentials() == null ? "" : authentication.getCredentials().toString();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (!passwordEncoder.matches(password, previewHash)) {
            throw new BadCredentialsException("Invalid credentials");
        }
        Employee employee = employeeRepository.findBySamAccountNameIgnoreCase(username)
                .orElseThrow(() -> new DisabledException("not-provisioned"));
        if (!employee.isPortalActive()) {
            throw new DisabledException("portal-inactive");
        }
        ItsmUserPrincipal principal = portalUserService.toPrincipal(employee);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
