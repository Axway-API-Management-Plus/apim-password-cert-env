package com.axway;


import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class CertHelperTest {

    CertHelper certHelper = new CertHelper();

    @Test
    public void testP12() {
//        InputStream inputStream = ClassLoader.getSystemResourceAsStream("test.p12");
//        byte[] data = new byte[0];
//        try {
//            data = IOUtils.toByteArray(inputStream);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        String content = Base64.getEncoder().encodeToString(data);
//        System.out.println(content);
        try {
            PKCS12 pkcs12 = certHelper.parseP12(new File(ClassLoader.getSystemResource("certificate.p12").getFile()), "changeit".toCharArray());
           // System.out.println(pkcs12.getCertificate().getPublicKey().getFormat());
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

    @Test
    public void testX509() {
        String cert = "-----BEGIN CERTIFICATE-----\n" +
                "MIIDRjCCAi6gAwIBAgIGAW5HwjW8MA0GCSqGSIb3DQEBCwUAMBExDzANBgNVBAMM\n" +
                "BkRvbWFpbjAgFw0xOTEwMzEyMTI1NDFaGA8yMTE5MTAxNDIxMjU0MVowQjEWMBQG\n" +
                "CgmSJomT8ixkARkWBmhvc3QtMTEQMA4GA1UECwwHZ3JvdXAtMTEWMBQGA1UEAwwN\n" +
                "bm9kZW1hbmFnZXItMTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAL8V\n" +
                "Oqt5OKndTAlSHY1/LATaAdvUUPRrRvyh/BfBGWueQKoG2AQAUA5dN1B1MvPzPaaL\n" +
                "FFYgfrckdmG47MFwkpyFgchl7IVkMhvJYy0Ku+aCoT0Gou9dkEKr9A5W9ZHzuWQM\n" +
                "YRCSfIZqRednP9qFRTma185+jj7EaGiPuglkk8nplNeCbxhMBfGPewEuTBDPIOMw\n" +
                "Ep7ChaRd07/mwmfKCjwh2C910wOg1qH+MEC+yjC3BwaNINAZtHd0lzJRji8Fjtrc\n" +
                "DzTVZf0MF3E8QhW0x1kS/53BQCm6YMxjxUEgorDWrzrmyyanlICsBIASMtMWQQug\n" +
                "P6qfEvj8WLH9VcGSQlMCAwEAAaNxMG8wCQYDVR0TBAIwADALBgNVHQ8EBAMCA7gw\n" +
                "OwYDVR0lBDQwMgYIKwYBBQUHAwEGCCsGAQUFBwMCBg0rBgEEAYGMTgoBAQIBBg0r\n" +
                "BgEEAYGMTgoBAQICMBgGA1UdEQQRMA+CB2FwaS1lbnaHBAqBPDkwDQYJKoZIhvcN\n" +
                "AQELBQADggEBAFfGAtf5Rdn3EkPTsT5CcUo2+kgT3Er9y3D+SeyraM3UcwqR0+gb\n" +
                "JHeLD6xnnkxbDIEr8ZvTL5BNqZad7Iu3mS7QVK7cBi9nHmr7HSzapD6ODli8whtn\n" +
                "daElSKsO9EPAB04rVLIFZ5NIfWHLTDJSyFdvC5JFPuYxWluQwN+KOFJMjs7zVGvm\n" +
                "MXO6WwSd0Q4+NlqgnvRl6viuo14M6Qu9TsidkZhdE+AIRPveYZm9J0FzanYOAoDf\n" +
                "ZGIu5manaCW4XJKyZU/Kp04JR6ojQai65R/OLaFOxQhdZ9rtIN1DAsyTBp/6tqqC\n" +
                "s2+QnHEKNi5n6eyF81l1X3AGOMp2uUF4CfU=\n" +
                "-----END CERTIFICATE-----";

        try {

            X509Certificate certificate = certHelper.parseX509(cert);
            String name = certificate.getSubjectDN().getName();
            System.out.println(name);
        } catch (CertificateException e) {
            e.printStackTrace();
        }
    }
}
