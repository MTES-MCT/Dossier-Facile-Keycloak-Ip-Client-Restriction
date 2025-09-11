FROM quay.io/keycloak/keycloak:26.0
ENV TZ=Europe/Paris
COPY keycloak-franceconnect-7.1.1.jar /opt/keycloak/providers/keycloak-franceconnect-7.1.1.jar
COPY keycloak-client-ip-restriction-1.0.0.jar /opt/keycloak/providers/keycloak-client-ip-restriction-1.0.0.jar

ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start-dev", "--log-level=DEBUG"]
