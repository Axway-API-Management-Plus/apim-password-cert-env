package com.axway;


import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class CertHelperTest {

    CertHelper certHelper = new CertHelper();

    @Test
    public void testP12() {
        try {
            PKCS12 pkcs12 = certHelper.parseP12(new File(ClassLoader.getSystemResource("certificate.p12").getFile()), "changeit".toCharArray());
           // System.out.println(pkcs12.getCertificate().getPublicKey().getFormat());
            System.out.println(pkcs12.getPrivateKey().getFormat());
        } catch (KeyStoreException | NoSuchAlgorithmException | IOException | CertificateException | UnrecoverableKeyException e) {
            Assert.fail(e.getMessage());
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

            X509Certificate certificate = certHelper.parseX509(cert).get(0);
            String name = certificate.getSubjectDN().getName();
            Assert.assertEquals("CN=nodemanager-1, OU=group-1, DC=host-1", name);
        } catch (CertificateException | FileNotFoundException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testCertChain() {

        try {
            List<X509Certificate> certificates = certHelper.parseX509("src/test/resources/certchain.pem");
            for (X509Certificate certificate: certificates) {
                Principal dn = certificate.getSubjectDN();
                System.out.println(dn);
                Principal issuer = certificate.getIssuerDN();
                System.out.println(issuer);
                if(!dn.equals(issuer)) {
                    certificate.verify(certificates.get(0).getPublicKey());
                    System.out.println("Certificate verified");
                }else {
                    Assert.fail("Invalid cert chain");
                }
            }

        } catch (CertificateException | FileNotFoundException | NoSuchAlgorithmException | SignatureException | InvalidKeyException | NoSuchProviderException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testPrivatelyPublicAndCACert() {

        try {
            List<X509Certificate> certificates = new ArrayList<>();
            X509Certificate caCert = certHelper.parseX509("src/test/resources/acp-ca.pem").get(0);
            X509Certificate cert = certHelper.parseX509("src/test/resources/acp-crt.pem").get(0);
            certificates.add(caCert);
            certificates.add(cert);


            for (X509Certificate certificate: certificates) {
                String name = certificate.getSubjectDN().getName();
                System.out.println(certificate.getSerialNumber());
            }

            PrivateKey privateKey = certHelper.parsePrivateKey("src/test/resources/acp-key.pem");
            System.out.println(privateKey);

        } catch (CertificateException | IOException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testKeyAndCert() {

        try {
            List<X509Certificate> certificates = new ArrayList<>();
            X509Certificate cert = certHelper.parseX509("src/test/resources/test_cert.pem").get(0);
            certificates.add(cert);
            for (X509Certificate certificate: certificates) {
                String name = certificate.getSubjectDN().getName();
                System.out.println("Serial number :"+ certificate.getSerialNumber());
                System.out.println("DN Name :"+ name);
            }
            PrivateKey privateKey = certHelper.parsePrivateKey("src/test/resources/test_key.pem");
            System.out.println(privateKey);

        } catch (CertificateException |  IOException e) {
            Assert.fail(e.getMessage());
        }
    }


    @Test
    public void testValidCertificateFormat(){
        String start = "-----BEGIN CERTIFICATE-----\n";
        String cert = "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\n";
        String end = "-----END CERTIFICATE-----";

      //  String cert =" MIIHDzCCBfegAwIBAgIUUI0fImVz47b5QgwoECugwkLEKiMwDQYJKoZIhvcNAQELBQAwcTELMAkGA1UEBhMCQlIxHDAaBgNVBAoTE09wZW4gQmFua2luZyBCcmFzaWwxFTATBgNVBAsTDE9wZW4gQmFua2luZzEtMCsGA1UEAxMkT3BlbiBCYW5raW5nIFNB TkRCT1ggSXNzdWluZyBDQSAtIEcxMB4XDTIxMDkwODAwMzUwMFoXDTIyMTAwODAwMzUwMFowgegxCzAJBgNVBAYTAkJSMRMwEQYDVQQKEwpJQ1AtQnJhc2lsMXEwLAYD VQQLEyVPcGVuIEJhbmtpbmcgQnJhc2lsIEluaXRpYWwgU3RydWN0dXJlMCcGA1UE CwwgPENOUEogZGEgUmVnaXN0cmF0aW9uIEF1dGhvcml0eT4wGAYDVQQLDBE8VmFs aWRhdGlvbiB0eXBlPjEbMBkGA1UEAxMSQkNPIFRSSUFOR1VMTyBTLkEuMTQwMgYK CZImiZPy LGQBARMkMzU3ZWIyM2ItZjE0ZC01MzIzLTkzNmQtZjViNDdkY2IwZWI2 MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzL6TupQlBpwIMSX7KD7S 1l9ta4S3GIeSUuT4V gJJZoX2p/D8nnoe4HgSjc+bXBhWcPjwT9ei0841Xfsqdnae EG+lkj+4N/NC9fMfmNV415ZC62BqLaAL6RqkBm9ahxEOAtWqfo1AkIE8pEyocDAa /7amtFjFz3wAqygx7xw7NfT0aK UfrL4+gCJCVUXq6WnZCDmh32j+76j17zRjGj9d JIF3ml5BdRqF95HO/R8iGO/zk1uSHCGL77ho3vHP/my8SzrHbDFuZJ/ImBc//BjL WlDvp3H94NUVBbZqERw4ZZvkXY0uQ+fyJqe mwG7CFDcpPimZWATjQxwmhprAOAvs VwIDAQABo4IDJTCCAyEwDAYDVR0TAQH/BAIwADAdBgNVHQ4EFgQU7EV6KB3BTtAL eRMyxIpNlS3PFFgwHwYDVR0jBBgwFoAUhn9YrRf1grZO tAWz+7DOEUPfTL4wTAYI KwYBBQUHAQEEQDA+MDwGCCsGAQUFBzABhjBodHRwOi8vb2NzcC5zYW5kYm94LnBr aS5vcGVuYmFua2luZ2JyYXNpbC5vcmcuYnIwSwYDVR0fBEQwQjBAo D6gPIY6aHR0 cDovL2NybC5zYW5kYm94LnBraS5vcGVuYmFua2luZ2JyYXNpbC5vcmcuYnIvaXNz dWVyLmNybDCBgAYDVR0RBHkwd6AqBgVgTAEDAqAhDB9MZW9uYXJkbyBIZW5yaX F1 ZSBMYWdlcyBQZXJlaXJhoBkGBWBMAQMDoBAMDjE3MzUxMTgwMDAwMTU5oBYGBWBM AQMEoA0MCzA4NTA4OTgwNjQ3oBYGBWBMAQMHoA0MCzEyOTUyMzUyMTAyMA4GA1Ud DwEB/w QEAwIGwDCCAaEGA1UdIASCAZgwggGUMIIBkAYKKwYBBAGDui9kATCCAYAw ggE2BggrBgEFBQcCAjCCASgMggEkVGhpcyBDZXJ0aWZpY2F0ZSBpcyBzb2xlbHkg Zm9yIHVzZSB3aXR oIFJhaWRpYW0gU2VydmljZXMgTGltaXRlZCBhbmQgb3RoZXIg cGFydGljaXBhdGluZyBvcmdhbmlzYXRpb25zIHVzaW5nIFJhaWRpYW0gU2Vydmlj ZXMgTGltaXRlZHMgVHJ1c3Qg RnJhbWV3b3JrIFNlcnZpY2VzLiBJdHMgcmVjZWlw dCwgcG9zc2Vzc2lvbiBvciB1c2UgY29uc3RpdHV0ZXMgYWNjZXB0YW5jZSBvZiB0 aGUgUmFpZGlhbSBTZXJ2aWNlcyBMdGQgQ 2VydGljaWNhdGUgUG9saWN5IGFuZCBy ZWxhdGVkIGRvY3VtZW50cyB0aGVyZWluLjBEBggrBgEFBQcCARY4aHR0cDovL2Nw cy5zYW5kYm94LnBraS5vcGVuYmFua2luZ2JyYXNpbC 5vcmcuYnIvcG9saWNpZXMw DQYJKoZIhvcNAQELBQADggEBAEnojC9wN6b4uJltOOVHVzcMdR8c64ZsDCJhUSIp jbvaf3gE+jhF2lyMJdX4KATSadmIvQO9YveO+I3eTCXF46xsoaQ UVlldfdvzgH+N nhCFMyNwl1kie1pHnZTbQRwRRfPJks7796zyyI9M7cFgvoi8GJpRFx0YP6lh+Fb5 tMeQ+vysvGsYWl8UyJD+1vZjYBB8xXIzD7eK0OyULYQe27XLgvxR3xl18BFM bEZ8 /qVyLOoYwBuznRZHPU7guWrBY4aOL00AdnNKSxK0eh7FOza5Te4bHrG7ZOKx9gsG TgPwDEYYBNgJPBQ6+8uB8EcmQF+MRy8DR3Op2x29weuYdZo= -----END CERTIFICATE";
        try {
           // cert = cert.replaceAll(" ", "");
            System.out.println(start+cert+end);
            X509Certificate certificate = certHelper.parseX509(start+cert+end).get(0);
            String name = certificate.getSubjectDN().getName();
            System.out.println(name);
        } catch (CertificateException | FileNotFoundException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testInvalidCertificateFormat(){


          String cert ="MIIHDzCCBfegAwIBAgIUUI0fImVz47b5QgwoECugwkLEKiMwDQYJKoZIhvcNAQELBQAwcTELMAkGA1UEBhMCQlIxHDAaBgNVBAoTE09wZW4gQmFua2luZyBCcmFzaWwxFTATBgNVBAsTDE9wZW4gQmFua2luZzEtMCsGA1UEAxMkT3BlbiBCYW5raW5nIFNB TkRCT1ggSXNzdWluZyBDQSAtIEcxMB4XDTIxMDkwODAwMzUwMFoXDTIyMTAwODAwMzUwMFowgegxCzAJBgNVBAYTAkJSMRMwEQYDVQQKEwpJQ1AtQnJhc2lsMXEwLAYD VQQLEyVPcGVuIEJhbmtpbmcgQnJhc2lsIEluaXRpYWwgU3RydWN0dXJlMCcGA1UE CwwgPENOUEogZGEgUmVnaXN0cmF0aW9uIEF1dGhvcml0eT4wGAYDVQQLDBE8VmFs aWRhdGlvbiB0eXBlPjEbMBkGA1UEAxMSQkNPIFRSSUFOR1VMTyBTLkEuMTQwMgYK CZImiZPy LGQBARMkMzU3ZWIyM2ItZjE0ZC01MzIzLTkzNmQtZjViNDdkY2IwZWI2 MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzL6TupQlBpwIMSX7KD7S 1l9ta4S3GIeSUuT4V gJJZoX2p/D8nnoe4HgSjc+bXBhWcPjwT9ei0841Xfsqdnae EG+lkj+4N/NC9fMfmNV415ZC62BqLaAL6RqkBm9ahxEOAtWqfo1AkIE8pEyocDAa /7amtFjFz3wAqygx7xw7NfT0aK UfrL4+gCJCVUXq6WnZCDmh32j+76j17zRjGj9d JIF3ml5BdRqF95HO/R8iGO/zk1uSHCGL77ho3vHP/my8SzrHbDFuZJ/ImBc//BjL WlDvp3H94NUVBbZqERw4ZZvkXY0uQ+fyJqe mwG7CFDcpPimZWATjQxwmhprAOAvs VwIDAQABo4IDJTCCAyEwDAYDVR0TAQH/BAIwADAdBgNVHQ4EFgQU7EV6KB3BTtAL eRMyxIpNlS3PFFgwHwYDVR0jBBgwFoAUhn9YrRf1grZO tAWz+7DOEUPfTL4wTAYI KwYBBQUHAQEEQDA+MDwGCCsGAQUFBzABhjBodHRwOi8vb2NzcC5zYW5kYm94LnBr aS5vcGVuYmFua2luZ2JyYXNpbC5vcmcuYnIwSwYDVR0fBEQwQjBAo D6gPIY6aHR0 cDovL2NybC5zYW5kYm94LnBraS5vcGVuYmFua2luZ2JyYXNpbC5vcmcuYnIvaXNz dWVyLmNybDCBgAYDVR0RBHkwd6AqBgVgTAEDAqAhDB9MZW9uYXJkbyBIZW5yaX F1 ZSBMYWdlcyBQZXJlaXJhoBkGBWBMAQMDoBAMDjE3MzUxMTgwMDAwMTU5oBYGBWBM AQMEoA0MCzA4NTA4OTgwNjQ3oBYGBWBMAQMHoA0MCzEyOTUyMzUyMTAyMA4GA1Ud DwEB/w QEAwIGwDCCAaEGA1UdIASCAZgwggGUMIIBkAYKKwYBBAGDui9kATCCAYAw ggE2BggrBgEFBQcCAjCCASgMggEkVGhpcyBDZXJ0aWZpY2F0ZSBpcyBzb2xlbHkg Zm9yIHVzZSB3aXR oIFJhaWRpYW0gU2VydmljZXMgTGltaXRlZCBhbmQgb3RoZXIg cGFydGljaXBhdGluZyBvcmdhbmlzYXRpb25zIHVzaW5nIFJhaWRpYW0gU2Vydmlj ZXMgTGltaXRlZHMgVHJ1c3Qg RnJhbWV3b3JrIFNlcnZpY2VzLiBJdHMgcmVjZWlw dCwgcG9zc2Vzc2lvbiBvciB1c2UgY29uc3RpdHV0ZXMgYWNjZXB0YW5jZSBvZiB0 aGUgUmFpZGlhbSBTZXJ2aWNlcyBMdGQgQ 2VydGljaWNhdGUgUG9saWN5IGFuZCBy ZWxhdGVkIGRvY3VtZW50cyB0aGVyZWluLjBEBggrBgEFBQcCARY4aHR0cDovL2Nw cy5zYW5kYm94LnBraS5vcGVuYmFua2luZ2JyYXNpbC 5vcmcuYnIvcG9saWNpZXMw DQYJKoZIhvcNAQELBQADggEBAEnojC9wN6b4uJltOOVHVzcMdR8c64ZsDCJhUSIp jbvaf3gE+jhF2lyMJdX4KATSadmIvQO9YveO+I3eTCXF46xsoaQ UVlldfdvzgH+N nhCFMyNwl1kie1pHnZTbQRwRRfPJks7796zyyI9M7cFgvoi8GJpRFx0YP6lh+Fb5 tMeQ+vysvGsYWl8UyJD+1vZjYBB8xXIzD7eK0OyULYQe27XLgvxR3xl18BFM bEZ8 /qVyLOoYwBuznRZHPU7guWrBY4aOL00AdnNKSxK0eh7FOza5Te4bHrG7ZOKx9gsG TgPwDEYYBNgJPBQ6+8uB8EcmQF+MRy8DR3Op2x29weuYdZo= -----END CERTIFICATE";
        try {
            X509Certificate certificate = certHelper.parseX509(cert).get(0);
            Assert.fail();
        } catch (CertificateException | FileNotFoundException e) {
            e.printStackTrace();
            Assert.assertEquals("No certificate data found", e.getMessage());
        }
    }


}
