#!/bin/bash

# Script de déploiement simplifié pour l'authenticator Client IP Restriction
# Ce script compile, construit et déploie l'authenticator dans l'environnement Docker

set -e

echo "🚀 Déploiement simplifié de l'authenticator Client IP Restriction pour Keycloak"

# Vérifier que Docker est en cours d'exécution
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker n'est pas en cours d'exécution. Veuillez démarrer Docker."
    exit 1
fi

# Aller dans le répertoire de l'authenticator
cd client-ip-restriction

echo "📦 Compilation de l'authenticator..."
./build.sh

# Vérifier que le JAR a été créé
JAR_FILE="../keycloak-client-ip-restriction-1.0.0.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ Le JAR n'a pas été créé. Vérifiez les erreurs de compilation."
    exit 1
fi

echo "📋 JAR créé avec succès: $JAR_FILE"

# Arrêter et supprimer les conteneurs existants
echo "🛑 Arrêt des conteneurs existants..."
docker-compose -f ../docker-compose.dev.yml down

# Reconstruire et redémarrer avec le nouveau JAR intégré
echo "🔨 Reconstruction de l'image Docker avec le JAR intégré..."
docker-compose -f ../docker-compose.dev.yml up --build