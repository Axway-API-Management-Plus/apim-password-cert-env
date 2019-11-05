package com.axway;


import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Base64;

public class CertHelperTest {

    CertHelper certHelper = new CertHelper();

    @Test
    public void testP12(){
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("test.p12");
        byte[] data = new byte[0];
        try {
            data = IOUtils.toByteArray(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String content = Base64.getEncoder().encodeToString(data);
        System.out.println(content);
        try {
           PKCS12 pkcs12 = certHelper.parseP12(content, "changeit");
           System.out.println(pkcs12.getCertificate().getPublicKey().getFormat());
            System.out.println(pkcs12.getPrivateKey().getFormat());
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        }
    }
}
