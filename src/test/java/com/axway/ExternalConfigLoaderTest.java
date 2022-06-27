package com.axway;

import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityStoreFactory;
import com.vordel.es.Value;
import com.vordel.es.util.ShorthandKeyFinder;
import com.vordel.es.xes.PortableESPK;
import com.vordel.trace.Trace;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;

import static org.powermock.api.mockito.PowerMockito.mockStatic;


@RunWith(PowerMockRunner.class)
@PrepareForTest({ Trace.class })
@SuppressStaticInitializationFor({ "com.vordel.trace.Trace" })
@PowerMockIgnore("javax.management.*")
public class ExternalConfigLoaderTest {

    final private static Logger logger = LoggerFactory.getLogger(ExternalConfigLoaderTest.class);

    final private ExternalConfigLoader externalConfigLoader = new ExternalConfigLoader();
    private EntityStore entityStore;


    @Test
    public void testUpdateHttpBasic(){
        String filterName = "apimanager";
        Map<String, String> attributes = new HashMap<>();
        attributes.put("httpbasic_"+ filterName + "_password","changeme");
        Map<String, Map<String, String>> httpBasicObjs = Util.parseCred(attributes);
        externalConfigLoader.updateHttpBasic(httpBasicObjs,entityStore);
        String shorthandKey = "/[AuthProfilesGroup]name=Auth Profiles/[BasicAuthGroup]name=HTTP Basic/[BasicProfile]name=" + filterName;
        Entity entity = externalConfigLoader.getEntity(entityStore, shorthandKey);
        Assert.assertEquals("httpAuthPass", "changeme", new String(Base64.getDecoder().decode(entity.getStringValue("httpAuthPass"))));
    }

    @Test
    public void importP12Test() throws Exception {
        File file = new File(ClassLoader.getSystemResource("test.p12").getFile());
        String content = Base64.getEncoder().encodeToString(FileUtils.readFileToByteArray(file));
        PKCS12 pkcs12 = externalConfigLoader.importP12(entityStore, content, "changeit".toCharArray());
        String escapedAlias = ShorthandKeyFinder.escapeFieldValue(pkcs12.getAlias());
        Entity entity = externalConfigLoader.getCertEntity(entityStore, escapedAlias);
        Assert.assertNotNull(entity);

    }

    @Test
    public void testDisableInterface(){
        String filterName = "traffic";
        String interfaceType = "SSLInterface"; // for https
        //String interfaceType = "InetInterface"; // for http
        externalConfigLoader.disableInterface(entityStore, filterName, interfaceType );
        String shorthandKey = "/[NetService]name=Service/[HTTP]**/[" + interfaceType + "]name=" + filterName;
        Entity entity = externalConfigLoader.getEntities(entityStore, shorthandKey).get(0);
        Assert.assertFalse(entity.getBooleanValue("enabled"));

    }

    @Test
    public void testDisableCassandraSSL(){

        externalConfigLoader.disableCassandraSSL(entityStore, "true");
        String shorthandKey = "/[CassandraSettings]name=Cassandra Settings";
        Entity entity = externalConfigLoader.getEntity(entityStore, shorthandKey);
        System.out.println(entity.getBooleanValue("useSSL"));
        Assert.assertFalse(entity.getBooleanValue("useSSL"));
    }

    @Test
    public void testEnableCassandraSSL() throws NoSuchFieldException, IllegalAccessException{

        Map<String, String> envVars =  new HashMap<>();
        envVars.put("cassandra_disablessl", "false");
        setupEnvVariables(envVars);
        externalConfigLoader.updatePassword(entityStore);
        String shorthandKey = "/[CassandraSettings]name=Cassandra Settings";
        Entity entity = externalConfigLoader.getEntity(entityStore, shorthandKey);
        Assert.assertTrue(entity.getBooleanValue("useSSL"));
    }



    @Test
    public void testUpdateLDAP(){
        String ldapConnectionName = "axway";
        Map<String, String> attributes = new HashMap<>();
        attributes.put("ldap_"+ ldapConnectionName +"_url","ldap://localhost:389");
        attributes.put("ldap_"+ ldapConnectionName +"_username","cn=test,dc=axway,dc=com");
        attributes.put("ldap_"+ ldapConnectionName + "_password","changeme");
        Map<String, Map<String, String>> ldapObjs = Util.parseCred(attributes);
        externalConfigLoader.updateLDAP(entityStore, ldapObjs);
        String shorthandKey = "/[LdapDirectoryGroup]name=LDAP Directories/[LdapDirectory]name=" + ldapConnectionName;
        Entity entity = externalConfigLoader.getEntity(entityStore, shorthandKey);
        Assert.assertEquals("url", "ldap://localhost:389", entity.getStringValue("url"));
        Assert.assertEquals("userName", "cn=test,dc=axway,dc=com", entity.getStringValue("userName"));
        Assert.assertEquals("password", "changeme", new String(Base64.getDecoder().decode(entity.getStringValue("password"))));
    }


    @Test
    public void testUpdateJMS(){
        String filterName = "axway";
        Map<String, String> attributes = new HashMap<>();
        attributes.put("url","ssl://b-871f83a2-9d81-47ce-af1e-8fdc23775442-1.mq.us-east-2.amazonaws.com:61617");
        attributes.put("username","axway");
        attributes.put("password","changeme");
        externalConfigLoader.updateJMS(entityStore, attributes, filterName);
        String shorthandKey = "/[JMSServiceGroup]name=JMS Services/[JMSService]name=" + filterName;
        Entity entity = externalConfigLoader.getEntity(entityStore, shorthandKey);
        Assert.assertEquals("providerURL", "ssl://b-871f83a2-9d81-47ce-af1e-8fdc23775442-1.mq.us-east-2.amazonaws.com:61617", entity.getStringValue("providerURL"));
        Assert.assertEquals("userName", "axway", entity.getStringValue("userName"));
        Assert.assertEquals("password", "changeme", new String(Base64.getDecoder().decode(entity.getStringValue("password"))));
    }

    public void setupEnvVariables( Map<String, String> inputParams) throws NoSuchFieldException, IllegalAccessException {
        Map<String, String> env = System.getenv();
        Field field = env.getClass().getDeclaredField("m");
        field.setAccessible(true);
        Map<String, String> envVars = (Map<String, String>) field.get(env);
        envVars.clear();
        envVars.putAll(inputParams);
    }

    @Test
    public void testUpdateSMTP() throws NoSuchFieldException, IllegalAccessException {

        Map<String, String> envVars =  new HashMap<>();
        envVars.put("smtp_manager_port", 587+"");
        envVars.put("smtp_manager_connectionType","TLS");
        setupEnvVariables(envVars);
        String filterName = "manager";
        Map<String, String> attributes = new HashMap<>();
        attributes.put("url","smtp.axway.com");
        attributes.put("username","rnatarajan");
        attributes.put("password","changeme");
        externalConfigLoader.updateSMTP(entityStore, attributes, filterName);
        String shorthandKey = "/[SMTPServerGroup]name=SMTP Servers/[SMTPServer]name=Portal SMTP";
        Entity entity = externalConfigLoader.getEntity(entityStore, shorthandKey);
        Assert.assertEquals("smtpServer", "smtp.axway.com", entity.getStringValue("smtpServer"));
        Assert.assertEquals("username", "rnatarajan", entity.getStringValue("username"));
        Assert.assertEquals("password", "changeme", new String(Base64.getDecoder().decode(entity.getStringValue("password"))));
        Assert.assertEquals("connectionType", "TLS", entity.getStringValue("connectionType"));
        Assert.assertEquals("smtpPort", "587", entity.getStringValue("smtpPort"));
    }

    @Test
    public void testUpdateAlertSMTP() throws NoSuchFieldException, IllegalAccessException{
        Map<String, String> envVars =  new HashMap<>();
        envVars.put("smtp_manager_port", 587+"");
        envVars.put("smtp_manager_connectionType","TLS");
        setupEnvVariables(envVars);
        String filterName = "manager";
        Map<String, String> attributes = new HashMap<>();
        attributes.put("url","smtp.axway.com");
        attributes.put("username","rnatarajan");
        attributes.put("password","changeme");
        externalConfigLoader.updateAlertSMTP(entityStore, attributes, filterName);
        Entity entity = externalConfigLoader.getEntity(entityStore, "/[AlertManager]name=Default Alert Configuration/[EmailAlertSystem]name=API Manager Email Alerts");
        Assert.assertEquals("smtp", "smtp.axway.com", entity.getStringValue("smtp"));
        Assert.assertEquals("username", "rnatarajan", entity.getStringValue("username"));
        Assert.assertEquals("password", "changeme", new String(Base64.getDecoder().decode(entity.getStringValue("password"))));
        Assert.assertEquals("connectionType", "TLS", entity.getStringValue("connectionType"));
        Assert.assertEquals("port", "587", entity.getStringValue("port"));

    }

    @Test
    public void testUpdateCassandraPassword(){
        char[] password = "changeme".toCharArray();
        externalConfigLoader.updateCassandraPassword(entityStore, password);
        String shorthandKey = "/[CassandraSettings]name=Cassandra Settings";
        Entity entity = externalConfigLoader.getEntity(entityStore, shorthandKey);
        Assert.assertEquals("password", "changeme", new String(Base64.getDecoder().decode(entity.getStringValue("password"))));
    }

    @Test
    public void testImportCertificates() throws FileNotFoundException, CertificateException {

        String backendCertificate = "-----BEGIN CERTIFICATE-----\n" +
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

        CertHelper certHelper = new CertHelper();
        X509Certificate certificate = certHelper.parseX509(backendCertificate).get(0);
        String alias = certificate.getSubjectDN().getName();
        String escapedAlias = ShorthandKeyFinder.escapeFieldValue(alias);

        externalConfigLoader.importCertificates(entityStore, backendCertificate);
        Entity entity = externalConfigLoader.getCertEntity(entityStore, escapedAlias);
        logger.info("Entity : {}", entity);

        Assert.assertNotNull(entity);

    }

    @Test
    public void testUpdateCassandraCert() throws NoSuchFieldException, IllegalAccessException {

        Map<String, String> envVars =  new HashMap<>();
        String certificate = "-----BEGIN CERTIFICATE-----\n" +
            "MIICxDCCAaygAwIBAgIGAW5HwjW7MA0GCSqGSIb3DQEBCwUAMBExDzANBgNVBAMM\n" +
            "BkRvbWFpbjAgFw0xOTEwMzEyMTI1NDBaGA8yMTE5MTAxNDIxMjU0MFowETEPMA0G\n" +
            "A1UEAwwGRG9tYWluMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlX2n\n" +
            "ePJaDMGWpNUwgyCfyDVIMjLKRjvJ7bID+BF+LI9gxJ2mUVFXl822fT3m2BR5oG8s\n" +
            "N/8JgvM+ie2PHxAWYokQcRSwYAFmMMMKp69M8sqAJHrm/QoVvFwCFVm+7DqJVKWu\n" +
            "q5K+J+ophJQNhvSl0KLorFI8IodLZq5cDtyhfaB27Zbk1A9ha4PfXmnoFWbDwoZU\n" +
            "UanoUy3xisbZ6HTvGKkawn53XaRJo5rn13b/9Np8PCJZLNmAiWoIB3NVyetwxS5C\n" +
            "4FwIm2ZRJZny5l+CgJ9Frs9Y0teAz4Z1bqJWn+kfBCxGW8Ab7W7t6ah3a/WoQxi2\n" +
            "HDU/134lBvoPhh9udwIDAQABoyAwHjAPBgNVHRMECDAGAQH/AgEAMAsGA1UdDwQE\n" +
            "AwICvDANBgkqhkiG9w0BAQsFAAOCAQEAlEo5pn1j8spkVg3RbLap80iwo8Slk+Fw\n" +
            "v8tGqR+GJEiJXDgnPPDMkrE+wtC1kT4VxyQw8D0eittUPjFmoMdxoUwM5Ddf4qS7\n" +
            "3LBO74CULyFZ0teyJoaVBjaG6MTg0ZfwUZt552IVLBgjbbE/yYu/dOJckpZlcZE7\n" +
            "yRw3ffr/trqh2B5tzwJMnWsakRwAtooRJ2RZ8ufQUhEYdI/7KJajZDQ0IFxleyPZ\n" +
            "PLHu3INlHcXQs3AY0wNBLhL2jBwZ0uwBYK+entFpCgb+Z+RQ+uxs3joYuKEMj6M6\n" +
            "6Xi8yAoGAN92VRi93iss3A7zoAsrPXCO7pNZdz3QzJ3Jjv9KW48DmQ==\n" +
            "-----END CERTIFICATE-----";


        envVars.put("cassandraCert_root", certificate);
        setupEnvVariables(envVars);
        String shorthandKey = "/[CassandraSettings]name=Cassandra Settings";
        Entity entity = externalConfigLoader.getEntity(entityStore, shorthandKey);
        entity.setBooleanField("useSSL", true);
        entityStore.updateEntity(entity);
        externalConfigLoader.updatePassword(entityStore);
        entity = externalConfigLoader.getEntity(entityStore, shorthandKey);
        Assert.assertEquals("sslTrustedCerts", "/[Certificates]name=Certificate Store/[Certificate]dname=CN=Domain", ((PortableESPK)entity.getField("sslTrustedCerts").getValueList().get(0).getRef()).toShorthandString());

    }


    @Test
    public void testUpdateCassandraCertAndKey() throws NoSuchFieldException, IllegalAccessException {

        Map<String, String> envVars =  new HashMap<>();
        String certificate = "-----BEGIN CERTIFICATE-----\n" +
            "MIICxDCCAaygAwIBAgIGAW5HwjW7MA0GCSqGSIb3DQEBCwUAMBExDzANBgNVBAMM\n" +
            "BkRvbWFpbjAgFw0xOTEwMzEyMTI1NDBaGA8yMTE5MTAxNDIxMjU0MFowETEPMA0G\n" +
            "A1UEAwwGRG9tYWluMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlX2n\n" +
            "ePJaDMGWpNUwgyCfyDVIMjLKRjvJ7bID+BF+LI9gxJ2mUVFXl822fT3m2BR5oG8s\n" +
            "N/8JgvM+ie2PHxAWYokQcRSwYAFmMMMKp69M8sqAJHrm/QoVvFwCFVm+7DqJVKWu\n" +
            "q5K+J+ophJQNhvSl0KLorFI8IodLZq5cDtyhfaB27Zbk1A9ha4PfXmnoFWbDwoZU\n" +
            "UanoUy3xisbZ6HTvGKkawn53XaRJo5rn13b/9Np8PCJZLNmAiWoIB3NVyetwxS5C\n" +
            "4FwIm2ZRJZny5l+CgJ9Frs9Y0teAz4Z1bqJWn+kfBCxGW8Ab7W7t6ah3a/WoQxi2\n" +
            "HDU/134lBvoPhh9udwIDAQABoyAwHjAPBgNVHRMECDAGAQH/AgEAMAsGA1UdDwQE\n" +
            "AwICvDANBgkqhkiG9w0BAQsFAAOCAQEAlEo5pn1j8spkVg3RbLap80iwo8Slk+Fw\n" +
            "v8tGqR+GJEiJXDgnPPDMkrE+wtC1kT4VxyQw8D0eittUPjFmoMdxoUwM5Ddf4qS7\n" +
            "3LBO74CULyFZ0teyJoaVBjaG6MTg0ZfwUZt552IVLBgjbbE/yYu/dOJckpZlcZE7\n" +
            "yRw3ffr/trqh2B5tzwJMnWsakRwAtooRJ2RZ8ufQUhEYdI/7KJajZDQ0IFxleyPZ\n" +
            "PLHu3INlHcXQs3AY0wNBLhL2jBwZ0uwBYK+entFpCgb+Z+RQ+uxs3joYuKEMj6M6\n" +
            "6Xi8yAoGAN92VRi93iss3A7zoAsrPXCO7pNZdz3QzJ3Jjv9KW48DmQ==\n" +
            "-----END CERTIFICATE-----";

        String pemKey = "src/test/resources/acp-key.pem";
        String cert = "src/test/resources/acp-crt.pem";


        envVars.put("cassandraCert_root", certificate);
        envVars.put("cassandra_private_key", pemKey);
        envVars.put("cassandra_public_key", cert);

        setupEnvVariables(envVars);
        String shorthandKey = "/[CassandraSettings]name=Cassandra Settings";
        Entity entity = externalConfigLoader.getEntity(entityStore, shorthandKey);
        entity.setBooleanField("useSSL", true);
        entityStore.updateEntity(entity);
        externalConfigLoader.updatePassword(entityStore);
        entity = externalConfigLoader.getEntity(entityStore, shorthandKey);
        String certAlias = "/[Certificates]name=Certificate Store/[Certificate]dname=CN=Domain";
        List<Value> values = entity.getField("sslTrustedCerts").getValueList();

        System.out.println(values);
        System.out.println(values.size());
        List<Value> filteredValues = values.stream().filter(value -> ((PortableESPK)value.getRef()).toShorthandString().equals(certAlias)).collect(Collectors.toList());

        Assert.assertEquals("sslTrustedCerts", certAlias, ((PortableESPK)filteredValues.get(0).getRef()).toShorthandString());
        Assert.assertEquals("sslCertificate", "/[Certificates]name=Certificate Store/[Certificate]dname=213910179734667807042092962809881497910", ((PortableESPK)entity.getField("sslCertificate").getValueList().get(0).getRef()).toShorthandString());


    }



    @Test
    public void testUpdateCassandraKPSTablesConsistencyLevel(){

        String readConsistencyLevel = "ONE";
        String writeConsistencyLevel = "ONE";
        externalConfigLoader.updateCassandraConsistencyLevel(entityStore, readConsistencyLevel, writeConsistencyLevel);
        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        List<Entity> kpsEntities = shorthandKeyFinder.getEntities("/[KPSRoot]name=Key Property Stores/[KPSPackage]**/[KPSDataSourceGroup]**/[KPSCassandraDataSource]name=Cassandra Storage");
        if (kpsEntities != null) {
            for (Entity entity : kpsEntities) {
                Assert.assertEquals("readConsistencyLevel", readConsistencyLevel, entity.getStringValue("readConsistencyLevel"));
                Assert.assertEquals("writeConsistencyLevel", writeConsistencyLevel, entity.getStringValue("writeConsistencyLevel"));
            }
        }
    }

    @Test
    public void testUpdateCassandraPortalConfigConsistencyLevel() {

        String readConsistencyLevel = "ONE";
        String writeConsistencyLevel = "ONE";
        externalConfigLoader.updateCassandraConsistencyLevel(entityStore, readConsistencyLevel, writeConsistencyLevel);
        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        List<Entity> kpsEntities = shorthandKeyFinder.getEntities("/[PortalConfiguration]name=Portal Config");
        if (kpsEntities != null) {
            for (Entity entity : kpsEntities) {
                Assert.assertEquals("quotaReadConsistency", readConsistencyLevel, entity.getStringValue("quotaReadConsistency"));
                Assert.assertEquals("quotaWriteConsistency", writeConsistencyLevel, entity.getStringValue("quotaWriteConsistency"));
            }
        }
    }

    @Test
    public void testUpdateCassandraThrottlingConsistencyLevel() {

        String readConsistencyLevel = "ONE";
        String writeConsistencyLevel = "ONE";
        externalConfigLoader.updateCassandraConsistencyLevel(entityStore, readConsistencyLevel, writeConsistencyLevel);
        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        List<Entity> kpsEntities = shorthandKeyFinder.getEntities("/[CassandraSettings]name=Cassandra Settings");
        if (kpsEntities != null) {
            for (Entity entity : kpsEntities) {
                Assert.assertEquals("throttlingReadConsistencyLevel", readConsistencyLevel, entity.getStringValue("throttlingReadConsistencyLevel"));
                Assert.assertEquals("throttlingWriteConsistencyLevel", writeConsistencyLevel, entity.getStringValue("throttlingWriteConsistencyLevel"));
            }
        }
    }

    @Test
    public void testUpdateCassandraAccessTokenConsistencyLevel() {

        String readConsistencyLevel = "ONE";
        String writeConsistencyLevel = "ONE";
        externalConfigLoader.updateCassandraConsistencyLevel(entityStore, readConsistencyLevel, writeConsistencyLevel);
        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        List<Entity> kpsEntities = shorthandKeyFinder.getEntities("/[OAuth2StoresGroup]name=OAuth2 Stores/[AccessTokenStoreGroup]name=Access Token Stores/[AccessTokenPersist]**");
        if (kpsEntities != null) {
            for (Entity entity : kpsEntities) {
                Assert.assertEquals("readConsistencyLevel", readConsistencyLevel, entity.getStringValue("readConsistencyLevel"));
                Assert.assertEquals("writeConsistencyLevel", writeConsistencyLevel, entity.getStringValue("writeConsistencyLevel"));
            }
        }
    }

    @Test
    public void testUpdateCassandraAuthCodeConsistencyLevel() {

        String readConsistencyLevel = "ONE";
        String writeConsistencyLevel = "ONE";
        externalConfigLoader.updateCassandraConsistencyLevel(entityStore, readConsistencyLevel, writeConsistencyLevel);
        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        List<Entity> kpsEntities = shorthandKeyFinder.getEntities("/[OAuth2StoresGroup]name=OAuth2 Stores/[AuthzCodeStoreGroup]name=Authorization Code Stores/[AuthzCodePersist]**");
        if (kpsEntities != null) {
            for (Entity entity : kpsEntities) {
                Assert.assertEquals("readConsistencyLevel", readConsistencyLevel, entity.getStringValue("readConsistencyLevel"));
                Assert.assertEquals("writeConsistencyLevel", writeConsistencyLevel, entity.getStringValue("writeConsistencyLevel"));            }
        }
    }

    @Test
    public void testUpdateCassandraClientAccessTokenConsistencyLevel() {

        String readConsistencyLevel = "ONE";
        String writeConsistencyLevel = "ONE";
        externalConfigLoader.updateCassandraConsistencyLevel(entityStore, readConsistencyLevel, writeConsistencyLevel);
        ShorthandKeyFinder shorthandKeyFinder = new ShorthandKeyFinder(entityStore);
        List<Entity> kpsEntities = shorthandKeyFinder.getEntities("/[OAuth2StoresGroup]name=OAuth2 Stores/[ClientAccessTokenStoreGroup]name=Client Access Token Stores/[ClientAccessTokenPersist]**");
        if (kpsEntities != null) {
            for (Entity entity : kpsEntities) {
                Assert.assertEquals("readConsistencyLevel", readConsistencyLevel, entity.getStringValue("readConsistencyLevel"));
                Assert.assertEquals("writeConsistencyLevel", writeConsistencyLevel, entity.getStringValue("writeConsistencyLevel"));
            }
        }
    }

    @Test
    public void testListenerKeyAndCertificate() throws Exception {
        String filterName = "traffic";

        String pemKey = "src/test/resources/acp-key.pem";
        String cert = "src/test/resources/acp-crt.pem";
        String caCert = "src/test/resources/acp-ca.pem";
        String alias = "alias-test";
        String mTLS = "false";

        PKCS12 pkcs12 = externalConfigLoader.importCertAndKeyAndCA(entityStore, cert, caCert, pemKey, alias);
        externalConfigLoader.configureP12(entityStore, filterName, pkcs12, mTLS);

        String shorthandKey = "/[NetService]name=Service/[HTTP]**/[SSLInterface]name=" + filterName;
        List<Entity> entities =  externalConfigLoader.getEntities(entityStore, shorthandKey);
        Entity entity = entities.get(0);
        Assert.assertEquals("serverCert", "/[Certificates]name=Certificate Store/[Certificate]dname=alias-test", ((PortableESPK)entity.getField("serverCert").getValueList().get(0).getRef()).toShorthandString());

    }

    @Test
    public void testJWTVerifyConfigureCertificate(){
        externalConfigLoader.jwtVerifyConfigureCertificate(entityStore, "jwtverify", "cn=test");

    }

    @Test
    public void testConnectToURLKeyAndCertificate() throws Exception {
        String filterName = "backend2ssl";

        String pemKey = "src/test/resources/acp-key.pem";
        String cert = "src/test/resources/acp-crt.pem";
        String caCert = "src/test/resources/acp-ca.pem";
        String alias = "alias-test";

        PKCS12 pkcs12 = externalConfigLoader.importCertAndKeyAndCA(entityStore, cert, caCert, pemKey, alias);
        externalConfigLoader.connectToURLConfigureP12(entityStore, filterName, pkcs12.getAlias());

        String shorthandKey = "/[FilterCircuit]**/[ConnectToURLFilter]name=" + filterName;
        List<Entity> entities =  externalConfigLoader.getEntities(entityStore, shorthandKey);
        Entity entity = entities.get(0);

        Assert.assertEquals("sslUsers", "/[Certificates]name=Certificate Store/[Certificate]dname=alias-test", ((PortableESPK)entity.getField("sslUsers").getValueList().get(0).getRef()).toShorthandString());

    }


    @Test
    public void testJWTSignKeyAndCertificate() throws Exception {
        String filterName = "jwtsign";

        String pemKey = "src/test/resources/acp-key.pem";
        String cert = "src/test/resources/acp-crt.pem";
        String caCert = "src/test/resources/acp-ca.pem";
        String alias = "alias-test";

        PKCS12 pkcs12 = externalConfigLoader.importCertAndKeyAndCA(entityStore, cert, caCert, pemKey, alias);
        logger.info("Pem file alias name : {}" , pkcs12.getAlias());
        externalConfigLoader.jwtSignConfigureP12(entityStore, filterName, alias);

        String shorthandKey = "/[FilterCircuit]**/[JWTSignFilter]name=" + filterName;
        List<Entity> entities =  externalConfigLoader.getEntities(entityStore, shorthandKey);
        Entity entity = entities.get(0);
        Assert.assertEquals("privateKeyAlias", "/[Certificates]name=Certificate Store/[Certificate]dname=alias-test", ((PortableESPK)entity.getField("privateKeyAlias").getValueList().get(0).getRef()).toShorthandString());
    }


    @Before
    public void setup() {
        mockStatic(Trace.class);
        File file = new File("src/test/resources/test-env/configs.xml");
        String url = "federated:file:"+file.getAbsolutePath();
        entityStore =  EntityStoreFactory.createESForURL(url);
        entityStore.connect(url, new Properties());

    }
}
