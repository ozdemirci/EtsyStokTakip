package dev.oasis.stockify.config.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CurrentTenantIdentifierResolverImpl implements CurrentTenantIdentifierResolver {

    private static final Logger log = LoggerFactory.getLogger(CurrentTenantIdentifierResolverImpl.class);
    private static final String DEFAULT_TENANT = "public";

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenant = TenantContext.getCurrentTenant();
        String resolved = tenant != null ? tenant : DEFAULT_TENANT;
        log.debug("Resolving tenant identifier: {} (from context: {})", resolved, tenant);
        return resolved;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
