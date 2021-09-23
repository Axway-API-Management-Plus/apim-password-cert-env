package com.axway;

import com.vordel.common.Config;
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
import java.io.FileNotFoundException;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;


public class ExternalConfigLoader implements LoadableModule {

    private static final Logger log = LogManager.getLogger(ExternalConfigLoader.class);
    private final CertHelper certHelper = new CertHelper();
    private final ExternalInstanceDomainCert externalInstanceDomainCert = new ExternalInstanceDomainCert();
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

        Map<String, String> ldap = groupEnvVariables(envValues,"ldap_");
        Map<String, String> jms = groupEnvVariables(envValues,"jms_");
        Map<String, String> smtp = groupEnvVariables(envValues,"smtp_");
        Map<String, String> cassandraConsistency = groupEnvVariables(envValues, "cassandraconsistency_");

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
                try {
                    List<X509Certificate> certificates = certHelper.parseX509(passwordValue);
                    for (X509Certificate certificate : certificates) {
                        importPublicCertificate(certificate, entityStore);
                    }
                } catch (CertificateException | FileNotFoundException e) {
                    Trace.error("Unable to add the certs from Environment variable", e);
                }
            } else if (key.startsWith("disablehttps_")) {
                if (passwordValue.equalsIgnoreCase("true")) {
                    disableInterface(entityStore, filterName, "SSLInterface");
                }
            } else if (key.startsWith("disablehttp_")) {
                if (passwordValue.equalsIgnoreCase("true")) {
                    disableInterface(entityStore, filterName, "InetInterface");
                }
            } else if (key.equalsIgnoreCase("cassandra_disablessl")) {
                if (passwordValue.equalsIgnoreCase("true")) {
                    disableCassandraSSL(entityStore);
                }
            } else if (key.startsWith("cassandraCert")) {
                try {
                    List<X509Certificate> certificates = certHelper.parseX509(passwordValue);
                    int index = 0;
                    for (X509Certificate certificate : certificates) {
                        String alias = importPublicCertificate(certificate, entityStore);
                        if (alias != null) {
                            if (index == 0)
                                updateCassandraCert(entityStore, alias, false);
                            else
                                updateCassandraCert(entityStore, alias, true);
                            index++;
                        }
                    }
                } catch (CertificateException | FileNotFoundException e) {
                    Trace.error("Unable to add Cassandra certificate from Environment variable", e);
                }
            } else if (key.startsWith("certandkey_")) {
                try {
                    Trace.info("Updating SSL interface certificate and key");
                    char[] password = System.getenv("certandkeypassword" + "_" + filterName).toCharArray();
                    String mTLS = System.getenv("certandkeymtls" + "_" + filterName);
                    PKCS12 pkcs12 = importP12(entityStore, passwordValue, password);
                    Trace.info("P12 file alias name :" + pkcs12.getAlias());
                    configureP12(entityStore, filterName, pkcs12, mTLS);
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
                    String caAlias = externalInstanceDomainCert.certsFile(pkcs12, certsXml);
                    File mgmtXml = new File(gatewayConfDir, "mgmt.xml");
                    externalInstanceDomainCert.updateMgmtFile(mgmtXml, caAlias);
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

    private Map<String, String>  groupEnvVariables( Map<String, String> envValues, String namePrefix){
        return envValues.entrySet()
                .stream()
                .filter(map -> map.getKey().startsWith(namePrefix))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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
        updateMailConnectionType(entity, credential.getFilterName());
        entityStore.updateEntity(entity);
    }

    private void updateAlertSMTP(EntityStore entityStore, Credential credential) {
        if (credential.getFilterName().equalsIgnoreCase("manager")) {
            Entity entity = getEntity(entityStore, "/[AlertManager]name=Default Alert Configuration/[EmailAlertSystem]name=API Manager Email Alerts");
            if (entity == null) {
                return;
            }
            setUsernameAndPassword(credential, entity, "username");
            String host = credential.getUrl();
            if (host != null) {
                entity.setStringField("smtp", host);
            }
            updateMailConnectionType(entity, credential.getFilterName());
            entityStore.updateEntity(entity);
        }
    }
    private void updateMailConnectionType(Entity entity, String filterName) {
        String connectionType = System.getenv("smtp_" + filterName + "_connectionType");
        if (connectionType != null) {
            // Possible Values NONE, SSL TLS
            if (MailConnectionTypes.valueOf(connectionType) != null) {
                entity.setStringField("connectionType", connectionType);
            } else {
                Trace.error("Invalid connection type : " + connectionType);
            }
        }
    }

    private void updateCassandraCert(EntityStore entityStore, String alias, boolean append) {
        String shorthandKey = "/[CassandraSettings]name=Cassandra Settings";
        Entity entity = getEntity(entityStore, shorthandKey);
        boolean useSSL = entity.getBooleanValue("useSSL");
        if (useSSL) {
            String filedName = "sslTrustedCerts";
            updateCertEntity(entityStore, entity, alias, filedName, append);
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
        String shorthandKey = "/[NetService]name=Service/[HTTP]**/[" + interfaceType + "]name=" + name;
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
                certEntity = EntityStoreDelegate.createDefaultedEntity(entityStore, "Certificate");
                ESPK rootPK = entityStore.getRootPK();
                EntityType group = entityStore.getTypeForName("Certificates");
                Collection<ESPK> groups = entityStore.listChildren(rootPK, group);
                certEntity.setStringField("dname", alias);
                certEntity.setBinaryValue("content", certificate.getEncoded());
                entityStore.addEntity(groups.iterator().next(), certEntity);
            } else {
                Trace.info("Updating cert with alias " + escapedAlias);
                certEntity.setBinaryValue("content", certificate.getEncoded());
                entityStore.updateEntity(certEntity);
            }
            return alias;
        } catch (CertificateException e) {
            Trace.error("Unable to add the certs from Environment variable", e);
        }
        return null;
    }

    private void configureP12(EntityStore entityStore, String name, PKCS12 pkcs12, String mTLS) {

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
            if (i == 1) {
                updateCertEntity(entityStore, entity, alias, fieldName, false);
            } else
                // Trust more than one certificate for mutual auth
                updateCertEntity(entityStore, entity, alias, fieldName, true);
        }
    }

    private List<Entity> getEntities(EntityStore entityStore, String shorthandKey) {
        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        return shorthandKeyFinder.getEntities(shorthandKey);
    }

    private void updateCertEntity(EntityStore entityStore, Entity entity, String alias, String fieldName, boolean append) {

        String escapedAlias = ShorthandKeyFinder.escapeFieldValue(alias);
        Entity certEntity = getCertEntity(entityStore, escapedAlias);
        // Trace.info("Certificate entity set to listener interface "+ certEntity);
        PortableESPK portableESPK = PortableESPK.toPortableKey(entityStore, certEntity.getPK());
        //Trace.info("Portable : " + portableESPK);
        if (append) {
            Field field = entity.getField(fieldName);
            List<Value> values = field.getValueList();
            List<Value> cloneVales = new ArrayList<>(values);
            for (Value value : cloneVales) {
                PortableESPK espk = (PortableESPK) value.getRef();
                String certStoreDistinguishedName = espk.getFieldValueOfReferencedEntity("dname");
                Trace.info(" alias name from Gateway Cert store :" + certStoreDistinguishedName);
                if (certStoreDistinguishedName.equals(alias)) {
                    Trace.info("Removing existing certs" + alias);
                    values.remove(value);
                }
                Trace.info("adding " + alias);
                values.add(new Value(portableESPK));
            }
            field.setValues(values);
        } else {
            entity.setReferenceField(fieldName, portableESPK);
        }
        entityStore.updateEntity(entity);
    }

    private void connectToURLConfigureP12(EntityStore entityStore, String name, String alias) {

        String shorthandKey = "/[FilterCircuit]**/[ConnectToURLFilter]name=" + name;
        //ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        List<Entity> entities = getEntities(entityStore, shorthandKey);
        if (entities.isEmpty()) {
            Trace.error("Unable to find connect to URL filter");
            return;
        } else if (entities.size() > 1) {
            Trace.error("Found more than one connect to URL filter");
            return;
        }
        Entity entity = entities.get(0);
        String fieldName = "sslUsers";
        updateCertEntity(entityStore, entity, alias, fieldName, false);
    }

    private Entity getCertEntity(EntityStore entityStore, String alias) {
        String shorthandKey = "/[Certificates]name=Certificate Store";
        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        Entity entity = shorthandKeyFinder.getEntity(shorthandKey);
        shorthandKey = "[Certificate]dname=" + alias;
        return shorthandKeyFinder.getEntity(entity.getPK(), shorthandKey);
    }


    private PKCS12 importP12(EntityStore entityStore, String cert, char[] password) throws Exception {

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
        // Trace.info("Certificate Entity received from entity store : "+ certEntity);
        if (certEntity != null) {
            //Updates the existing certificate in the certstore
            Trace.info("Updating existing certificate");
            for (int i = 0; i < certificates.length; i++) {
                if (i == 0) {
                    certEntity.setBinaryValue("content", certificates[i].getEncoded());
                    String key = Base64.getEncoder().encodeToString(pkcs12.getPrivateKey().getEncoded());
                    certEntity.setStringField("key", key);
                    entityStore.updateEntity(certEntity);
                } else {
                    //handle CA Certificate chain
                    X509Certificate certificate = (X509Certificate) certificates[i];
                    importPublicCertificate(certificate, entityStore);
                }
            }
        } else {
            ESPK rootPK = entityStore.getRootPK();
            EntityType group = entityStore.getTypeForName("Certificates");
            Collection<ESPK> groups = entityStore.listChildren(rootPK, group);
            certEntity = EntityStoreDelegate.createDefaultedEntity(entityStore, "Certificate");
            for (int i = 0; i < certificates.length; i++) {
                if (i == 0) {
                    Trace.info("Importing Leaf certificate");
                    certEntity.setStringField("dname", alias);
                    certEntity.setBinaryValue("content", certificates[i].getEncoded());
                    String key = Base64.getEncoder().encodeToString(pkcs12.getPrivateKey().getEncoded());
                    certEntity.setStringField("key", key);
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

    private void updateCassandraConsistencyLevel(EntityStore entityStore, String readConsistencyLevel, String writeConsistencyLevel) {

        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        // Update KPS table consistency level
        updateCassandraConsistencyLevel(shorthandKeyFinder, "/[KPSRoot]name=Key Property Stores/[KPSPackage]**/[KPSDataSourceGroup]**/[KPSCassandraDataSource]name=Cassandra Storage",
                "readConsistencyLevel", readConsistencyLevel, "writeConsistencyLevel", writeConsistencyLevel);
        // Update OAUTH table consistency level
//        updateCassandraConsistencyLevel(shorthandKeyFinder, "/[KPSRoot]name=Key Property Stores/[KPSPackage]**/[KPSDataSourceGroup]name=DataSources/[KPSCassandraDataSource]name=Cassandra Storage",
//                "readConsistencyLevel", readConsistencyLevel, "writeConsistencyLevel", writeConsistencyLevel);
        // Update Quota table consistency level
        updateCassandraConsistencyLevel(shorthandKeyFinder, "/[PortalConfiguration]name=Portal Config",
                "quotaReadConsistency", readConsistencyLevel, "quotaWriteConsistency", writeConsistencyLevel);
        //Update throttling consistency level
        updateCassandraConsistencyLevel(shorthandKeyFinder, "/[CassandraSettings]name=Cassandra Settings",
                "throttlingReadConsistencyLevel", readConsistencyLevel, "throttlingWriteConsistencyLevel", writeConsistencyLevel);

        //Update access token  consistency level
        updateCassandraConsistencyLevel(shorthandKeyFinder, "/[OAuth2StoresGroup]name=OAuth2 Stores/[AccessTokenStoreGroup]name=Access Token Stores/[AccessTokenPersist]**",
                "readConsistencyLevel", readConsistencyLevel, "writeConsistencyLevel", writeConsistencyLevel);

        //Update auth code consistency level
        updateCassandraConsistencyLevel(shorthandKeyFinder, "/[OAuth2StoresGroup]name=OAuth2 Stores/[AuthzCodeStoreGroup]name=Authorization Code Stores/[AuthzCodePersist]**",
                "readConsistencyLevel", readConsistencyLevel, "writeConsistencyLevel", writeConsistencyLevel);
        //update client access token consistency level
        updateCassandraConsistencyLevel(shorthandKeyFinder, "/[OAuth2StoresGroup]name=OAuth2 Stores/[ClientAccessTokenStoreGroup]name=Client Access Token Stores/[ClientAccessTokenPersist]**",
                "readConsistencyLevel", readConsistencyLevel, "writeConsistencyLevel", writeConsistencyLevel);

    }

    private void updateCassandraConsistencyLevel(ShorthandKeyFinder shorthandKeyFinder, String shorthandKey, String readConsistencyLevelFieldName, String readConsistencyLevel, String writeConsistencyLevelFieldName, String writeConsistencyLevel) {
        List<Entity> kpsEntities = shorthandKeyFinder.getEntities(shorthandKey);
        if (kpsEntities != null) {
            Trace.info("Total number of KPS Store: " + kpsEntities.size() + " in entity : "+ shorthandKey);
            EntityStore entityStore = shorthandKeyFinder.getEntityStore();
            for (Entity entity : kpsEntities) {
//                Trace.info(entity.toString());
//                Trace.info("Read "+ entity.getStringValue(readConsistencyLevelFieldName));
//                Trace.info("Write "+ entity.getStringValue(readConsistencyLevelFieldName));
                entity.setStringField(readConsistencyLevelFieldName, readConsistencyLevel);
                entity.setStringField(writeConsistencyLevelFieldName, writeConsistencyLevel);
                entityStore.updateEntity(entity);
//                Trace.info("Update Read "+ entity.getStringValue(readConsistencyLevelFieldName));
//                Trace.info("Update Write "+ entity.getStringValue(readConsistencyLevelFieldName));

            }
        }
    }

    public enum MailConnectionTypes {NONE, SSL, TLS}

}
