package com.axway;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;

public class ExternalInstanceDomainCert {

    private static final Logger log = LogManager.getLogger(ExternalInstanceDomainCert.class);


    public final static String LINE_SEPARATOR = System.getProperty("line.separator");
    private Base64.Encoder encoder;

    public ExternalInstanceDomainCert() {
        encoder = Base64.getMimeEncoder(64, LINE_SEPARATOR.getBytes());
    }


    // File certsXml = new File(Config.getVDir("VINSTDIR"), "conf");
    // File certsXml = new File("src/");
    // certsXml = new File(certsXml, "certs.xml");


    public void updateMgmtFile(File mgmtFile, String CAAlias) throws ParserConfigurationException, IOException, SAXException, TransformerException {
        if (mgmtFile.exists()) {
            log.info("Management file mgmt.xml exists");
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(mgmtFile);
        NodeList nodeList = document.getElementsByTagName("SSLInterface");
        Element sslInterface = (Element) nodeList.item(0);
        NodeList trustedCA = sslInterface.getElementsByTagName("TrustedCA");
        if (trustedCA.getLength() == 0) {
            Element trustedCAElement = document.createElement("TrustedCA");
            trustedCAElement.setAttribute("cert", CAAlias);
            sslInterface.appendChild(trustedCAElement);
        }
        sslInterface.setAttribute("address", "*");
        NodeList verifyIsLocalNodeManager = sslInterface.getElementsByTagName("VerifyIsLocalNodeManager");
        if (verifyIsLocalNodeManager.getLength() > 0) {
            Node node = verifyIsLocalNodeManager.item(0);
            sslInterface.removeChild(node);
        }
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(document);
        FileWriter writer = new FileWriter(mgmtFile);
        StreamResult result = new StreamResult(writer);
        transformer.transform(source, result);
//        byte[] data = Files.readAllBytes(Paths.get(mgmtFile.getAbsolutePath()));
//        System.out.println(new String(data));

    }

    public String certsFile(PKCS12 pkcs12, File certsXml) throws IOException, CertificateException {

        String CAAlias = null;
        if (certsXml.exists()) {
            log.info("Management file certs.xml exists");
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("<ConfigurationFragment>");
        PrivateKey privateKey = pkcs12.getPrivateKey();

        String privateKeyEncoded = encoder.encodeToString(privateKey.getEncoded());
        Certificate[] certificates = pkcs12.getCertificates();
        for (Certificate certificate : certificates) {
            PublicKey publicKey = certificate.getPublicKey();
            byte[] data = "test".getBytes();
            byte[] digitalSignature = signData(data, privateKey);
            if (verifySignature(data, publicKey, digitalSignature)) {
                createCertificateElement(stringBuilder, pkcs12.getAlias());
                createPublicKeyElement(stringBuilder, certificate);
                stringBuilder.append("<attribute key=\"key\">");
                stringBuilder.append(privateKeyEncoded);
                stringBuilder.append("</attribute>");
            } else {
                X509Certificate cert = (X509Certificate) certificate;
                CAAlias = cert.getSubjectDN().getName();
                createCertificateElement(stringBuilder, CAAlias);
                createPublicKeyElement(stringBuilder, certificate);
            }
            endCertificateElement(stringBuilder);
        }
        stringBuilder.append("</ConfigurationFragment>");
        FileOutputStream fileOutputStream = null;

        try {
            fileOutputStream = new FileOutputStream(certsXml);
            fileOutputStream.write(stringBuilder.toString().getBytes(StandardCharsets.UTF_8));
            fileOutputStream.close();
        } finally {
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }
        }
        return CAAlias;
    }

    public void createCertificateElement(StringBuilder stringBuilder, String alias) {
        stringBuilder.append("<Certificate id=\"");
        stringBuilder.append(alias);
        stringBuilder.append("\">");
    }

    public void endCertificateElement(StringBuilder stringBuilder) {
        stringBuilder.append("</Certificate>");
    }

    public void createPublicKeyElement(StringBuilder stringBuilder, Certificate certificate) throws CertificateEncodingException {
        stringBuilder.append("<attribute key=\"content\">");
        stringBuilder.append(encoder.encodeToString(certificate.getEncoded()));
        stringBuilder.append("</attribute>");

    }


    public static byte[] signData(byte[] data, PrivateKey key) {
        Signature signer = null;
        try {
            signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(key);
            signer.update(data);
            return (signer.sign());
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean verifySignature(byte[] data, PublicKey key, byte[] sig) {
        Signature signer = null;
        try {
            signer = Signature.getInstance("SHA256withRSA");
            signer.initVerify(key);
            signer.update(data);
            return (signer.verify(sig));
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            e.printStackTrace();
            return false;
        }

    }
}
