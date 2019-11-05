package com.axway;

import com.vordel.config.ConfigContext;
import com.vordel.config.LoadableModule;
import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityStoreDelegate;
import com.vordel.es.EntityStoreException;
import com.vordel.es.util.ShorthandKeyFinder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;


public class ExternalConfigLoader implements LoadableModule {

    private static final Logger log = LogManager.getLogger(ExternalConfigLoader.class);
    private CertHelper certHelper = new CertHelper();


    @Override
    public void load(LoadableModule arg0, String arg1) throws FatalException {
        log.info("loading Password and Certificate Environment variable Module");
    }

    @Override
    public void unload() {
        log.info("unloading Password and Certificate Environment variable Module");
    }

    @Override
    public void configure(ConfigContext configContext, Entity entity) throws EntityStoreException, FatalException {
        log.info("Loading configuration Password and Certificate Environment variable Module");
        EntityStore entityStore = configContext.getStore();
        updatePassword(entityStore);
        // entity.
    }

    public void updatePassword(EntityStore entityStore) {
        Map<String, String> envValues = System.getenv();
        Set<String> keys = envValues.keySet();
        Iterator<String> keysIterator = keys.iterator();

        while (keysIterator.hasNext()) {
            String key = keysIterator.next();
            String filterName = key.split(".")[1];
            String passwordValue = envValues.get(key);
            String shorthandKey;
            if (key.startsWith("db")) {
                shorthandKey = "/[DbConnectionGroup]name=Database Connections/[DbConnection]name=" + filterName;
                updatePasswordField(entityStore, shorthandKey, "password", passwordValue, null);
            } else if (key.startsWith("ldap")) {
                shorthandKey = "/[LdapDirectoryGroup]name=LDAP Directories/[LdapDirectory]name=" + filterName;
                updatePasswordField(entityStore, shorthandKey, "password", passwordValue, null);
            } else if (key.startsWith("smtp")) {
                shorthandKey = "/[SMTPServerGroup]name=SMTP Servers/[SMTPServer]name=" + filterName;
                updatePasswordField(entityStore, shorthandKey, "password", passwordValue, null);
            } else if (key.startsWith("httpbasic")) {
                shorthandKey = "/[AuthProfilesGroup]name=Auth Profiles/[BasicAuthGroup]name=HTTP Basic/[BasicProfile]name=" + filterName;
                updatePasswordField(entityStore, shorthandKey, "httpAuthPass", passwordValue, null);
            }

            else if (key.startsWith("radius")) {
                // [RadiusClients]name=RADIUS Client Settings/[RadiusClient]clientName=HMHSRadiusClient/[RadiusServer]host=157.154.52.85,port=1812
                shorthandKey = "[RadiusClients]name=RADIUS Client Settings/[RadiusClient]clientName=" + filterName;
                for (int i = 1; true; i++) {
                    String customHostKey = "radius." + i + "." + filterName + ".host";
                    String host = envValues.get(customHostKey);
                    if (host == null) {
                        break;
                    }
                    String customPortKey = "radius." + i + "." + filterName + ".port";
                    String port = envValues.get(customHostKey);
                    if (port == null) {
                        port = "1812";
                    }
                    String radiusShorthandKey = shorthandKey + "/[RadiusServer]host=" + host + ",port=" + port;
                    updatePasswordField(entityStore, shorthandKey, "secret", passwordValue, null);
                }

            } else if (key.startsWith("cert")) {
                //<key type='Certificates'><id field='name' value='Certificate Store'/><key type='Certificate'><id field='dname' value='CN=Change this for production'/></key></key>
                //importCertficate(filterName);
            }
        }
    }

    public void updatePasswordField(EntityStore entityStore, String shorthandKey, String fieldName, String value, Object secret) {
        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        Entity entity = shorthandKeyFinder.getEntity(shorthandKey);
        entity.setStringField(fieldName, value);
    }


    // Trust CA certs
//    private String importCertficate(String base64EncodedCert) {
//        CertificateFactory certificateFactory;
//
//        try {
//            byte encodedCert[] = Base64.getDecoder().decode(base64EncodedCert);
//            InputStream inputStream = new ByteArrayInputStream(encodedCert);
//            certificateFactory = CertificateFactory.getInstance("X.509");
//            X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(inputStream);
//            CertStore certStore = CertStore.getInstance();
//            PublicKey publicKey = certificate.getPublicKey();
//            //certificate.getP
//            final String alias = DatatypeConverter.printBase64Binary(publicKey.getEncoded());
//            if (certStore.getPersonalInfo(certificate.getSubjectDN()) == null) {
//
//
//                Thread task = new Thread(new Runnable() {
//                    public void run() {
//                        try {
//                            certStore.addEntry(certificate, null, alias);
//                        } catch (NoSuchAlgorithmException e) {
//                            e.printStackTrace();
//                        } catch (CertificateException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                });
//                task.start();
//                task.join();
//            }
//            return alias;
//        } catch (CertificateException | InterruptedException e) {
//            e.printStackTrace();
//        }
//        return null;
//
//    }


    public void addP12ToStore(EntityStore entityStore, String alias, String cert, String password) throws Exception {

        PKCS12 pkcs12 = certHelper.parseP12(cert, password);

        //         # Gets the Cert store using short hand key
        String shorthandKey = "/[Certificates]name=Certificate Store";
        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        //  entityStore.getEntity(shorthandKeyFinder)
        Entity entity = shorthandKeyFinder.getEntity(shorthandKey);
        //escape
        shorthandKey = "[Certificate]dname=" + alias;
        //See if the certificate alias already exists in the entity store,
        //if it does then update it thereby preserving any references to any HTTPS interfaces that are using this cert
        Entity certEntity = shorthandKeyFinder.getEntity(entity.getPK(), shorthandKey);
        if (certEntity != null) {
            //certEntity.setBinaryValue();
            //Updates the existing certificate in the certstore
            certEntity.setBinaryValue("content", pkcs12.getCertificate().getEncoded());
            entityStore.updateEntity(certEntity);
            String key = Base64.getEncoder().encodeToString(pkcs12.getPrivateKey().getEncoded());
            certEntity.setStringField("key", key);
        } else {
            certEntity = EntityStoreDelegate.createDefaultedEntity(entityStore, "Certificate");
            certEntity.setStringField("dname", alias);
            certEntity.setBinaryValue("content", pkcs12.getCertificate().getEncoded());
            String key = Base64.getEncoder().encodeToString(pkcs12.getPrivateKey().getEncoded());
            certEntity.setStringField("key", key);
            entityStore.addEntity(certEntity.getPK(), certEntity);
        }
    }


}
