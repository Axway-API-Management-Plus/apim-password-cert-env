package com.axway;

import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

public class ExternalInstanceDomainCertTest {

    CertHelper certHelper = new CertHelper();
    ExternalInstanceDomainCert externalInstanceDomainCert = new ExternalInstanceDomainCert();

    @Test
    public void testCerts(){
        try {
            PKCS12 pkcs12 = certHelper.parseP12(new File(ClassLoader.getSystemResource("topology.p12").getFile()), "".toCharArray());
            File certsXml = new File("src/test/resources/");
            certsXml = new File(certsXml, "certs.xml");
            externalInstanceDomainCert.certsFile(pkcs12, certsXml);
        } catch (KeyStoreException | NoSuchAlgorithmException | IOException | CertificateException | UnrecoverableKeyException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testUpdateMgmtFile(){
        File file = new File("src/test/resources/mgmt.xml");
        try {
            externalInstanceDomainCert.updateMgmtFile(file, "cn=dss");
        } catch (ParserConfigurationException | IOException | SAXException | TransformerException e) {
            e.printStackTrace();
        }
    }
}
