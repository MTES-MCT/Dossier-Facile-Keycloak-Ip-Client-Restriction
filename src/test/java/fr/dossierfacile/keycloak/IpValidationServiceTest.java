package fr.dossierfacile.keycloak;

import fr.dossierfacile.keycloak.executor.IpValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for IpValidationService.
 */
@DisplayName("IP Validation Service Tests")
class IpValidationServiceTest {

    @Test
    @DisplayName("Should validate IP in single CIDR range")
    void testIpInSingleCidrRange() {
        List<String> allowedRanges = Arrays.asList("192.168.1.0/24");
        
        assertTrue(IpValidationService.isIpInRanges("192.168.1.100", allowedRanges));
        assertTrue(IpValidationService.isIpInRanges("192.168.1.1", allowedRanges));
        assertTrue(IpValidationService.isIpInRanges("192.168.1.254", allowedRanges));
        assertFalse(IpValidationService.isIpInRanges("192.168.2.100", allowedRanges));
        assertFalse(IpValidationService.isIpInRanges("10.0.0.1", allowedRanges));
    }

    @Test
    @DisplayName("Should validate IP in multiple CIDR ranges")
    void testIpInMultipleCidrRanges() {
        List<String> allowedRanges = Arrays.asList("192.168.1.0/24", "10.0.0.0/8", "203.0.113.0/24");
        
        assertTrue(IpValidationService.isIpInRanges("192.168.1.100", allowedRanges));
        assertTrue(IpValidationService.isIpInRanges("10.0.0.1", allowedRanges));
        assertTrue(IpValidationService.isIpInRanges("10.255.255.255", allowedRanges));
        assertTrue(IpValidationService.isIpInRanges("203.0.113.50", allowedRanges));
        assertFalse(IpValidationService.isIpInRanges("172.16.0.1", allowedRanges));
        assertFalse(IpValidationService.isIpInRanges("8.8.8.8", allowedRanges));
    }

    @Test
    @DisplayName("Should handle edge cases")
    void testEdgeCases() {
        List<String> allowedRanges = Arrays.asList("192.168.1.0/24");
        
        // Null or empty IP
        assertFalse(IpValidationService.isIpInRanges(null, allowedRanges));
        assertFalse(IpValidationService.isIpInRanges("", allowedRanges));
        assertFalse(IpValidationService.isIpInRanges("   ", allowedRanges));
        
        // Null or empty ranges
        assertFalse(IpValidationService.isIpInRanges("192.168.1.1", null));
        assertFalse(IpValidationService.isIpInRanges("192.168.1.1", Arrays.asList()));
        
        // Invalid CIDR in ranges
        List<String> invalidRanges = Arrays.asList("invalid-cidr", "192.168.1.0/33");
        assertFalse(IpValidationService.isIpInRanges("192.168.1.1", invalidRanges));
    }

    @Test
    @DisplayName("Should validate specific CIDR ranges")
    void testSpecificCidrRanges() {
        // /32 (single host)
        assertTrue(IpValidationService.isIpInCidr("192.168.1.1", "192.168.1.1/32"));
        assertFalse(IpValidationService.isIpInCidr("192.168.1.2", "192.168.1.1/32"));
        
        // /24 (class C)
        assertTrue(IpValidationService.isIpInCidr("192.168.1.1", "192.168.1.0/24"));
        assertTrue(IpValidationService.isIpInCidr("192.168.1.254", "192.168.1.0/24"));
        assertFalse(IpValidationService.isIpInCidr("192.168.2.1", "192.168.1.0/24"));
        
        // /16 (class B)
        assertTrue(IpValidationService.isIpInCidr("192.168.1.1", "192.168.0.0/16"));
        assertTrue(IpValidationService.isIpInCidr("192.168.255.254", "192.168.0.0/16"));
        assertFalse(IpValidationService.isIpInCidr("192.169.1.1", "192.168.0.0/16"));
        
        // /8 (class A)
        assertTrue(IpValidationService.isIpInCidr("10.0.0.1", "10.0.0.0/8"));
        assertTrue(IpValidationService.isIpInCidr("10.255.255.255", "10.0.0.0/8"));
        assertFalse(IpValidationService.isIpInCidr("11.0.0.1", "10.0.0.0/8"));
    }

    @Test
    @DisplayName("Should validate IP address format")
    void testIpAddressValidation() {
        // Valid IPv4 addresses
        assertTrue(IpValidationService.isValidIpAddress("192.168.1.1"));
        assertTrue(IpValidationService.isValidIpAddress("127.0.0.1"));
        assertTrue(IpValidationService.isValidIpAddress("0.0.0.0"));
        assertTrue(IpValidationService.isValidIpAddress("255.255.255.255"));
        
        // Invalid IPv4 addresses
        assertFalse(IpValidationService.isValidIpAddress("256.1.1.1"));
        assertFalse(IpValidationService.isValidIpAddress("192.168.1"));
        assertFalse(IpValidationService.isValidIpAddress("192.168.1.1.1"));
        assertFalse(IpValidationService.isValidIpAddress("invalid"));
        assertFalse(IpValidationService.isValidIpAddress(""));
        assertFalse(IpValidationService.isValidIpAddress(null));
        
        // Valid IPv6 addresses
        assertTrue(IpValidationService.isValidIpAddress("::1"));
        assertTrue(IpValidationService.isValidIpAddress("2001:db8::1"));
        assertTrue(IpValidationService.isValidIpAddress("fe80::1"));
    }

    @Test
    @DisplayName("Should validate CIDR notation")
    void testCidrNotationValidation() {
        // Valid CIDR notations
        assertTrue(IpValidationService.isValidCidrNotation("192.168.1.0/24"));
        assertTrue(IpValidationService.isValidCidrNotation("10.0.0.0/8"));
        assertTrue(IpValidationService.isValidCidrNotation("172.16.0.0/12"));
        assertTrue(IpValidationService.isValidCidrNotation("0.0.0.0/0"));
        assertTrue(IpValidationService.isValidCidrNotation("255.255.255.255/32"));
        
        // Invalid CIDR notations
        assertFalse(IpValidationService.isValidCidrNotation("192.168.1.0/33"));
        assertFalse(IpValidationService.isValidCidrNotation("192.168.1.0"));
        assertFalse(IpValidationService.isValidCidrNotation("192.168.1.0/"));
        assertFalse(IpValidationService.isValidCidrNotation("/24"));
        assertFalse(IpValidationService.isValidCidrNotation("256.1.1.1/24"));
        assertFalse(IpValidationService.isValidCidrNotation("invalid"));
        assertFalse(IpValidationService.isValidCidrNotation(""));
        assertFalse(IpValidationService.isValidCidrNotation(null));
    }

    @Test
    @DisplayName("Should handle real-world scenarios")
    void testRealWorldScenarios() {
        // Common private network ranges
        List<String> privateRanges = Arrays.asList("192.168.0.0/16", "10.0.0.0/8", "172.16.0.0/12");
        
        assertTrue(IpValidationService.isIpInRanges("192.168.1.100", privateRanges));
        assertTrue(IpValidationService.isIpInRanges("10.0.0.1", privateRanges));
        assertTrue(IpValidationService.isIpInRanges("172.16.0.1", privateRanges));
        assertFalse(IpValidationService.isIpInRanges("8.8.8.8", privateRanges));
        assertFalse(IpValidationService.isIpInRanges("203.0.113.1", privateRanges));
        
        // Specific client IP ranges
        List<String> clientRanges = Arrays.asList("203.0.113.0/24", "198.51.100.0/24");
        
        assertTrue(IpValidationService.isIpInRanges("203.0.113.50", clientRanges));
        assertTrue(IpValidationService.isIpInRanges("198.51.100.100", clientRanges));
        assertFalse(IpValidationService.isIpInRanges("192.168.1.1", clientRanges));
    }
}
