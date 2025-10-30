#!/usr/bin/env bash
set -euo pipefail

# Usage: ./get-client-attributes.sh <client-id> <realm> <username> <password> <auth-url>
# Example: ./get-client-attributes.sh my-client my-realm admin 'pass' https://kc.example.com

usage() {
  echo "Usage: $0 <client-id> <realm> <username> <password> <auth-url>" >&2
  exit 1
}

command -v curl >/dev/null 2>&1 || { echo "Erreur: 'curl' est requis." >&2; exit 2; }
command -v jq   >/dev/null 2>&1 || { echo "Erreur: 'jq' est requis."   >&2; exit 2; }

if [ "$#" -ne 5 ]; then
  usage
fi

CLIENT_ID="$1"
REALM="$2"
USERNAME="$3"
PASSWORD="$4"
AUTH_URL="${5%/}"  # retire le slash final s'il existe

urlencode() {
  # URL-encode simple pour paramètres de requête
  local LC_ALL=C
  local s="$1"
  local i c out=""
  for (( i=0; i<${#s}; i++ )); do
    c=${s:$i:1}
    case "$c" in
      [a-zA-Z0-9.~_-]) out+="$c" ;;
      *) out+=$(printf '%%%02X' "'$c") ;;
    esac
  done
  printf '%s' "$out"
}

# 1) Récupérer un token d'admin (realm master, comme dans add-client-attribute.sh)
TOKEN_RESP=$(curl -sS -X POST "$AUTH_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  --data-urlencode "username=$USERNAME" \
  --data-urlencode "password=$PASSWORD" ) || { echo "Erreur: échec de l'appel token." >&2; exit 3; }

ACCESS_TOKEN=$(printf '%s' "$TOKEN_RESP" | jq -r '.access_token // empty')
if [ -z "${ACCESS_TOKEN:-}" ]; then
  echo "Erreur: impossible d'obtenir un access_token (essayé sur le realm 'master'). Réponse: $TOKEN_RESP" >&2
  exit 3
fi

# 2) Résoudre l'UUID interne du client
ENC_CLIENT_ID=$(urlencode "$CLIENT_ID")
CLIENTS_JSON=$(curl -sS -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$AUTH_URL/admin/realms/$REALM/clients?clientId=$ENC_CLIENT_ID") || { echo "Erreur: échec lookup client." >&2; exit 4; }

CLIENT_UUID=$(printf '%s' "$CLIENTS_JSON" | jq -r '.[0].id // empty')
if [ -z "${CLIENT_UUID:-}" ]; then
  echo "Erreur: client introuvable pour clientId='$CLIENT_ID' dans le realm '$REALM'." >&2
  exit 4
fi

# 3) Récupérer le client et afficher ses attributs
CLIENT_JSON=$(curl -sS -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$AUTH_URL/admin/realms/$REALM/clients/$CLIENT_UUID") || { echo "Erreur: échec récup client." >&2; exit 5; }

# Affichage: cle=valeur1,valeur2...
# Gère map<string, string|array>
ATTR_COUNT=$(printf '%s' "$CLIENT_JSON" | jq '(.attributes // {}) | length')
if [ "$ATTR_COUNT" -eq 0 ]; then
  echo "Aucun attribut pour le client '$CLIENT_ID' (realm '$REALM')."
  exit 0
fi

printf '%s' "$CLIENT_JSON" | jq -r '
  (.attributes // {}) | to_entries |
  .[] |
  if (.value|type)=="array" then
    .key + "=" + ( .value | map(tostring) | join(",") )
  else
    .key + "=" + ( .value | tostring )
  end
'
