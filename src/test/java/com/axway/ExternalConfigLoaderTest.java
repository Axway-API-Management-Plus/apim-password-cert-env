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
import java.util.Properties;

import static org.powermock.api.mockito.PowerMockito.mockStatic;


@RunWith(PowerMockRunner.class)
@PrepareForTest({ Trace.class })
@SuppressStaticInitializationFor({ "com.vordel.trace.Trace" })
@PowerMockIgnore("javax.management.*")
public class ExternalConfigLoaderTest {

    private ExternalConfigLoader externalConfigLoader = new ExternalConfigLoader();

    @Test
    public void testDisableCassandraSSL(){
        File file = new File("src/test/resources/f37d16cb-0f3d-42f0-b6cb-85c073b36c43/configs.xml");
        String url = "federated:file:"+file.getAbsolutePath();
        EntityStore entityStore =  EntityStoreFactory.createESForURL(url);
        entityStore.connect(url, new Properties());
        externalConfigLoader.disableCassandraSSL(entityStore);
        String shorthandKey = "/[CassandraSettings]name=Cassandra Settings";
        Entity entity = externalConfigLoader.getEntity(entityStore, shorthandKey);
        Assert.assertEquals("Disable cassandra SSL", false, entity.getBooleanValue("useSSL"));

    }


    @Before
    public void setup() {
        mockStatic(Trace.class);

    }
}
