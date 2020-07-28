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

import java.io.File;
import java.security.Principal;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;


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

    private void updatePassword(EntityStore entityStore) {
        Map<String, String> envValues = System.getenv();
        Set<String> keys = envValues.keySet();
        Iterator<String> keysIterator = keys.iterator();

        Map<String, String> ldap = envValues.entrySet()
                .stream()
                .filter(map -> map.getKey().startsWith("ldap_"))
                .collect(Collectors.toMap(map -> map.getKey(), map -> map.getValue()));

        Map<String, String> jms = envValues.entrySet()
                .stream()
                .filter(map -> map.getKey().startsWith("jms_"))
                .collect(Collectors.toMap(map -> map.getKey(), map -> map.getValue()));

        Map<String, String> smtp = envValues.entrySet()
                .stream()
                .filter(map -> map.getKey().startsWith("smtp_"))
                .collect(Collectors.toMap(map -> map.getKey(), map -> map.getValue()));


        while (keysIterator.hasNext()) {
            String key = keysIterator.next();
            if (!key.contains("_"))
                continue;
            String filterName = key.split("_")[1];
            String passwordValue = envValues.get(key);
            String shorthandKey;
            if (key.startsWith("httpbasic")) {
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
                    String port = envValues.get(customPortKey);
                    if (port == null) {
                        port = "1812";
                    }
                    String radiusShorthandKey = shorthandKey + "/[RadiusServer]host=" + host + ",port=" + port;
                    updatePasswordField(entityStore, radiusShorthandKey, "secret", passwordValue, null);
                }

            } else if (key.startsWith("cert_")) {
                importPublicCertificate(passwordValue, entityStore);
            } else if (key.startsWith("disablehttps_")) {
                if(passwordValue.equalsIgnoreCase("true")){
                    disableInterface(entityStore, filterName, "SSLInterface");
                }
            } else if (key.startsWith("disablehttp_")) {
                if(passwordValue.equalsIgnoreCase("true")){
                    disableInterface(entityStore, filterName, "InetInterface");
                }
            } else if (key.equalsIgnoreCase("cassandra_disablessl")) {
                if(passwordValue.equalsIgnoreCase("true")){
                    disableCassandraSSL(entityStore);
                }
            } else if (key.startsWith("cassandraCert")) {
                String alias = importPublicCertificate(passwordValue, entityStore);
                String escapedAlias = ShorthandKeyFinder.escapeFieldValue(alias);
                updateCassandraCert(entityStore, escapedAlias);
            } else if (key.startsWith("certandkey_")) {
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

        List<Credential> credentials = parseCred(ldap, "ldap");
        if (!credentials.isEmpty()) {
            for (Credential credential : credentials) {
                updateLdap(entityStore, credential);
            }
        }

        credentials = parseCred(jms, "jms");
        if (!credentials.isEmpty()) {
            for (Credential credential : credentials) {
                updateJMS(entityStore, credential);
            }
        }

        credentials = parseCred(smtp, "smtp");
        if (!credentials.isEmpty()) {
            for (Credential credential : credentials) {
                updateSMTP(entityStore, credential);
                updateAlertSMTP(entityStore, credential);

            }
        }
    }

    private List<Credential> parseCred(Map<String, String> envMap, String connectorName) {

        List<Credential> credentials = new ArrayList<>();
        if (envMap != null && !envMap.isEmpty()) {
            Iterator<String> keyIterator = envMap.keySet().iterator();
            while (keyIterator.hasNext()) {
                String key = keyIterator.next();
                String[] delimitedKeys = key.split("_");
                String filterName;
                if (delimitedKeys.length == 3) {
                    filterName = delimitedKeys[1];
                } else {
                    envMap.remove(key);
                    keyIterator = envMap.keySet().iterator();
                    continue;
                }
                String prefix = connectorName + "_" + filterName + "_";
                String userNameVar = prefix + "username";
                String passwordVar = prefix + "password";
                String urlVar = prefix + "url";
                String username = envMap.get(userNameVar);
                String password = envMap.get(passwordVar);
                String url = envMap.get(urlVar);

                envMap.remove(userNameVar);
                envMap.remove(passwordVar);
                envMap.remove(urlVar);
                Credential credential = new Credential();
                credential.setUrl(url);
                credential.setPassword(password);
                credential.setUsername(username);
                credential.setFilterName(filterName);
                credentials.add(credential);
                keyIterator = envMap.keySet().iterator();
            }
        }
        return credentials;
    }

    private void updatePasswordField(EntityStore entityStore, String shorthandKey, String fieldName, String
            value, Object secret) {
        Trace.info("updating password");
        Entity entity = getEntity(entityStore, shorthandKey);
        if (entity == null)
            return;
        value = Base64.getEncoder().encodeToString(value.getBytes());
        //passwordCipher.encrypt()
        entity.setStringField(fieldName, value);
        entityStore.updateEntity(entity);
    }

    private Entity getEntity(EntityStore entityStore, String shorthandKey) {
        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        return shorthandKeyFinder.getEntity(shorthandKey);
    }

    private void setUsernameAndPassword(Credential credential, Entity entity, String usernameFieldName) {
        String password = credential.getPassword();
        if (password != null) {
            password = Base64.getEncoder().encodeToString(password.getBytes());
            //passwordCipher.encrypt()
            entity.setStringField("password", password);
        }
        String username = credential.getUsername();
        if (username != null) {
            entity.setStringField(usernameFieldName, username);
        }
    }

    private void updateLdap(EntityStore entityStore, Credential credential) {
        Trace.info("updating LDAP");
        Entity entity = getEntity(entityStore, "/[LdapDirectoryGroup]name=LDAP Directories/[LdapDirectory]name=" + credential.getFilterName());
        if (entity == null)
            return;
        setUsernameAndPassword(credential, entity, "userName");
        String url = credential.getUrl();
        if (url != null) {
            entity.setStringField("url", url);
        }
        entityStore.updateEntity(entity);
    }

    private void updateJMS(EntityStore entityStore, Credential credential) {
        Trace.info("updating JMS");
        Entity entity = getEntity(entityStore, "/[JMSServiceGroup]name=JMS Services/[JMSService]name=" + credential.getFilterName());
        if (entity == null)
            return;
        setUsernameAndPassword(credential, entity, "userName");
        String url = credential.getUrl();
        if (url != null) {
            entity.setStringField("providerURL", url);
        }
        entityStore.updateEntity(entity);
    }

    private void updateSMTP(EntityStore entityStore, Credential credential) {
        Entity entity;
        if (credential.getFilterName().equalsIgnoreCase("manager")) {
            entity = getEntity(entityStore, "/[SMTPServerGroup]name=SMTP Servers/[SMTPServer]name=Portal SMTP");

        } else {
            entity = getEntity(entityStore, "/[SMTPServerGroup]name=SMTP Servers/[SMTPServer]name=" + credential.getFilterName());
        }
        setUsernameAndPassword(credential, entity, "username");

        String host = credential.getUrl();
        if (host != null) {
            entity.setStringField("smtpServer", host);
        }
        entityStore.updateEntity(entity);
    }

    private void updateAlertSMTP(EntityStore entityStore, Credential credential) {
        if (credential.getFilterName().equalsIgnoreCase("manager")) {
            Entity entity = getEntity(entityStore, "/[AlertManager]name=Default Alert Configuration/[EmailAlertSystem]name=API Manager Email Alerts");
            if(entity == null){
                return;
            }
            setUsernameAndPassword(credential, entity, "username");
            String host = credential.getUrl();
            if (host != null) {
                entity.setStringField("smtp", host);
            }
            entityStore.updateEntity(entity);
        }
    }

    private void updateCassandraCert(EntityStore entityStore, String escapedAlias) {
        String shorthandKey = "/[CassandraSettings]name=Cassandra Settings";
        Entity entity = getEntity(entityStore, shorthandKey);
        boolean useSSL = entity.getBooleanValue("useSSL");
        if (useSSL) {
            //String certPlaceHolder = "<key type='Certificates'><id field='name' value='Certificate Store'/><key type='Certificate'><id field='dname' value='" + escapedAlias + "'/></key></key>";
            PortableESPK portableESPK = getCertEntity(entityStore, escapedAlias);
            entity.setReferenceField("sslTrustedCerts", portableESPK);
            entityStore.updateEntity(entity);
        }
    }
    
    private void disableCassandraSSL(EntityStore entityStore) {
        String shorthandKey = "/[CassandraSettings]name=Cassandra Settings";
        Entity entity = getEntity(entityStore, shorthandKey);
        entity.setBooleanField("useSSL", false);
        entityStore.updateEntity(entity);
        Trace.info("Disabled Cassandra SSL");
    }

    // Supports both HTTP and HTTPS interfaces where interfaceType are InetInterface, SSLInterface
    private void disableInterface(EntityStore entityStore, String name, String interfaceType) {
        String shorthandKey = "/[NetService]name=Service/[HTTP]**/["+interfaceType+"]name=" + name;
        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        List<Entity> entities = shorthandKeyFinder.getEntities(shorthandKey);
        if (entities.isEmpty()) {
            Trace.error("Listener interface is not available");
            return;
        }
        Entity entity = entities.get(0);
        entity.setBooleanField("enabled", false);
        entityStore.updateEntity(entity);
        Trace.info("Disabled Interface: " + name);
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

    private void configureP12(EntityStore entityStore, String name, String alias) {

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

    private PortableESPK getCertEntity(EntityStore entityStore, String alias) {
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


    private String importP12(EntityStore entityStore, String cert, char[] password) throws Exception {

        PKCS12 pkcs12 = certHelper.parseP12(new File(cert), password);
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
