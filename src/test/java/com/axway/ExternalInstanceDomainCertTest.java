package com.axway;

import com.vordel.common.crypto.PasswordCipher;
import com.vordel.trace.Trace;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

import static org.mockito.ArgumentMatchers.any;
import static org.powermock.api.mockito.PowerMockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ Trace.class })
@SuppressStaticInitializationFor({ "com.vordel.trace.Trace", "com.vordel.common.crypto.PasswordCipher" })
@PowerMockIgnore({"javax.management.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "org.w3c.*", "com.sun.org.apache.xalan.*"})

public class ExternalInstanceDomainCertTest {

    CertHelper certHelper = new CertHelper();
    ExternalInstanceDomainCert externalInstanceDomainCert = new ExternalInstanceDomainCert();
    PasswordCipher passwordCipher;

    @Before
    public void setup() throws GeneralSecurityException {
        mockStatic(Trace.class);
        passwordCipher = mock(PasswordCipher.class);
        when(passwordCipher.encrypt(any())).thenAnswer(i -> i.getArguments()[0]);
    }
    @Test
    public void testCerts(){
        try {
            PKCS12 pkcs12 = certHelper.parseP12(new File(ClassLoader.getSystemResource("topology.p12").getFile()), "".toCharArray());
            File certsXml = new File("src/test/resources/");
            certsXml = new File(certsXml, "certs.xml");
            String alias = externalInstanceDomainCert.certsFile(pkcs12, certsXml, passwordCipher);
           Assert.assertEquals("CA Alias name","CN=CACERTIFICATE, O=AXWAY, L=Scottsdale, ST=AZ, C=US", alias);
        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testUpdateMgmtFile(){
        File file = new File("src/test/resources/mgmt.xml");
        try {
            externalInstanceDomainCert.updateMgmtFile(file, "cn=dss");
        } catch (ParserConfigurationException | IOException | SAXException | TransformerException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
}
