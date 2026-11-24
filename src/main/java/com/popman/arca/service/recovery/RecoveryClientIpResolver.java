package com.popman.arca.service.recovery;

import com.popman.arca.config.PasswordRecoveryProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import java.net.InetAddress;
import java.util.List;

@Component
public class RecoveryClientIpResolver {
    private final List<IpAddressMatcher> proxies;
    public RecoveryClientIpResolver(PasswordRecoveryProperties settings) {
        proxies = settings.trustedProxies().stream().map(cidr -> {
            if (!cidr.matches("[0-9a-fA-F:.]+(/[0-9]{1,3})?")) {
                throw new IllegalArgumentException("Recovery trusted proxies must be explicit IP/CIDR ranges, never all addresses");
            }
            String[] parts = cidr.split("/", -1);
            String address = literal(parts[0]);
            if (parts.length == 2) {
                int prefix = Integer.parseInt(parts[1]);
                if (prefix < 1 || prefix > (address.contains(":") ? 128 : 32)) {
                    throw new IllegalArgumentException("Recovery trusted proxies require a nonzero bounded CIDR prefix");
                }
                return new IpAddressMatcher(address + "/" + prefix);
            }
            return new IpAddressMatcher(address);
        }).toList();
    }

    public String resolve(HttpServletRequest request) {
        String current = literal(request.getRemoteAddr());
        if (!trusted(current)) return current;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null) return current;
        String[] hops = forwarded.split(",", -1);
        if (forwarded.length() > 1024 || hops.length > 20) throw new IllegalArgumentException("Invalid forwarded client address");
        for (int i = hops.length - 1; i >= 0 && trusted(current); i--) current = literal(hops[i].strip());
        return current;
    }

    private boolean trusted(String ip) { return proxies.stream().anyMatch(proxy -> proxy.matches(ip)); }
    private String literal(String value) {
        try {
            if (value == null) throw new IllegalArgumentException();
            if (value.contains(":")) {
                if (!value.matches("[0-9a-fA-F:.]+")) throw new IllegalArgumentException();
                // A colon forces Java's numeric IPv6 parser, never a DNS hostname lookup.
                return InetAddress.getByName(value).getHostAddress();
            }
            if (!value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) throw new IllegalArgumentException();
            String[] octets = value.split("\\.");
            for (int i = 0; i < octets.length; i++) {
                int number = Integer.parseInt(octets[i]);
                if (number > 255) throw new IllegalArgumentException();
                octets[i] = Integer.toString(number);
            }
            return String.join(".", octets);
        } catch (Exception ex) { throw new IllegalArgumentException("Invalid client address"); }
    }
}
