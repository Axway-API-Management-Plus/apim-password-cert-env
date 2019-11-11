package com.axway;

import com.vordel.common.crypto.PasswordCipher;
import com.vordel.config.ConfigContext;
import com.vordel.config.LoadableModule;
import com.vordel.dwe.Service;
import com.vordel.es.*;
import com.vordel.es.util.ShorthandKeyFinder;
import com.vordel.store.cert.CertStore;
import com.vordel.trace.Trace;
import iaik.asn1.CodingException;
import iaik.x509.X509ExtensionInitException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.bind.DatatypeConverter;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;


public class ExternalConfigLoader implements LoadableModule {

    private static final Logger log = LogManager.getLogger(ExternalConfigLoader.class);
    private CertHelper certHelper = new CertHelper();
    private PasswordCipher passwordCipher;


    @Override
    public void load(LoadableModule arg0, String arg1) {
        log.info("loading Password and Certificate Environment variable Module");
        passwordCipher = Service.getInstance().getPasswordCipher();
    }

    @Override
    public void unload() {
        log.info("unloading Password and Certificate Environment variable Module");
    }

    @Override
    public void configure(ConfigContext configContext, Entity entity) throws EntityStoreException {
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
            if (!key.contains("_"))
                continue;
            String filterName = key.split("_")[1];
            String passwordValue = envValues.get(key);
            String shorthandKey;
            if (key.startsWith("db")) {
                log.info("Updating db password for DB connection : " + filterName);
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
            } else if (key.startsWith("radius")) {
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

                importPublicCertficate(passwordValue, entityStore);

            }

        }

        //entityStore.updateEntity();
    }

    public void updatePasswordField(EntityStore entityStore, String shorthandKey, String fieldName, String value, Object secret) {
        Trace.info("updating password");
        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        Entity entity = shorthandKeyFinder.getEntity(shorthandKey);
        if (entity == null)
            return;
        value = Base64.getEncoder().encodeToString(value.getBytes());
        //passwordCipher.encrypt()
        entity.setStringField(fieldName, value);
        entityStore.updateEntity(entity);
    }


    // Trust CA certs
    private String importPublicCertficate(String base64EncodedCert, EntityStore entityStore) {

        String shorthandKey = "/[Certificates]name=Certificate Store";
        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        //  entityStore.getEntity(shorthandKeyFinder)
        Entity entity = shorthandKeyFinder.getEntity(shorthandKey);
        try {
            Trace.info("Cert :" + base64EncodedCert);
            //byte encodedCert[] = Base64.getDecoder().decode(base64EncodedCert.getBytes("UTF-8"));
            X509Certificate certificate = certHelper.parseX509(base64EncodedCert);
            CertStore certStore = CertStore.getInstance();
           // PublicKey publicKey = certificate.getPublicKey();
            //certificate.getP
            Principal principal = certificate.getSubjectDN();
            final String alias = principal.getName();
            String escapedAlias = ShorthandKeyFinder.escapeFieldValue(alias);
            shorthandKey = "[Certificate]dname=" + escapedAlias;

            Entity certEntity = shorthandKeyFinder.getEntity(entity.getPK(), shorthandKey);

            Trace.info("Alias :" + alias);
            Trace.info("certStore"+ certStore);
            if (certEntity == null) {
                Trace.info("Adding cert");
                certEntity = EntityStoreDelegate.createDefaultedEntity(entityStore, "Certificate");
                ESPK rootPK = entityStore.getRootPK();
                EntityType group = entityStore.getTypeForName("Certificates");
                Collection<ESPK> groups = entityStore.listChildren(rootPK, group);
                certEntity.setStringField("dname", alias);
                certEntity.setBinaryValue("content", certificate.getEncoded());
                entityStore.addEntity(groups.iterator().next(), certEntity);
            }else{
                Trace.info("Updating cert with alias "+ alias);
                certEntity.setBinaryValue("content", certificate.getEncoded());
                entityStore.updateEntity(certEntity);
            }
            return alias;
        } catch (CertificateException  e) {
            e.printStackTrace();
        }
        return null;

    }


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
