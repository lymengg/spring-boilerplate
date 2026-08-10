package com.example.demo.security.service;

import com.example.demo.config.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class ClientIpResolver {

    private final List<IpRange> trustedProxies;

    public ClientIpResolver(SecurityProperties securityProperties) {
        this.trustedProxies = securityProperties.getRateLimiting().getTrustedProxies().stream()
                .map(ClientIpResolver::parseIpRange)
                .flatMap(List::stream)
                .toList();
    }

    public String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (trustedProxies.isEmpty() || !isTrusted(remoteAddr)) {
            return remoteAddr;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddr;
        }

        String[] ips = forwardedFor.split(",");
        for (int i = ips.length - 1; i >= 0; i--) {
            String ip = ips[i].trim();
            if (ip.isEmpty()) {
                continue;
            }
            if (isTrusted(ip)) {
                continue;
            }
            return ip;
        }

        return Arrays.stream(ips)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse(remoteAddr);
    }

    private boolean isTrusted(String ip) {
        for (IpRange range : trustedProxies) {
            if (range.contains(ip)) {
                return true;
            }
        }
        return false;
    }

    private static List<IpRange> parseIpRange(String value) {
        List<IpRange> result = new ArrayList<>();
        for (String part : value.split(",")) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }
            try {
                int slashIndex = part.indexOf('/');
                String addressPart = slashIndex >= 0 ? part.substring(0, slashIndex) : part;
                InetAddress address = InetAddress.getByName(addressPart);
                int prefixLength = slashIndex >= 0
                        ? Integer.parseInt(part.substring(slashIndex + 1))
                        : (address.getAddress().length == 4 ? 32 : 128);
                result.add(new IpRange(address, prefixLength));
            } catch (UnknownHostException | NumberFormatException e) {
                log.warn("Ignoring invalid trusted proxy value: {}", part);
            }
        }
        return result;
    }

    private static final class IpRange {
        private final byte[] network;
        private final int prefixLength;
        private final int addressLength;

        private IpRange(InetAddress address, int prefixLength) {
            this.network = address.getAddress();
            this.addressLength = network.length;
            this.prefixLength = Math.min(prefixLength, addressLength * 8);
        }

        boolean contains(String ip) {
            try {
                byte[] address = InetAddress.getByName(ip).getAddress();
                if (address.length != addressLength) {
                    return false;
                }
                int fullBytes = prefixLength / 8;
                for (int i = 0; i < fullBytes; i++) {
                    if (network[i] != address[i]) {
                        return false;
                    }
                }
                int remainingBits = prefixLength % 8;
                if (remainingBits > 0) {
                    int mask = 0xFF << (8 - remainingBits);
                    return (network[fullBytes] & mask) == (address[fullBytes] & mask);
                }
                return true;
            } catch (UnknownHostException e) {
                return false;
            }
        }
    }
}
