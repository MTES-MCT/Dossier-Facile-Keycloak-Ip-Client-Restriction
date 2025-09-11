#!/bin/bash

# Build script for Keycloak Client IP Restriction Authenticator
# This script compiles the project and creates a JAR file ready for deployment

set -e

echo "🔨 Building Keycloak Client IP Restriction Authenticator..."

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven is not installed. Please install Maven first."
    exit 1
fi

# Version passée en argument ou récupérée depuis le pom
if [ -n "$1" ]; then
  VERSION="$1"
  echo "ℹ️ Version fournie: $VERSION"
else
  VERSION="$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"
  echo "ℹ️ Version détectée depuis le pom: $VERSION"
fi

# Clean and compile
echo "📦 Cleaning and compiling project..."
mvn clean compile

# Run tests
echo "🧪 Running tests..."
mvn test

# Package the JAR
echo "📦 Creating JAR package..."
mvn package

# Check if JAR was created
JAR_FILE="target/keycloak-client-ip-restriction-1.0.0.jar"
if [ -f "$JAR_FILE" ]; then
    echo "✅ Build successful! JAR created: $JAR_FILE"
    echo "📊 JAR size: $(du -h $JAR_FILE | cut -f1)"
    
    # Copy to parent directory for easy access
    cp "$JAR_FILE" "Dossier-Facile-Keycloak-Ip-Client-Restriction-${VERSION}.jar"
    echo "📋 JAR copied to project root as: Dossier-Facile-Keycloak-Ip-Client-Restriction-${VERSION}.jar"
    
    echo ""
    echo "🚀 Next steps:"
    echo "1. Copy the JAR to your Keycloak providers directory"
    echo "2. Restart Keycloak"
    echo "3. Configure the authenticator in your client authentication flow"
    echo "4. Add 'allowed.ip.ranges' attribute to your clients"
    
else
    echo "❌ Build failed! JAR file not found."
    exit 1
fi
