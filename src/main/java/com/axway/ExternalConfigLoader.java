package com.axway;

import com.vordel.common.Config;
import com.vordel.common.crypto.PasswordCipher;
import com.vordel.config.ConfigContext;
import com.vordel.config.LoadableModule;
import com.vordel.es.*;
import com.vordel.es.util.ShorthandKeyFinder;
import com.vordel.es.xes.PortableESPK;
import com.vordel.trace.Trace;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

public class ExternalConfigLoader implements LoadableModule {

    public static final String SMTP = "smtp_";
    public static final String PASSWORD = "password";
    public static final String USERNAME = "username";
    public static final String DNAME = "dname";
    public static final String CONTENT = "content";
    public static final String CASSANDRA_SETTINGS_NAME_CASSANDRA_SETTINGS = "/[CassandraSettings]name=Cassandra Settings";
    public static final String USE_SSL = "useSSL";
    public static final String CERTIFICATE = "Certificate";
    public static final String CERTIFICATES = "Certificates";
    public static final String READ_CONSISTENCY_LEVEL = "readConsistencyLevel";
    public static final String WRITE_CONSISTENCY_LEVEL = "writeConsistencyLevel";
    private final CertHelper certHelper = new CertHelper();
    private final ExternalInstanceDomainCert externalInstanceDomainCert = new ExternalInstanceDomainCert();
    private final List<String> mailConnectionTypes = Arrays.asList("NONE", "SSL", "TLS");

    private PasswordCipher passwordCipher;

    public ExternalConfigLoader(PasswordCipher passwordCipher){
        this.passwordCipher = passwordCipher;
    }

    public ExternalConfigLoader(){
    }

    @Override
    public void load(LoadableModule arg0, String arg1) {
        Trace.info("loading Password and Certificate Environment variable Module");
    }

    @Override
    public void unload() {
        Trace.info("unloading Password and Certificate Environment variable Module");
    }

    @Override
    public void configure(ConfigContext configContext, Entity entity) throws EntityStoreException {
        passwordCipher = configContext.getCipher();
        Trace.info("Loading configuration Password and Certificate Environment variable Module");
        EntityStore entityStore = configContext.getStore();
        updatePassword(entityStore);
        Trace.info("ExternalConfigLoader - Environment variables update is complete");
    }

    public void updatePassword(EntityStore entityStore) {
        Map<String, String> envValues = System.getenv();
        Set<String> keys = envValues.keySet();
        Iterator<String> keysIterator = keys.iterator();
        Map<String, String> ldap = Util.groupEnvVariables(envValues, "ldap_");
        Map<String, String> jms = Util.groupEnvVariables(envValues, "jms_");
        Map<String, String> smtp = Util.groupEnvVariables(envValues, SMTP);
        Map<String, String> httpBasic = Util.groupEnvVariables(envValues, "httpbasic_");
        Map<String, String> cassandraConsistency = Util.groupEnvVariables(envValues, "cassandraconsistency_");

        while (keysIterator.hasNext()) {
            String key = keysIterator.next();
            if (!key.contains("_"))
                continue;
            if(key.startsWith("ldap_") || key.startsWith("jms_") || key.startsWith(SMTP) || key.startsWith("httpbasic_") || key.startsWith("cassandraconsistency_"))
                continue;
            String filterName = key.split("_")[1];
            String passwordValue = envValues.get(key);
            if (key.startsWith("cert_")) {
                importCertificates(entityStore, passwordValue);
            } else if (key.startsWith("disablehttps_")) {
                if (passwordValue.equalsIgnoreCase("true")) {
                    disableInterface(entityStore, filterName, "SSLInterface");
                }
            } else if (key.startsWith("disablehttp_")) {
                if (passwordValue.equalsIgnoreCase("true")) {
                    disableInterface(entityStore, filterName, "InetInterface");
                }
            } else if (key.equalsIgnoreCase("cassandra_disablessl")) {
                disableCassandraSSL(entityStore, passwordValue);
            } else if (key.startsWith("cassandraCert")) {
                try {
                    String pemKey = System.getenv("cassandra_private_key");
                    String publicKey = System.getenv("cassandra_public_key");
                    if( pemKey != null && publicKey != null) {
                        PKCS12 pkcs12 = importCertAndKeyAndCA(entityStore, publicKey, passwordValue, pemKey, null);
                        Trace.info("Pem file alias name :" + pkcs12.getAlias());
                        updateCassandraCertAndKey(entityStore, pkcs12.getAlias(), pkcs12.getCertificates());
                    }else {
                        List<X509Certificate> certificates = certHelper.parseX509(passwordValue);

                        int index = 0;
                        for (X509Certificate certificate : certificates) {
                            String alias = importPublicCertificate(certificate, entityStore);
                            if (alias != null) {
                                updateCassandraCert(entityStore, alias, index != 0);
                                index++;
                            }
                        }
                    }
                } catch (Exception e) {
                    Trace.error("Unable to add Cassandra certificate from Environment variable", e);
                }
            } else if (key.startsWith("certandkey_")) {
                try {
                    Trace.info("Updating SSL interface certificate and key");
                    char[] password = System.getenv("certandkeypassword" + "_" + filterName).toCharArray();
                    String mTLS = System.getenv("certandkeymtls" + "_" + filterName);
                    PKCS12 pkcs12 = importP12(entityStore, passwordValue, password);
                    Trace.info("P12 file alias name :" + pkcs12.getAlias());
                    if(!filterName.equalsIgnoreCase("certstore")) {
                        configureP12(entityStore, filterName, pkcs12, mTLS);
                    }
                } catch (Exception e) {
                    Trace.error("Unable to add the p12 from Environment variable", e);
                }
            } else if (key.startsWith("connecttourlcertandkey_")) {
                try {
                    Trace.info("Updating Connect to URL client Auth certificate and key");
                    char[] password = System.getenv("connecttourlcertandkeypassword" + "_" + filterName).toCharArray();
                    String alias = importP12(entityStore, passwordValue, password).getAlias();
                    Trace.info("P12 file alias name :" + alias);
                    connectToURLConfigureP12(entityStore, filterName, alias);
                } catch (Exception e) {
                    Trace.error("Unable to add the p12 from Environment variable", e);
                }
            } else if (key.startsWith("listenercert_")) {
                try {
                    Trace.info("Updating SSL interface certificate, CA and key");
                    String pemKey = System.getenv("listenerkey" + "_" + filterName);
                    String caCert = System.getenv("listenercacert" + "_" + filterName);
                    String mTLS = System.getenv("listenermtls" + "_" + filterName);
                    PKCS12 pkcs12 = importCertAndKeyAndCA(entityStore, passwordValue, caCert, pemKey, null);
                    Trace.info("Pem file alias name :" + pkcs12.getAlias());
                    configureP12(entityStore, filterName, pkcs12, mTLS);
                } catch (Exception e) {
                    Trace.error("Unable to add the pem key, ca and certificate from Environment variable", e);
                }
            } else if (key.startsWith("connecttourlcert_")) {
                try {
                    Trace.info("Updating Connect to URL client Auth certificate and key");
                    String pemKey = System.getenv("connecttourlkey" + "_" + filterName);
                    String caCert = System.getenv("connecttourlcacert" + "_" + filterName);
                    String alias = importCertAndKeyAndCA(entityStore, passwordValue, caCert, pemKey, null).getAlias();
                    Trace.info("Pem file alias name :" + alias);
                    connectToURLConfigureP12(entityStore, filterName, alias);
                } catch (Exception e) {
                    Trace.error("Unable to add the  key and certificate from Environment variable", e);
                }
            } else if (key.startsWith("jwtsigncert_")) {
                try {
                    Trace.info("Updating JWT Sign -   Signing key");
                    String pemKey = System.getenv("jwtsignkey" + "_" + filterName);
                    String caCert = System.getenv("jwtsigncacert" + "_" + filterName);
                    String alias = System.getenv("jwtsignkid" + "_" + filterName);
                    PKCS12 pkcs12 = importCertAndKeyAndCA(entityStore, passwordValue, caCert, pemKey, alias);
                    if (alias == null) {
                        alias = pkcs12.getAlias();
                    }
                    Trace.info("Pem file alias name :" + alias);
                    jwtSignConfigureP12(entityStore, filterName, alias);
                } catch (Exception e) {
                    Trace.error("Unable to add the  key and certificate from Environment variable", e);
                }
            } else if (key.startsWith("jwtverifycert_")) {
                try {
                    Trace.info("Updating JWT  verify certificate");
                    X509Certificate certificate = certHelper.parseX509(passwordValue).get(0);
                    String alias = importPublicCertificate(certificate, entityStore);
                    if(!jwtVerifyConfigureCertificate(entityStore, filterName, alias)){
                        Trace.error("Unable to update certificate to JWT verify Filter");
                    }
                } catch (Exception e) {
                    Trace.error("Unable to update certificate to JWT verify Filter");
                }
            } else if (key.startsWith("gatewaytoplogycertandkey_")) {
                try {
                    Trace.info("Updating Gateway topology certificate");
                    char[] password = System.getenv("gatewaytoplogycertandkeypassword" + "_" + filterName).toCharArray();
                    File file = new File(passwordValue);
                    PKCS12 pkcs12;
                    if (file.exists()) {
                        pkcs12 = certHelper.parseP12(file, password);
                    } else {
                        pkcs12 = certHelper.parseP12(passwordValue, password);
                    }
                    File gatewayConfDir = new File(Config.getVDir("VINSTDIR"), "conf");
                    File certsXml = new File(gatewayConfDir, "certs.xml");
                    String caAlias = externalInstanceDomainCert.certsFile(pkcs12, certsXml, passwordCipher);
                    File mgmtXml = new File(gatewayConfDir, "mgmt.xml");
                    externalInstanceDomainCert.updateMgmtFile(mgmtXml, caAlias);
                } catch (Exception e) {
                    Trace.error("Unable to add the p12 from Environment variable", e);
                }
            } else if (key.startsWith("cassandra_password")) {
                try {
                    updateCassandraPassword(entityStore, passwordValue.toCharArray());
                } catch (GeneralSecurityException e) {
                    Trace.error("Unable to set Cassandra password", e);
                }
            }
        }

        Map<String, Map<String, String>> httpBasicObjs = Util.parseCred(httpBasic);
        updateHttpBasic(httpBasicObjs, entityStore);

        Map<String, Map<String, String>> ldapObjs = Util.parseCred(ldap);
        updateLDAP(entityStore, ldapObjs);

        Map<String, Map<String, String>> jmsObjs = Util.parseCred(jms);
        if (!jmsObjs.isEmpty()) {
            for (Map.Entry<String, Map<String, String>> entry : jmsObjs.entrySet()) {
                String filterName = entry.getKey();
                Map<String, String> attributes = entry.getValue();
                updateJMS(entityStore, attributes, filterName);
            }
        }
        Map<String, Map<String, String>> smtpObjs = Util.parseCred(smtp);
        if (!smtpObjs.isEmpty()) {
            for (Map.Entry<String, Map<String, String>> entry : smtpObjs.entrySet()) {
                String filterName = entry.getKey();
                Map<String, String> attributes = entry.getValue();
                updateSMTP(entityStore, attributes, filterName);
                updateAlertSMTP(entityStore, attributes, filterName);
            }
        }
        if (!cassandraConsistency.isEmpty()) {
            String readConsistencyLevel = cassandraConsistency.get("cassandraconsistency_readlevel");
            String writeConsistencyLevel = cassandraConsistency.get("cassandraconsistency_writelevel");
            if (readConsistencyLevel != null && writeConsistencyLevel != null) {
                updateCassandraConsistencyLevel(entityStore, readConsistencyLevel, writeConsistencyLevel);
            } else {
                Trace.info("cassandraconsistency_readlevel and cassandraconsistency_writelevel environment variables are not found");
            }
        }
    }

    public void updateHttpBasic(Map<String, Map<String, String>> httpBasicObjs, EntityStore entityStore){
        if (!httpBasicObjs.isEmpty()) {
            for (Map.Entry<String, Map<String, String>> entry : httpBasicObjs.entrySet()) {
                String filterName = entry.getKey();
                Map<String, String> attributes = entry.getValue();
                String password = attributes.get(PASSWORD);
                String shorthandKey = "/[AuthProfilesGroup]name=Auth Profiles/[BasicAuthGroup]name=HTTP Basic/[BasicProfile]name=" + filterName;

                Entity entity = getEntity(entityStore, shorthandKey);
                if (entity == null) {
                    Trace.error("Unable to find HttpBasic auth profile :"+ filterName);
                    return;
                }
                Trace.info("updating HttpBasic profile password");
                String base64Password = Base64.getEncoder().encodeToString(password.getBytes());
                entity.setStringField("httpAuthPass", base64Password);
                entityStore.updateEntity(entity);

            }
        }
    }

    public void importCertificates(EntityStore entityStore, String passwordValue) {
        try {
            List<X509Certificate> certificates = certHelper.parseX509(passwordValue);
            for (X509Certificate certificate : certificates) {
                importPublicCertificate(certificate, entityStore);
            }
        } catch (CertificateException | FileNotFoundException e) {
            Trace.error("Unable to add the certs from Environment variable", e);
        }
    }

    public Entity getEntity(EntityStore entityStore, String shorthandKey) {
        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        return shorthandKeyFinder.getEntity(shorthandKey);
    }

    private void setUsernameAndPassword(Map<String, String> attributes, Entity entity, String usernameFieldName) throws GeneralSecurityException {
        String password = attributes.get(PASSWORD);
        if (password != null) {
            byte[] encryptedPassword = passwordCipher.encrypt(password.getBytes());
            password = Base64.getEncoder().encodeToString(encryptedPassword);
            entity.setStringField(PASSWORD, password);
        }
        String username = attributes.get(USERNAME);
        if (username != null) {
            entity.setStringField(usernameFieldName, username);
        }
    }

    public void updateLDAP(EntityStore entityStore, Map<String, Map<String, String>> ldapObjs)  {
        if (!ldapObjs.isEmpty()) {
            for (Map.Entry<String, Map<String, String>> entry : ldapObjs.entrySet()) {
                String filterName = entry.getKey();
                Map<String, String> attributes = entry.getValue();
                Trace.info("updating LDAP : "+ filterName);
                Entity entity = getEntity(entityStore, "/[LdapDirectoryGroup]name=LDAP Directories/[LdapDirectory]name=" + filterName);
                if (entity == null)
                    return;
                try {
                    setUsernameAndPassword(attributes, entity, "userName");
                    String url = attributes.get("url");
                    if (url != null) {
                        entity.setStringField("url", url);
                    }
                    entityStore.updateEntity(entity);
                } catch (GeneralSecurityException e) {
                    throw new RuntimeException(e);
                }

            }
        }

    }

    public void updateJMS(EntityStore entityStore, Map<String, String> attributes, String filterName) {
        Trace.info("updating JMS");
        Entity entity = getEntity(entityStore, "/[JMSServiceGroup]name=JMS Services/[JMSService]name=" + filterName);
        if (entity == null)
            return;
        try {
            setUsernameAndPassword(attributes, entity, "userName");
            String url = attributes.get("url");
            if (url != null) {
                entity.setStringField("providerURL", url);
            }
            entityStore.updateEntity(entity);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }

    }

    public void updateSMTP(EntityStore entityStore, Map<String, String> attributes, String filterName) {
        Trace.info("Updating SMTP connection");
        Entity entity;
        if (filterName.equalsIgnoreCase("manager")) {
            entity = getEntity(entityStore, "/[SMTPServerGroup]name=SMTP Servers/[SMTPServer]name=Portal SMTP");
        } else {
            entity = getEntity(entityStore, "/[SMTPServerGroup]name=SMTP Servers/[SMTPServer]name=" + filterName);
        }
        if (entity == null) {
            Trace.error("Unable to locate SMTP connection : " + filterName);
            return;
        }
        try {
            setUsernameAndPassword(attributes, entity, USERNAME);
            String host = attributes.get("url");
            if (host != null) {
                entity.setStringField("smtpServer", host);
            }
            updateMailConnectionTypeAndPort(entity, filterName, "smtpPort");
            entityStore.updateEntity(entity);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }

    }

    public void updateAlertSMTP(EntityStore entityStore, Map<String, String> attributes, String filterName) {
        Trace.info("Updating SMTP Alert connection");
        if (filterName.equalsIgnoreCase("manager")) {
            Entity entity = getEntity(entityStore, "/[AlertManager]name=Default Alert Configuration/[EmailAlertSystem]name=API Manager Email Alerts");
            if (entity == null) {
                return;
            }
            try {
                setUsernameAndPassword(attributes, entity, USERNAME);
                String host = attributes.get("url");
                if (host != null) {
                    entity.setStringField("smtp", host);
                }
                updateMailConnectionTypeAndPort(entity, filterName, "port");
                entityStore.updateEntity(entity);
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void updateMailConnectionTypeAndPort(Entity entity, String filterName, String portFieldName) {
        String connectionType = System.getenv(SMTP + filterName + "_connectionType");
        if (connectionType != null) {
            // Possible Values NONE, SSL TLS
            if (mailConnectionTypes.contains(connectionType)) {
                entity.setStringField("connectionType", connectionType);
            } else {
                Trace.error("Invalid connection type : " + connectionType);
            }
        }
        String port = System.getenv(SMTP + filterName + "_port");
        if (port != null) {
            try {
                entity.setStringField(portFieldName, port);
            } catch (NumberFormatException e) {
                Trace.error("Invalid SMTP port number :" + port);
            }
        }
    }

    public void updateCassandraPassword(EntityStore entityStore, char[] password) throws GeneralSecurityException {
        Entity entity = getEntity(entityStore, CASSANDRA_SETTINGS_NAME_CASSANDRA_SETTINGS);
        byte[] encryptedPassword = passwordCipher.encrypt(String.valueOf(password).getBytes());
        String encodedPassword = Base64.getEncoder().encodeToString(encryptedPassword);
        entity.setStringField(PASSWORD, encodedPassword);
        entityStore.updateEntity(entity);
    }

    public void updateCassandraCertAndKey(EntityStore entityStore, String clientAuthAlias, Certificate[] certificates) {
        Entity entity = getEntity(entityStore, CASSANDRA_SETTINGS_NAME_CASSANDRA_SETTINGS);
        boolean useSSL = entity.getBooleanValue(USE_SSL);
        if (useSSL) {
            String clientAuth = "sslCertificate";
            updateCertEntity(entityStore, entity, clientAuthAlias, clientAuth, false);
            String filedName = "sslTrustedCerts";

            if( certificates.length > 1){
                // Start from 1 To ignore public key associated with private key
                for (int i = 1; i < certificates.length; i++) {
                    Certificate certificate = certificates[i];
                    String alias = Util.getAliasName((X509Certificate) certificate);
                    updateCertEntity(entityStore, entity, alias, filedName, true);
                }
            }
        }
    }

    public void updateCassandraCert(EntityStore entityStore, String alias, boolean append) {
        Entity entity = getEntity(entityStore, CASSANDRA_SETTINGS_NAME_CASSANDRA_SETTINGS);
        boolean useSSL = entity.getBooleanValue(USE_SSL);
        if (useSSL) {
            String filedName = "sslTrustedCerts";
            updateCertEntity(entityStore, entity, alias, filedName, append);
        }
    }

    public void disableCassandraSSL(EntityStore entityStore, String value) {
        Entity entity = getEntity(entityStore, CASSANDRA_SETTINGS_NAME_CASSANDRA_SETTINGS);
        boolean boolValue = Boolean.parseBoolean(value);
        entity.setBooleanField(USE_SSL, !boolValue);
        entityStore.updateEntity(entity);
        if(!boolValue)
            Trace.info("Disabled Cassandra SSL");
        else
            Trace.info("Enabled Cassandra SSL");
    }

    // Supports both HTTP and HTTPS interfaces where interfaceType are InetInterface, SSLInterface
    public void disableInterface(EntityStore entityStore, String name, String interfaceType) {
        String shorthandKey = "/[NetService]name=Service/[HTTP]**/[" + interfaceType + "]name=" + name;
        List<Entity> entities = getEntities(entityStore, shorthandKey);
        if (entities.isEmpty()) {
            Trace.error("Listener interface is not available");
            return;
        }
        Entity entity = entities.get(0);
        entity.setBooleanField("enabled", false);
        entityStore.updateEntity(entity);
        Trace.info("Disabled Interface: " + name);
    }

    // Trust CA Certs
    private String importPublicCertificate(X509Certificate certificate, EntityStore entityStore) {
        try {
            Principal principal = certificate.getSubjectDN();
            final String alias = principal.getName();
            String escapedAlias = ShorthandKeyFinder.escapeFieldValue(alias);
            Entity certEntity = getCertEntity(entityStore, escapedAlias);
            Trace.info("Alias :" + alias + "Escaped alias :" + escapedAlias);
            if (certEntity == null) {
                Trace.info("Adding cert");
                certEntity = EntityStoreDelegate.createDefaultedEntity(entityStore, CERTIFICATE);
                ESPK rootPK = entityStore.getRootPK();
                EntityType group = entityStore.getTypeForName(CERTIFICATES);
                Collection<ESPK> groups = entityStore.listChildren(rootPK, group);
                certEntity.setStringField(DNAME, alias);
                certEntity.setBinaryValue(CONTENT, certificate.getEncoded());
                entityStore.addEntity(groups.iterator().next(), certEntity);
            } else {
                Trace.info("Updating cert with alias " + escapedAlias);
                certEntity.setBinaryValue(CONTENT, certificate.getEncoded());
                entityStore.updateEntity(certEntity);
            }
            return alias;
        } catch (CertificateException e) {
            Trace.error("Unable to add the certs from Environment variable", e);
        }
        return null;
    }

    public void configureP12(EntityStore entityStore, String name, PKCS12 pkcs12, String mTLS) {

        String shorthandKey = "/[NetService]name=Service/[HTTP]**/[SSLInterface]name=" + name;
        List<Entity> entities = getEntities(entityStore, shorthandKey);
        if (entities.isEmpty()) {
            Trace.error("Listener interface is not available");
            return;
        } else if (entities.size() > 1) {
            Trace.error("Found more than one Listener interface");
            return;
        }
        Entity entity = entities.get(0);
        String fieldName = "serverCert";
        String alias = pkcs12.getAlias();
        updateCertEntity(entityStore, entity, alias, fieldName, false);
        Trace.info("Mutual auth flag : " + mTLS);
        if (mTLS != null && mTLS.equalsIgnoreCase("true")) {
            String clientAuth = entity.getStringValue("clientAuth");
            Trace.info("Mutual auth configured with flag : " + clientAuth);
            if (clientAuth.equals("required") || clientAuth.equals("optional")) {
                trustRootAndIntermediateCerts(entityStore, entity, pkcs12);
            }
        }
    }

    private void trustRootAndIntermediateCerts(EntityStore entityStore, Entity entity, PKCS12 pkcs12) {
        Certificate[] certificates = pkcs12.getCertificates();
        Trace.info("Trusting additional certs for mutual auth");
        Trace.info("Total certificates : " + certificates.length);
        for (int i = 1; i < certificates.length; i++) {
            X509Certificate certificate = (X509Certificate) certificates[i];
            Principal principal = certificate.getSubjectDN();
            final String alias = principal.getName();
            Trace.info("Trusting cert :" + alias);
            String fieldName = "caCert";
            // Trust more than one certificate for mutual auth
            updateCertEntity(entityStore, entity, alias, fieldName, i != 1);
        }
    }

    public List<Entity> getEntities(EntityStore entityStore, String shorthandKey) {
        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        return shorthandKeyFinder.getEntities(shorthandKey);
    }

    private void updateCertEntity(EntityStore entityStore, Entity entity, String alias, String fieldName, boolean append) {

        String escapedAlias = ShorthandKeyFinder.escapeFieldValue(alias);
        Entity certEntity = getCertEntity(entityStore, escapedAlias);
        PortableESPK portableESPK = PortableESPK.toPortableKey(entityStore, certEntity.getPK());
        if (append) {
            Field field = entity.getField(fieldName);
            List<Value> values = field.getValueList();
            List<Value> cloneVales = new ArrayList<>(values);
            for (Value value : cloneVales) {
                PortableESPK valueRef = (PortableESPK) value.getRef();
                String certStoreDistinguishedName = valueRef.getFieldValueOfReferencedEntity(DNAME);
                Trace.info(" alias name from Gateway Cert store :" + certStoreDistinguishedName);
                if (certStoreDistinguishedName.equals(alias)) {
                    Trace.info("Removing existing cert as it matches the current cert" + alias);
                    values.remove(value);
                }
            }
            Trace.info("adding " + alias);
            values.add(new Value(portableESPK));
            field.setValues(values);
        } else {
            Trace.debug("Replacing exising cert reference");
            entity.setReferenceField(fieldName, portableESPK);
        }
        entityStore.updateEntity(entity);
    }

    public void connectToURLConfigureP12(EntityStore entityStore, String name, String alias) {

        String shorthandKey = "/[CircuitContainer]**/[FilterCircuit]**/[ConnectToURLFilter]name=" + name;
        List<Entity> entities = getEntities(entityStore, shorthandKey);
        if (entities.isEmpty()) {
            Trace.error("Unable to find connect to URL filter under container");
            shorthandKey = "/[FilterCircuit]**/[ConnectToURLFilter]name=" + name;
            entities = getEntities(entityStore, shorthandKey);
            if(entities.isEmpty())
                return;
        }
        String fieldName = "sslUsers";
        for (Entity entity : entities) {
            updateCertEntity(entityStore, entity, alias, fieldName, false);
        }
    }

    public boolean jwtVerifyConfigureCertificate(EntityStore entityStore, String name, String alias) {

        String shorthandKey = "/[CircuitContainer]**/[FilterCircuit]**/[JWTVerifyFilter]name=" + name;
        List<Entity> entities = getEntities(entityStore, shorthandKey);
        if (entities.isEmpty()) {
            Trace.error("Unable to find JWT verify filter under container");
            shorthandKey = "/[FilterCircuit]**/[JWTVerifyFilter]name=" + name;
            entities = getEntities(entityStore, shorthandKey);
            if(entities.isEmpty())
                return false;
        }
        String fieldName = "publicKeyAlias";
        for (Entity entity : entities) {
            updateCertEntity(entityStore, entity, alias, fieldName, false);
        }
        return true;
    }

    public void jwtSignConfigureP12(EntityStore entityStore, String name, String alias) {

        String shorthandKey = "/[CircuitContainer]**/[FilterCircuit]**/[JWTSignFilter]name=" + name;
        List<Entity> entities = getEntities(entityStore, shorthandKey);
        if (entities.isEmpty()) {
            Trace.info("Unable to find JWT Sign filter under container");
            shorthandKey = "/[FilterCircuit]**/[JWTSignFilter]name=" + name;
            entities = getEntities(entityStore, shorthandKey);
            if(entities.isEmpty())
                return;
        }
        String fieldName = "privateKeyAlias";
        for (Entity entity : entities) {
            updateCertEntity(entityStore, entity, alias, fieldName, false);
        }
    }

    public Entity getCertEntity(EntityStore entityStore, String alias) {
        String shorthandKey = "/[Certificates]name=Certificate Store";
        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        Entity entity = shorthandKeyFinder.getEntity(shorthandKey);
        shorthandKey = "[Certificate]dname=" + alias;
        return shorthandKeyFinder.getEntity(entity.getPK(), shorthandKey);
    }


    public PKCS12 importP12(EntityStore entityStore, String cert, char[] password) throws GeneralSecurityException, IOException {

        PKCS12 pkcs12;
        File file = new File(cert);
        if (file.exists()) {
            pkcs12 = certHelper.parseP12(file, password);
        } else {
            pkcs12 = certHelper.parseP12(cert, password);
        }
        String alias = pkcs12.getAlias();
        Trace.info("Certificate alias name : " + alias);
        String escapedAlias = ShorthandKeyFinder.escapeFieldValue(alias);
        Certificate[] certificates = pkcs12.getCertificates();
        Entity certEntity = getCertEntity(entityStore, escapedAlias);
        Trace.info("Escaped Certificate alias name : " + escapedAlias);
        if (certEntity != null) {
            //Updates the existing certificate in the certStore
            Trace.info("Updating existing certificate");
            for (int i = 0; i < certificates.length; i++) {
                if (i == 0) {
                    updateCertificateEntityWithKey(certEntity, certificates[i].getEncoded(), pkcs12.getPrivateKey().getEncoded());
                    entityStore.updateEntity(certEntity);
                } else {
                    //handle CA Certificate chain
                    X509Certificate certificate = (X509Certificate) certificates[i];
                    importPublicCertificate(certificate, entityStore);
                }
            }
        } else {
            ESPK rootPK = entityStore.getRootPK();
            EntityType group = entityStore.getTypeForName(CERTIFICATES);
            Collection<ESPK> groups = entityStore.listChildren(rootPK, group);
            certEntity = EntityStoreDelegate.createDefaultedEntity(entityStore, CERTIFICATE);
            for (int i = 0; i < certificates.length; i++) {
                if (i == 0) {
                    Trace.info("Importing Leaf certificate");
                    certEntity.setStringField(DNAME, alias);
                    updateCertificateEntityWithKey(certEntity, certificates[i].getEncoded(), pkcs12.getPrivateKey().getEncoded());
                    entityStore.addEntity(groups.iterator().next(), certEntity);
                    Trace.info("Leaf certificate imported");
                } else {
                    //handle CA Certificate chain
                    Trace.info("Importing certificate root / intermediate");
                    X509Certificate certificate = (X509Certificate) certificates[i];
                    importPublicCertificate(certificate, entityStore);
                    Trace.info("Imported root / intermediate certificate");
                }
            }
        }
        return pkcs12;
    }

    public void updateCertificateEntityWithKey(Entity certEntity, byte[] publicKey, byte[] privateKey) throws GeneralSecurityException {
        Trace.info("Updating existing certificate");
        certEntity.setBinaryValue(CONTENT, publicKey);
        byte[] keyBytes = passwordCipher.encrypt(privateKey);
        String keyStr = Base64.getEncoder().encodeToString(keyBytes);
        certEntity.setStringField("key", keyStr);
        certEntity.setStringField("issuer", "-1");
    }


    public PKCS12 importCertAndKeyAndCA(EntityStore entityStore, String cert, String ca, String key, String alias) throws GeneralSecurityException, IOException {

        PKCS12 pkcs12 = new PKCS12();
        List<X509Certificate> caCerts = new ArrayList<>();
        Trace.info("ca cert " + ca);
        if (ca != null) {
            caCerts = certHelper.parseX509(ca);
        }
        X509Certificate certObj = certHelper.parseX509(cert).get(0);
        if (alias == null) {
            alias = certObj.getSubjectDN().getName();
            if (alias.isEmpty()) {
                alias = certObj.getSerialNumber().toString();
            }
        }
        PrivateKey privateKey = certHelper.parsePrivateKey(key);
        if (privateKey == null) {
            throw new IOException("Unable to parse a private key");
        }
        Trace.info("Certificate alias name : " + alias);
        String escapedAlias = ShorthandKeyFinder.escapeFieldValue(alias);
        Entity certEntity = getCertEntity(entityStore, escapedAlias);
        Trace.info("Escaped Certificate alias name : " + escapedAlias);
        if (certEntity != null) {
            //Updates the existing certificate in the certStore
            Trace.info("Updating existing certificate");
            updateCertificateEntityWithKey(certEntity, certObj.getEncoded(), privateKey.getEncoded());
            entityStore.updateEntity(certEntity);

            //handle CA Certificate chain
            for (X509Certificate x509Certificate : caCerts) {
                importPublicCertificate(x509Certificate, entityStore);
            }
        } else {
            ESPK rootPK = entityStore.getRootPK();
            EntityType group = entityStore.getTypeForName(CERTIFICATES);
            Collection<ESPK> groups = entityStore.listChildren(rootPK, group);
            certEntity = EntityStoreDelegate.createDefaultedEntity(entityStore, CERTIFICATE);
            Trace.info("Importing Leaf certificate");
            certEntity.setStringField(DNAME, alias);
            updateCertificateEntityWithKey(certEntity, certObj.getEncoded(), privateKey.getEncoded());
            entityStore.addEntity(groups.iterator().next(), certEntity);
            Trace.info("Leaf certificate imported");
            //handle CA Certificate chain
            for (X509Certificate x509Certificate : caCerts) {
                Trace.info("Importing certificate root / intermediate");
                importPublicCertificate(x509Certificate, entityStore);
                Trace.info("Imported root / intermediate certificate");
            }
        }
        pkcs12.setAlias(alias);
        pkcs12.setPrivateKey(privateKey);
        List<Certificate> certificates = new ArrayList<>();
        certificates.add(certObj);
        certificates.addAll(caCerts);
        Certificate[] certificatesArray = new Certificate[certificates.size()];
        pkcs12.setCertificates(certificates.toArray(certificatesArray));
        return pkcs12;
    }

    public void updateCassandraConsistencyLevel(EntityStore entityStore, String readConsistencyLevel, String writeConsistencyLevel) {

        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        // Update KPS table consistency level
        updateCassandraConsistencyLevel(shorthandKeyFinder, "/[KPSRoot]name=Key Property Stores/[KPSPackage]**/[KPSDataSourceGroup]**/[KPSCassandraDataSource]name=Cassandra Storage",
            READ_CONSISTENCY_LEVEL, readConsistencyLevel, WRITE_CONSISTENCY_LEVEL, writeConsistencyLevel);
        updateCassandraConsistencyLevel(shorthandKeyFinder, "/[PortalConfiguration]name=Portal Config",
                "quotaReadConsistency", readConsistencyLevel, "quotaWriteConsistency", writeConsistencyLevel);
        //Update throttling consistency level
        updateCassandraConsistencyLevel(shorthandKeyFinder, CASSANDRA_SETTINGS_NAME_CASSANDRA_SETTINGS,
                "throttlingReadConsistencyLevel", readConsistencyLevel, "throttlingWriteConsistencyLevel", writeConsistencyLevel);
        //Update access token  consistency level
        updateCassandraConsistencyLevel(shorthandKeyFinder, "/[OAuth2StoresGroup]name=OAuth2 Stores/[AccessTokenStoreGroup]name=Access Token Stores/[AccessTokenPersist]**",
            READ_CONSISTENCY_LEVEL, readConsistencyLevel, WRITE_CONSISTENCY_LEVEL, writeConsistencyLevel);
        //Update auth code consistency level
        updateCassandraConsistencyLevel(shorthandKeyFinder, "/[OAuth2StoresGroup]name=OAuth2 Stores/[AuthzCodeStoreGroup]name=Authorization Code Stores/[AuthzCodePersist]**",
            READ_CONSISTENCY_LEVEL, readConsistencyLevel, WRITE_CONSISTENCY_LEVEL, writeConsistencyLevel);
        //update client access token consistency level
        updateCassandraConsistencyLevel(shorthandKeyFinder, "/[OAuth2StoresGroup]name=OAuth2 Stores/[ClientAccessTokenStoreGroup]name=Client Access Token Stores/[ClientAccessTokenPersist]**",
            READ_CONSISTENCY_LEVEL, readConsistencyLevel, WRITE_CONSISTENCY_LEVEL, writeConsistencyLevel);

    }

    private void updateCassandraConsistencyLevel(ShorthandKeyFinder shorthandKeyFinder, String shorthandKey, String readConsistencyLevelFieldName, String readConsistencyLevel,
                                                 String writeConsistencyLevelFieldName, String writeConsistencyLevel) {
        List<Entity> kpsEntities = shorthandKeyFinder.getEntities(shorthandKey);
        if (kpsEntities != null) {
            Trace.info("Total number of KPS Store: " + kpsEntities.size() + " in entity : " + shorthandKey);
            EntityStore entityStore = shorthandKeyFinder.getEntityStore();
            for (Entity entity : kpsEntities) {
                entity.setStringField(readConsistencyLevelFieldName, readConsistencyLevel);
                entity.setStringField(writeConsistencyLevelFieldName, writeConsistencyLevel);
                entityStore.updateEntity(entity);
            }
        }
    }
}
