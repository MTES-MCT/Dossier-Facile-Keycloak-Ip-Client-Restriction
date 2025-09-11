package fr.dossierfacile.keycloak.executor;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.services.clientpolicy.executor.ClientPolicyExecutorProviderFactory;

import java.util.Collections;
import java.util.List;

public class IpWhitelistExecutorFactory implements ClientPolicyExecutorProviderFactory {

    public static final String PROVIDER_ID = "df-ip-whitelist-client";

    @Override
    public IpWhitelistExecutor create(KeycloakSession session) {
        return new IpWhitelistExecutor(session);
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getHelpText() {
        return "On authorization endpoint and token endpoint, this executor checks whether the client is confidential client. If not, it denies its request.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return Collections.emptyList();
    }
}