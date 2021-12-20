package com.axway;

import com.vordel.es.Entity;
import com.vordel.es.EntityStore;
import com.vordel.es.EntityStoreFactory;
import com.vordel.trace.Trace;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

import static org.powermock.api.mockito.PowerMockito.mockStatic;


@RunWith(PowerMockRunner.class)
@PrepareForTest({ Trace.class })
@SuppressStaticInitializationFor({ "com.vordel.trace.Trace" })
@PowerMockIgnore("javax.management.*")
public class ExternalConfigLoaderTest {

    private ExternalConfigLoader externalConfigLoader = new ExternalConfigLoader();
    private EntityStore entityStore;

    @Test
    public void testDisableCassandraSSL(){

        externalConfigLoader.disableCassandraSSL(entityStore);
        String shorthandKey = "/[CassandraSettings]name=Cassandra Settings";
        Entity entity = externalConfigLoader.getEntity(entityStore, shorthandKey);
        Assert.assertEquals("Disable cassandra SSL", false, entity.getBooleanValue("useSSL"));
    }


    @Test
    public void testUpdateLDAP(){
        String ldapConnectionName = "axway";
        Map<String, String> attributes = new HashMap<>();
        attributes.put("url","ldap://localhost:389");
        attributes.put("userName","cn=test,dc=axway,dc=com");
        attributes.put("password","changeme");
        externalConfigLoader.updateLDAP(entityStore, attributes, ldapConnectionName);
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
        attributes.put("providerURL","ssl://b-871f83a2-9d81-47ce-af1e-8fdc23775442-1.mq.us-east-2.amazonaws.com:61617");
        attributes.put("userName","axway");
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
    public void testUpdateCassandraCert(){
        String filterName = "axway";
        String alias = "";
        externalConfigLoader.updateCassandraCert(entityStore, alias, true);
    }

    @Test
    public void testListenerKeyAndCertificate() throws Exception {
        String filterName = "traffic";

        String cert ="";
        String pemKey = System.getenv("listenerkey" + "_" + filterName);
        String caCert = System.getenv("listenercacert" + "_" + filterName);
        String mTLS = "false";

        PKCS12 pkcs12 = externalConfigLoader.importCertAndKeyAndCA(entityStore, cert, caCert, pemKey, null);
        Trace.info("Pem file alias name :" + pkcs12.getAlias());
        externalConfigLoader.configureP12(entityStore, filterName, pkcs12, mTLS);

        String shorthandKey = "/[NetService]name=Service/[HTTP]**/[SSLInterface]name=" + filterName;
        List<Entity> entities =  externalConfigLoader.getEntities(entityStore, shorthandKey);
        Entity entity = entities.get(0);
    }

    @Test
    public void testConnectToURLKeyAndCertificate() throws Exception {
        String filterName = "backend2ssl";
        String cert ="";

        String pemKey = System.getenv("listenerkey" + "_" + filterName);
        String caCert = System.getenv("listenercacert" + "_" + filterName);

        PKCS12 pkcs12 = externalConfigLoader.importCertAndKeyAndCA(entityStore, cert, caCert, pemKey, null);
        Trace.info("Pem file alias name :" + pkcs12.getAlias());
        externalConfigLoader.connectToURLConfigureP12(entityStore, filterName, pkcs12.getAlias());

        String shorthandKey = "/[CircuitContainer]**/[FilterCircuit]**/[ConnectToURLFilter]name=" + filterName;
        List<Entity> entities =  externalConfigLoader.getEntities(entityStore, shorthandKey);
        Entity entity = entities.get(0);
    }


    @Test
    public void testJWTSignKeyAndCertificate() throws Exception {
        String filterName = "jwtsign";
        String cert ="";

        String pemKey = System.getenv("jwtsignkey" + "_" + filterName);
        String caCert = System.getenv("jwtsigncacert" + "_" + filterName);
        String alias = System.getenv("jwtsignkid" + "_" + filterName);

        PKCS12 pkcs12 = externalConfigLoader.importCertAndKeyAndCA(entityStore, cert, caCert, pemKey, null);
        Trace.info("Pem file alias name :" + pkcs12.getAlias());
        externalConfigLoader.jwtSignConfigureP12(entityStore, filterName, alias);


        String shorthandKey = "/[CircuitContainer]**/[FilterCircuit]**/[JWTSignFilter]name=" + filterName;
        List<Entity> entities =  externalConfigLoader.getEntities(entityStore, shorthandKey);
        Entity entity = entities.get(0);
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
