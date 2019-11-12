package com.axway;

import com.vordel.common.crypto.PasswordCipher;
import com.vordel.config.ConfigContext;
import com.vordel.config.LoadableModule;
import com.vordel.dwe.Service;
import com.vordel.es.*;
import com.vordel.es.util.ShorthandKeyFinder;
import com.vordel.es.xes.PortableESPK;
import com.vordel.trace.Trace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.Principal;
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

            } else if (key.startsWith("cert_")) {
                importPublicCertificate(passwordValue, entityStore);
            } else if (key.startsWith("cassandraCertDname")) {

            } else if (key.startsWith("cassandraCert")) {
                String alias = importPublicCertificate(passwordValue, entityStore);
                String escapedAlias = ShorthandKeyFinder.escapeFieldValue(alias);
                updateCassandraCert(entityStore, escapedAlias);
            } else if (key.startsWith("certandkey")) {
                try {
                    char[] password = System.getenv("certandkeypassword" + "_" + filterName).toCharArray();
                    String alias = importP12(entityStore, passwordValue, password);
                    String escapedAlias = ShorthandKeyFinder.escapeFieldValue(alias);
                    configureP12(entityStore, filterName, escapedAlias);
                } catch (Exception e) {
                    Trace.error("Unable to add the p12 from Environment variable", e);
                }
            }

        }


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

    private void updateCassandraCert(EntityStore entityStore, String escapedAlias) {
        String shorthandKey = "/[CassandraSettings]name=Cassandra Settings";
        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        //  entityStore.getEntity(shorthandKeyFinder)
        Entity entity = shorthandKeyFinder.getEntity(shorthandKey);
        boolean useSSL = entity.getBooleanValue("useSSL");
        if (useSSL) {
            //String certPlaceHolder = "<key type='Certificates'><id field='name' value='Certificate Store'/><key type='Certificate'><id field='dname' value='" + escapedAlias + "'/></key></key>";
            PortableESPK portableESPK = getCertEntity(entityStore, escapedAlias);
            entity.setReferenceField("sslTrustedCerts", portableESPK);
            entityStore.updateEntity(entity);
        }

    }

    // Trust CA certs
    private String importPublicCertificate(String base64EncodedCert, EntityStore entityStore) {

        String shorthandKey = "/[Certificates]name=Certificate Store";
        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        Entity entity = shorthandKeyFinder.getEntity(shorthandKey);
        try {
            Trace.info("Cert :" + base64EncodedCert);
            X509Certificate certificate = certHelper.parseX509(base64EncodedCert);
            Principal principal = certificate.getSubjectDN();
            final String alias = principal.getName();
            String escapedAlias = ShorthandKeyFinder.escapeFieldValue(alias);
            shorthandKey = "[Certificate]dname=" + escapedAlias;
            Entity certEntity = shorthandKeyFinder.getEntity(entity.getPK(), shorthandKey);
            Trace.info("Alias :" + alias);

            if (certEntity == null) {
                Trace.info("Adding cert");
                certEntity = EntityStoreDelegate.createDefaultedEntity(entityStore, "Certificate");
                ESPK rootPK = entityStore.getRootPK();
                EntityType group = entityStore.getTypeForName("Certificates");
                Collection<ESPK> groups = entityStore.listChildren(rootPK, group);
                certEntity.setStringField("dname", alias);
                certEntity.setBinaryValue("content", certificate.getEncoded());
                entityStore.addEntity(groups.iterator().next(), certEntity);
            } else {
                Trace.info("Updating cert with alias " + alias);
                certEntity.setBinaryValue("content", certificate.getEncoded());
                entityStore.updateEntity(certEntity);
            }
            return alias;
        } catch (CertificateException e) {
            Trace.error("Unable to add the certs from Environment variable", e);
        }
        return null;

    }

    public void configureP12(EntityStore entityStore, String name, String alias) {

        String shorthandKey = "/[NetService]name=Service/[HTTP]**/[SSLInterface]name=" + name;
        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        List<Entity> entities = shorthandKeyFinder.getEntities(shorthandKey);
        if (entities.isEmpty()) {
            Trace.error("Listener interface is not available");
            return;
        }
        Entity entity = entities.get(0);
        PortableESPK portableESPK = getCertEntity(entityStore, alias);
        //Trace.info("Portable : " + portableESPK);
        entity.setReferenceField("serverCert", portableESPK);
        entityStore.updateEntity(entity);
    }

    public PortableESPK getCertEntity(EntityStore entityStore, String alias) {
        String shorthandKey = "/[Certificates]name=Certificate Store";
        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        Entity entity = shorthandKeyFinder.getEntity(shorthandKey);
        shorthandKey = "[Certificate]dname=" + alias;
        //See if the certificate alias already exists in the entity store,
        //if it does then update it thereby preserving any references to any HTTPS interfaces that are using this cert
        Entity certEntity = shorthandKeyFinder.getEntity(entity.getPK(), shorthandKey);
        //Trace.info("PK : " + certEntity.getPK());
        return PortableESPK.toPortableKey(entityStore, certEntity.getPK());
    }


    public String importP12(EntityStore entityStore, String cert, char[] password) throws Exception {

        PKCS12 pkcs12 = certHelper.parseP12(cert, password);
        String alias = pkcs12.getAlias();
        String shorthandKey = "/[Certificates]name=Certificate Store";
        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        Entity entity = shorthandKeyFinder.getEntity(shorthandKey);
        String escapedAlias = ShorthandKeyFinder.escapeFieldValue(alias);
        shorthandKey = "[Certificate]dname=" + escapedAlias;
        //See if the certificate alias already exists in the entity store,
        //if it does then update it thereby preserving any references to any HTTPS interfaces that are using this cert
        Entity certEntity = shorthandKeyFinder.getEntity(entity.getPK(), shorthandKey);
        if (certEntity != null) {
            //certEntity.setBinaryValue();
            //Updates the existing certificate in the certstore
            certEntity.setBinaryValue("content", pkcs12.getCertificate().getEncoded());
            String key = Base64.getEncoder().encodeToString(pkcs12.getPrivateKey().getEncoded());
            certEntity.setStringField("key", key);
            entityStore.updateEntity(certEntity);
        } else {
            ESPK rootPK = entityStore.getRootPK();
            EntityType group = entityStore.getTypeForName("Certificates");
            Collection<ESPK> groups = entityStore.listChildren(rootPK, group);
            certEntity = EntityStoreDelegate.createDefaultedEntity(entityStore, "Certificate");
            certEntity.setStringField("dname", alias);
            certEntity.setBinaryValue("content", pkcs12.getCertificate().getEncoded());
            String key = Base64.getEncoder().encodeToString(pkcs12.getPrivateKey().getEncoded());
            certEntity.setStringField("key", key);
            entityStore.addEntity(groups.iterator().next(), certEntity);
        }

        return alias;

    }


}
