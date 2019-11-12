package com.axway;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Enumeration;

public class CertHelper {

    public PKCS12 parseP12(String content, char[] password) throws KeyStoreException, NoSuchAlgorithmException, IOException, CertificateException, UnrecoverableKeyException {

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        InputStream io = new ByteArrayInputStream(Base64.getDecoder().decode(content));
        keyStore.load(io, password);
        io.close();
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) {
                Certificate certificate = keyStore.getCertificate(alias);
                PrivateKey key = (PrivateKey) keyStore.getKey(alias, password);
                PKCS12 pkcs12 = new PKCS12();
                pkcs12.setCertificate(certificate);
                pkcs12.setPrivateKey(key);
                pkcs12.setAlias(alias);
                return pkcs12;
            }
        }
        return null;
    }

    public X509Certificate parseX509(String base64EncodedCert) throws CertificateException {
        InputStream inputStream = new ByteArrayInputStream(base64EncodedCert.getBytes());
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(inputStream);
        return certificate;
    }

}
