package com.axway;


import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

public class CertHelper {


    final JcaPEMKeyConverter jcaPEMKeyConverter = new JcaPEMKeyConverter();

    public PKCS12 parseP12(File file, char[] password) throws KeyStoreException, NoSuchAlgorithmException, IOException, CertificateException, UnrecoverableKeyException {
        InputStream io = new FileInputStream(file);
        return loadP12(io, password);
    }

    private PKCS12 loadP12(InputStream inputStream, char[] password) throws KeyStoreException, NoSuchAlgorithmException, IOException, CertificateException, UnrecoverableKeyException {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(inputStream, password);
        inputStream.close();
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) {
                //Certificate certificate = keyStore.getCertificate(alias);
                PrivateKey key = (PrivateKey) keyStore.getKey(alias, password);
                PKCS12 pkcs12 = new PKCS12();
                pkcs12.setPrivateKey(key);
                pkcs12.setAlias(alias);
                Certificate[] certificates = keyStore.getCertificateChain(alias);
                pkcs12.setCertificates(certificates);
                return pkcs12;
            }
        }
        return null;
    }


    public PKCS12 parseP12(String content, char[] password) throws KeyStoreException, NoSuchAlgorithmException, IOException, CertificateException, UnrecoverableKeyException {


        InputStream io = new ByteArrayInputStream(Base64.getDecoder().decode(content));
        return loadP12(io, password);

    }

    public PrivateKey parsePrivateKey(String base64EncodedCertOrFilePath) throws IOException, CertificateException {
        File file = new File(base64EncodedCertOrFilePath);
        Reader reader = null;
        try {
            if (file.exists()) {
                reader = new FileReader(file);
            } else {
                reader = new StringReader(base64EncodedCertOrFilePath);
            }
            PEMParser pemParser = new PEMParser(reader);
            Object pemContent = pemParser.readObject();
            if (pemContent instanceof PEMKeyPair) {
                PEMKeyPair pemKeyPair = (PEMKeyPair) pemContent;
                KeyPair keyPair = jcaPEMKeyConverter.getKeyPair(pemKeyPair);
                return keyPair.getPrivate();
            }
            return null;
        } finally {
            if (reader != null) {
                reader.close();
            }

        }

    }


    public List<X509Certificate> parseX509(String base64EncodedCertOrFilePath) throws CertificateException, FileNotFoundException {

        File file = new File(base64EncodedCertOrFilePath);
        InputStream inputStream = null;
        try {
            if (file.exists()) {
                inputStream = new FileInputStream(file);
            } else {
                inputStream = new ByteArrayInputStream(base64EncodedCertOrFilePath.getBytes());
            }
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> parsedCertificates = certificateFactory.generateCertificates(inputStream);
            List<X509Certificate> certificates = new ArrayList<>();

            for (Certificate certificate : parsedCertificates) {
                certificates.add((X509Certificate) certificate);
            }
            return certificates;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
