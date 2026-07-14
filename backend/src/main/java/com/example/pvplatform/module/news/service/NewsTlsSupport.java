package com.example.pvplatform.module.news.service;

import org.jsoup.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.net.URI;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Adds the CFCA EV root used by the selected government sources without changing
 * the JVM-wide truststore or weakening normal certificate and hostname checks.
 */
@Component
public class NewsTlsSupport {
    private static final Logger log = LoggerFactory.getLogger(NewsTlsSupport.class);
    private static final String CERTIFICATE_RESOURCE = "/certificates/cfca-ev-root.pem";
    private static final String EXPECTED_SHA256 = "5CC3D78E4E1D5E45547A04E6873E64F90CF9536D1CCC2EF800F355C4C5FD70FD";

    private final SSLSocketFactory cfcaSocketFactory;

    public NewsTlsSupport() {
        this.cfcaSocketFactory = createSocketFactory();
    }

    public Connection apply(Connection connection, String url) {
        if (requiresCfca(url) && cfcaSocketFactory != null) {
            return connection.sslSocketFactory(cfcaSocketFactory);
        }
        return connection;
    }

    private boolean requiresCfca(String url) {
        try {
            String host = URI.create(url).getHost();
            return "www.mem.gov.cn".equalsIgnoreCase(host) || "www.nea.gov.cn".equalsIgnoreCase(host);
        } catch (Exception ignored) {
            return false;
        }
    }

    private SSLSocketFactory createSocketFactory() {
        try (InputStream input = NewsTlsSupport.class.getResourceAsStream(CERTIFICATE_RESOURCE)) {
            if (input == null) throw new IllegalStateException("CFCA 根证书资源不存在");
            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
            String fingerprint = HexFormat.of().withUpperCase().formatHex(
                MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
            if (!EXPECTED_SHA256.equals(fingerprint)) {
                throw new IllegalStateException("CFCA 根证书指纹不匹配");
            }

            X509TrustManager defaultManager = trustManager(null);
            KeyStore pinnedStore = KeyStore.getInstance(KeyStore.getDefaultType());
            pinnedStore.load(null, null);
            pinnedStore.setCertificateEntry("cfca-ev-root", certificate);
            X509TrustManager pinnedManager = trustManager(pinnedStore);

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new CompositeTrustManager(defaultManager, pinnedManager)}, new SecureRandom());
            log.info("新闻采集已加载项目级 CFCA EV ROOT，sha256={}", fingerprint);
            return context.getSocketFactory();
        } catch (Exception exception) {
            log.error("新闻采集 CFCA 信任链初始化失败，将保留默认 JVM 校验：{}", exception.getMessage());
            return null;
        }
    }

    private X509TrustManager trustManager(KeyStore store) throws Exception {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        return Arrays.stream(factory.getTrustManagers())
            .filter(X509TrustManager.class::isInstance)
            .map(X509TrustManager.class::cast)
            .findFirst().orElseThrow(() -> new IllegalStateException("未找到 X509TrustManager"));
    }

    private static final class CompositeTrustManager implements X509TrustManager {
        private final X509TrustManager primary;
        private final X509TrustManager additional;

        private CompositeTrustManager(X509TrustManager primary, X509TrustManager additional) {
            this.primary = primary;
            this.additional = additional;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            primary.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                primary.checkServerTrusted(chain, authType);
            } catch (CertificateException primaryFailure) {
                try {
                    additional.checkServerTrusted(chain, authType);
                } catch (CertificateException additionalFailure) {
                    additionalFailure.addSuppressed(primaryFailure);
                    throw additionalFailure;
                }
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] first = primary.getAcceptedIssuers();
            X509Certificate[] second = additional.getAcceptedIssuers();
            X509Certificate[] combined = Arrays.copyOf(first, first.length + second.length);
            System.arraycopy(second, 0, combined, first.length, second.length);
            return combined;
        }
    }
}
