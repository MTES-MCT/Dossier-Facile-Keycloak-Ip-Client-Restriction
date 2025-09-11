package fr.dossierfacile.keycloak.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service for validating IP addresses against CIDR ranges.
 * <p>
 * This service provides methods to check if an IP address falls within
 * one or more CIDR (Classless Inter-Domain Routing) blocks.
 */
public class IpValidationService {

    private static final Logger logger = LoggerFactory.getLogger(IpValidationService.class);

    // Pattern for validating CIDR notation (e.g., 192.168.1.0/24)
    private static final Pattern CIDR_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)/([0-9]|[1-2][0-9]|3[0-2])$"
    );

    /**
     * Check if the given IP address is within any of the specified CIDR ranges.
     *
     * @param clientIp      The IP address to validate
     * @param allowedRanges List of CIDR ranges to check against
     * @return true if the IP is within any of the allowed ranges, false otherwise
     */
    public static boolean isIpInRanges(String clientIp, List<String> allowedRanges) {
        if (clientIp == null || clientIp.trim().isEmpty()) {
            logger.warn("Client IP is null or empty");
            return false;
        }

        if (allowedRanges == null || allowedRanges.isEmpty()) {
            logger.warn("No allowed IP ranges provided");
            return false;
        }

        // Clean the client IP (remove any whitespace)
        clientIp = clientIp.trim();

        // Validate client IP format
        if (!isValidIpAddress(clientIp)) {
            logger.warn("Invalid client IP format: {}", clientIp);
            return false;
        }

        for (String range : allowedRanges) {
            if (range == null || range.trim().isEmpty()) {
                continue;
            }

            String cleanRange = range.trim();

            // Validate CIDR format
            if (!isValidCidrNotation(cleanRange)) {
                logger.warn("Invalid CIDR notation: {}", cleanRange);
                continue;
            }

            try {
                if (isIpInCidr(clientIp, cleanRange)) {
                    logger.debug("IP {} matches CIDR range {}", clientIp, cleanRange);
                    return true;
                }
            } catch (Exception e) {
                logger.error("Error checking IP {} against CIDR range {}: {}", clientIp, cleanRange, e.getMessage());
            }
        }

        logger.debug("IP {} does not match any allowed CIDR ranges", clientIp);
        return false;
    }

    /**
     * Check if an IP address is within a specific CIDR range.
     *
     * @param ip   The IP address to check
     * @param cidr The CIDR range (e.g., "192.168.1.0/24")
     * @return true if the IP is within the CIDR range
     * @throws IllegalArgumentException if the CIDR notation is invalid
     */
    public static boolean isIpInCidr(String ip, String cidr) {
        if (ip == null || cidr == null) {
            throw new IllegalArgumentException("IP and CIDR cannot be null");
        }

        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid CIDR format: " + cidr);
        }

        String networkAddress = parts[0];
        int prefixLength = Integer.parseInt(parts[1]);

        try {
            InetAddress ipAddr = InetAddress.getByName(ip);
            InetAddress networkAddr = InetAddress.getByName(networkAddress);

            // Convert to byte arrays
            byte[] ipBytes = ipAddr.getAddress();
            byte[] networkBytes = networkAddr.getAddress();

            // Check if IP and network are the same type (IPv4 or IPv6)
            if (ipBytes.length != networkBytes.length) {
                return false;
            }

            // Calculate the number of bytes to check
            int bytesToCheck = prefixLength / 8;
            int bitsToCheck = prefixLength % 8;

            // Check full bytes
            for (int i = 0; i < bytesToCheck; i++) {
                if (ipBytes[i] != networkBytes[i]) {
                    return false;
                }
            }

            // Check remaining bits
            if (bitsToCheck > 0 && bytesToCheck < ipBytes.length) {
                int mask = 0xFF << (8 - bitsToCheck);
                if ((ipBytes[bytesToCheck] & mask) != (networkBytes[bytesToCheck] & mask)) {
                    return false;
                }
            }

            return true;

        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IP address or network: " + e.getMessage());
        }
    }

    /**
     * Validate if a string is a valid IP address (IPv4 or IPv6).
     *
     * @param ip The IP address string to validate
     * @return true if the string is a valid IP address
     */
    public static boolean isValidIpAddress(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }

        String trimmedIp = ip.trim();

        try {
            InetAddress addr = InetAddress.getByName(trimmedIp);
            String resolvedAddress = addr.getHostAddress();

            // For IPv4, ensure exact match to prevent cases like "192.168.1" being resolved to "192.168.0.1"
            if (addr instanceof java.net.Inet4Address) {
                return resolvedAddress.equals(trimmedIp);
            }

            // For IPv6, check if the input is a valid IPv6 format
            // We need to handle normalization (e.g., "::1" -> "0:0:0:0:0:0:0:1")
            if (addr instanceof java.net.Inet6Address) {
                // Check if the input looks like a valid IPv6 address
                return isIPv6Format(trimmedIp);
            }

            return false;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * Check if a string looks like a valid IPv6 address format.
     *
     * @param ip The IP string to check
     * @return true if it looks like IPv6
     */
    private static boolean isIPv6Format(String ip) {
        // Basic IPv6 format check - contains colons and/or double colons
        return ip.contains(":") && !ip.contains(".");
    }

    /**
     * Validate if a string is a valid CIDR notation.
     *
     * @param cidr The CIDR string to validate
     * @return true if the string is a valid CIDR notation
     */
    public static boolean isValidCidrNotation(String cidr) {
        if (cidr == null || cidr.trim().isEmpty()) {
            return false;
        }

        return CIDR_PATTERN.matcher(cidr.trim()).matches();
    }
}
