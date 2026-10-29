package com.reyzerbit.suiteqlcli;

import com.reyzerbit.suiteqlcli.model.Profile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/** OAuth 1.0a (TBA) signer using HMAC-SHA256, same scheme as Noble's NetSuiteOAuthInterceptor. */
public class NetSuiteSigner
{
    private final Profile profile;

    public NetSuiteSigner(Profile profile)
    {
        this.profile = profile;
    }

    // Must be called per attempt: every request needs a fresh nonce and timestamp.
    public String authorizationHeader(String method, URI uri)
    {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString().replace("-", "");

        Map<String, String> oauth = new LinkedHashMap<>();
        oauth.put("oauth_consumer_key", profile.consumerKey());
        oauth.put("oauth_nonce", nonce);
        oauth.put("oauth_signature_method", "HMAC-SHA256");
        oauth.put("oauth_timestamp", timestamp);
        oauth.put("oauth_token", profile.tokenId());
        oauth.put("oauth_version", "1.0");

        String signature = sign(method, uri, oauth);

        // Realm goes in the header but not in the signature base string.
        return "OAuth realm=\"" + encode(profile.realm()) + "\"," +
            "oauth_consumer_key=\"" + encode(profile.consumerKey()) + "\"," +
            "oauth_nonce=\"" + encode(nonce) + "\"," +
            "oauth_signature=\"" + encode(signature) + "\"," +
            "oauth_signature_method=\"HMAC-SHA256\"," +
            "oauth_timestamp=\"" + encode(timestamp) + "\"," +
            "oauth_token=\"" + encode(profile.tokenId()) + "\"," +
            "oauth_version=\"1.0\"";
    }

    private String sign(String method, URI uri, Map<String, String> oauth)
    {
        String url = uri.toString();
        String baseUrl = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;

        Map<String, String> all = new TreeMap<>(oauth);
        String query = uri.getRawQuery();

        if (query != null)
        {
            for (String pair : query.split("&"))
            {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) all.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }

        String paramString = all.entrySet().stream()
            .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
            .collect(Collectors.joining("&"));

        String baseString = method.toUpperCase() + "&" + encode(baseUrl) + "&" + encode(paramString);
        String signingKey = encode(profile.consumerSecret()) + "&" + encode(profile.tokenSecret());

        try
        {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 signature.", e);
        }
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }
}
