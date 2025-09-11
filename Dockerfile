FROM quay.io/keycloak/keycloak:26.0
ENV TZ=Europe/Paris
COPY keycloak-franceconnect-7.1.1.jar /opt/keycloak/providers/keycloak-franceconnect-7.1.1.jar
COPY Dossier-Facile-Keycloak-Ip-Client-Restriction-1.0.0.jar /opt/keycloak/providers/Dossier-Facile-Keycloak-Ip-Client-Restriction-1.0.0.jar

ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start-dev", "--log-level=DEBUG"]
