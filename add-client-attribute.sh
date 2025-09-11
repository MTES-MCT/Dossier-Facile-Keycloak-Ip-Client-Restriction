#!/bin/bash

# Script to add an attribute to a client in a Keycloak realm
# Usage: ./add-client-attribute.sh <client_id> <realm_name> <attribute_key> <attribute_value> <admin_user> <admin_password> [keycloak_url]

# Check if required parameters are provided (only when script is executed directly)
if [ "${BASH_SOURCE[0]}" = "${0}" ] && [ $# -lt 6 ]; then
    echo "Usage: $0 <client_id> <realm_name> <attribute_key> <attribute_value> <admin_user> <admin_password> [keycloak_url]"
    echo ""
    echo "Parameters:"
    echo "  client_id       - The ID of the client to modify"
    echo "  realm_name      - The name of the Keycloak realm"
    echo "  attribute_key   - The key of the attribute to add"
    echo "  attribute_value - The value of the attribute to add"
    echo "  admin_user      - Keycloak admin username"
    echo "  admin_password  - Keycloak admin password"
    echo "  keycloak_url    - Keycloak base URL (optional, defaults to http://localhost:8085/auth)"
    echo ""
    echo "Example:"
    echo "  $0 my-client myrealm allowed-ips '192.168.1.0/24,10.0.0.0/8' admin mypassword"
    echo "  $0 my-client myrealm allowed-ips '192.168.1.0/24,10.0.0.0/8' admin mypassword http://keycloak.example.com:8080/auth"
    exit 1
fi

# Parse command line arguments (only when script is executed directly)
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
    CLIENT_ID="$1"
    REALM_NAME="$2"
    ATTRIBUTE_KEY="$3"
    ATTRIBUTE_VALUE="$4"
    ADMIN_USER="$5"
    ADMIN_PASSWORD="$6"
    KEYCLOAK_URL="${7:-http://localhost:8085/auth}"
fi

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1" >&2
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1" >&2
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1" >&2
}

# Function to check if jq is installed
check_dependencies() {
    if ! command -v jq &> /dev/null; then
        print_error "jq is required but not installed. Please install jq first."
        print_info "On macOS: brew install jq"
        print_info "On Ubuntu/Debian: sudo apt-get install jq"
        print_info "On CentOS/RHEL: sudo yum install jq"
        exit 1
    fi
}

# Function to test Keycloak connection
test_keycloak_connection() {
    print_info "Testing Keycloak connection..."
    
    # Try to access the realm info endpoint instead of health
    local realm_response=$(curl -s -w "\n%{http_code}" "${KEYCLOAK_URL}/realms/master")
    local http_code=$(echo "$realm_response" | tail -n1)
    local response_body=$(echo "$realm_response" | sed '$d')
    
    if [ "$http_code" -eq 200 ]; then
        print_info "✓ Keycloak is accessible"
    else
        print_warning "⚠ Keycloak connection test returned HTTP $http_code"
        print_info "Response: $response_body"
        print_info "Please ensure Keycloak is running and accessible at $KEYCLOAK_URL"
    fi
}

# Function to get admin access token
get_admin_token() {
    local token_response=$(curl -s -X POST \
        "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "username=${ADMIN_USER}" \
        -d "password=${ADMIN_PASSWORD}" \
        -d "grant_type=password" \
        -d "client_id=admin-cli")
    
    if [ $? -ne 0 ]; then
        print_error "Failed to connect to Keycloak. Please check the URL and ensure Keycloak is running."
        exit 1
    fi
    
    local access_token=$(echo "$token_response" | jq -r '.access_token')
    
    if [ "$access_token" = "null" ] || [ -z "$access_token" ]; then
        print_error "Failed to get access token. Please check your admin credentials."
        print_error "Response: $token_response"
        exit 1
    fi
    
    echo "$access_token"
}

# Function to get client details
get_client_details() {
    local token="$1"
    local client_id="$2"
    local realm_name="$3"
    
    # Getting client details for client ID: $client_id
    
    local client_response=$(curl -s -X GET \
        "${KEYCLOAK_URL}/admin/realms/${realm_name}/clients" \
        -H "Authorization: Bearer ${token}" \
        -H "Content-Type: application/json")
    
    local curl_exit_code=$?
    if [ $curl_exit_code -ne 0 ]; then
        print_error "Failed to get clients list (curl exit code: $curl_exit_code)"
        exit 1
    fi
    
    # Check if the response is valid JSON
    if ! echo "$client_response" | jq empty 2>/dev/null; then
        print_error "Invalid JSON response from Keycloak"
        print_error "Response: $client_response"
        exit 1
    fi
    
    # Debug: Show the response structure (commented out for production)
    # print_info "Debug: Client response structure:"
    # echo "$client_response" | jq '.[0] | keys' 2>/dev/null || echo "Could not parse response structure"
    
    # Find the client by clientId
    local client_uuid=$(echo "$client_response" | jq -r --arg clientId "$client_id" '.[] | select(.clientId == $clientId) | .id')
    
    if [ -z "$client_uuid" ] || [ "$client_uuid" = "null" ]; then
        print_error "Client with ID '$client_id' not found in realm '$realm_name'"
        print_info "Available clients:"
        echo "$client_response" | jq -r '.[].clientId' | sed 's/^/  - /'
        exit 1
    fi
    
    echo "$client_uuid"
}

# Function to get current client configuration
get_client_config() {
    local token="$1"
    local client_uuid="$2"
    local realm_name="$3"
    
    print_info "Getting current client configuration..."
    
    local client_config=$(curl -s -X GET \
        "${KEYCLOAK_URL}/admin/realms/${realm_name}/clients/${client_uuid}" \
        -H "Authorization: Bearer ${token}" \
        -H "Content-Type: application/json")
    
    if [ $? -ne 0 ]; then
        print_error "Failed to get client configuration"
        exit 1
    fi
    
    echo "$client_config"
}

# Function to update client with new attribute
update_client_attribute() {
    local token="$1"
    local client_uuid="$2"
    local realm_name="$3"
    local attribute_key="$4"
    local attribute_value="$5"
    local client_config="$6"
    
    print_info "Adding attribute '$attribute_key' with value '$attribute_value' to client..."
    
    # Update the client configuration with the new attribute
    local updated_config=$(echo "$client_config" | jq --arg key "$attribute_key" --arg value "$attribute_value" '.attributes[$key] = $value')
    
    if [ $? -ne 0 ]; then
        print_error "Failed to update client configuration JSON"
        exit 1
    fi
    
    # Update the client
    local update_response=$(curl -s -w "\n%{http_code}" -X PUT \
        "${KEYCLOAK_URL}/admin/realms/${realm_name}/clients/${client_uuid}" \
        -H "Authorization: Bearer ${token}" \
        -H "Content-Type: application/json" \
        -d "$updated_config")
    
    local curl_exit_code=$?
    if [ $curl_exit_code -ne 0 ]; then
        print_error "Failed to send update request (curl exit code: $curl_exit_code)"
        exit 1
    fi
    
    local http_code=$(echo "$update_response" | tail -n1)
    local response_body=$(echo "$update_response" | sed '$d')
    
    if [ "$http_code" -eq 204 ]; then
        print_info "Successfully added attribute '$attribute_key' to client '$CLIENT_ID'"
        print_info "Attribute value: $attribute_value"
    else
        print_error "Failed to update client. HTTP Status: $http_code"
        print_error "Response: $response_body"
        exit 1
    fi
}

# Function to verify the attribute was added
verify_attribute() {
    local token="$1"
    local client_uuid="$2"
    local realm_name="$3"
    local attribute_key="$4"
    
    print_info "Verifying attribute was added..."
    
    local client_config=$(curl -s -X GET \
        "${KEYCLOAK_URL}/admin/realms/${realm_name}/clients/${client_uuid}" \
        -H "Authorization: Bearer ${token}" \
        -H "Content-Type: application/json")
    
    local attribute_value=$(echo "$client_config" | jq -r --arg key "$attribute_key" '.attributes[$key] // "not found"')
    
    if [ "$attribute_value" != "not found" ]; then
        print_info "✓ Attribute '$attribute_key' successfully added with value: $attribute_value"
    else
        print_warning "⚠ Attribute '$attribute_key' not found in client configuration"
    fi
}

# Main execution
main() {
    print_info "Starting client attribute addition process..."
    print_info "Client ID: $CLIENT_ID"
    print_info "Realm: $REALM_NAME"
    print_info "Attribute: $ATTRIBUTE_KEY = $ATTRIBUTE_VALUE"
    print_info "Keycloak URL: $KEYCLOAK_URL"
    echo ""
    
    # Check dependencies
    check_dependencies
    
    # Test Keycloak connection
    test_keycloak_connection
    
    # Get admin token
    local admin_token
    admin_token=$(get_admin_token) || {
        print_error "Failed to get admin token"
        exit 1
    }
    
    # Get client UUID
    local client_uuid
    client_uuid=$(get_client_details "$admin_token" "$CLIENT_ID" "$REALM_NAME") || {
        print_error "Failed to get client details"
        exit 1
    }
    print_info "Found client UUID: $client_uuid"
    
    # Get current client configuration
    local client_config
    client_config=$(get_client_config "$admin_token" "$client_uuid" "$REALM_NAME") || {
        print_error "Failed to get client configuration"
        exit 1
    }
    
    # Update client with new attribute
    update_client_attribute "$admin_token" "$client_uuid" "$REALM_NAME" "$ATTRIBUTE_KEY" "$ATTRIBUTE_VALUE" "$client_config" || {
        print_error "Failed to update client attribute"
        exit 1
    }
    
    # Verify the attribute was added
    verify_attribute "$admin_token" "$client_uuid" "$REALM_NAME" "$ATTRIBUTE_KEY"
    
    print_info "Process completed successfully!"
}

# Run main function (only when script is executed directly)
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
    main "$@"
fi
