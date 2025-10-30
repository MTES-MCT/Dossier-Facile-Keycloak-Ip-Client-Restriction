package fr.dossierfacile.keycloak.executor;

import jakarta.ws.rs.core.Response;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.representations.idm.ClientPolicyExecutorConfigurationRepresentation;
import org.keycloak.services.clientpolicy.ClientPolicyContext;
import org.keycloak.services.clientpolicy.ClientPolicyException;
import org.keycloak.services.clientpolicy.context.ServiceAccountTokenRequestContext;
import org.keycloak.services.clientpolicy.context.TokenRequestContext;
import org.keycloak.services.clientpolicy.executor.ClientPolicyExecutorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class IpWhitelistExecutor implements ClientPolicyExecutorProvider<ClientPolicyExecutorConfigurationRepresentation> {
    private static final Logger logger = LoggerFactory.getLogger(IpWhitelistExecutor.class);
    private final KeycloakSession session;

    private static final String ALLOWED_IP_RANGES_ATTR = "allowed.ip.ranges";

    public IpWhitelistExecutor(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public void executeOnEvent(ClientPolicyContext context) throws ClientPolicyException {

        logger.info("IpWhitelistExecutor called");

        boolean isTokenEndpointContext =
                context instanceof TokenRequestContext
                || context instanceof ServiceAccountTokenRequestContext;

        // Ne traiter que les jetons service account (client_credentials)
        if (! isTokenEndpointContext) {
            logger.info("Ignoring non-token endpoint context: {}", context.getClass().getSimpleName());
            return;
        }

        KeycloakContext kc = session.getContext();
        ClientModel client = kc.getClient();
        if (client == null) return;

        String remoteIp = getClientIpAddress(kc);

        if (remoteIp == null || remoteIp.isEmpty()) {
            logger.error("Could not determine client IP address");
            throw new ClientPolicyException("Access denied from IpWhitelistExecutor: Unable to determine client IP");
        }

        String allowedIpRangesStr = client.getAttribute(ALLOWED_IP_RANGES_ATTR);
        logger.debug("Allowed IP ranges: {}", allowedIpRangesStr);

        if (allowedIpRangesStr == null || allowedIpRangesStr.isEmpty()) {
            logger.error("No allowed IP ranges configured for client ID: {}", client.getClientId());
            throw new ClientPolicyException("Access denied from IpWhitelistExecutor: No allowed IP ranges configured");
        }

        logger.debug("Client ID: {}, IP: {}", client.getClientId(), remoteIp);
        List<String> allowedIpRanges = Arrays.asList(allowedIpRangesStr.split(","));

        var isIpValid = IpValidationService.isIpInRanges(remoteIp, allowedIpRanges);

        if (isIpValid) {
            logger.info("Access granted from IpWhitelistExecutor for IP: {}", remoteIp);
        } else {
            logger.error("Access denied from IpWhitelistExecutor for IP: {}", remoteIp);
            throw new ClientPolicyException("Access denied from IpWhitelistExecutor", "invalid_client", Response.Status.FORBIDDEN);
        }

    }

    @Override
    public String getProviderId() {
        return IpWhitelistExecutorFactory.PROVIDER_ID;
    }

    /**
     * Extract client IP address from the authentication context.
     *
     * @param context The client authentication context
     * @return The client IP address or null if not found
     */
    private String getClientIpAddress(KeycloakContext context) {
        try {
            // Try to get IP from X-Forwarded-For header first (for load balancers/proxies)
            String forwardedFor = context.getHttpRequest().getHttpHeaders().getHeaderString("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.trim().isEmpty()) {
                // X-Forwarded-For can contain multiple IPs, take the first one
                String clientIp = forwardedFor.split(",")[0].trim();
                logger.debug("Client IP from X-Forwarded-For: {}", clientIp);
                return clientIp;
            }

            // Try X-Real-IP header (alternative proxy header)
            String realIp = context.getHttpRequest().getHttpHeaders().getHeaderString("X-Real-IP");
            if (realIp != null && !realIp.trim().isEmpty()) {
                logger.debug("Client IP from X-Real-IP: {}", realIp);
                return realIp.trim();
            }

            // Fall back to remote address
            String remoteAddr = context.getConnection().getRemoteAddr();
            if (remoteAddr != null && !remoteAddr.trim().isEmpty()) {
                logger.debug("Client IP from remote address: {}", remoteAddr);
                return remoteAddr;
            }

            logger.warn("Could not determine client IP address from any source");
            return null;

        } catch (Exception e) {
            logger.error("Error extracting client IP address", e);
            return null;
        }
    }

}

