package com.axway;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class EnvVarTest {

    @Test
    public void testSmtpEnvVars() {
        ExternalConfigLoader externalConfigLoader = new ExternalConfigLoader();
        Map<String, String> smtp = new HashMap<>();
        smtp.put("smtp_manager_url", "email-smtp.us-east-1.amazonaws.com");
        smtp.put("smtp_manager_username", "rathna");
        smtp.put("smtp_manager_password", "xyx");
        smtp.put("smtp_manager_port", "465");
        smtp.put("smtp_manager_connectionType", "SSL");
        smtp.put("smtp_SB_url", "email-smtp.us-east-1.amazonaws.com");
        smtp.put("smtp_SB_username", "rathna");
        smtp.put("smtp_SB_password", "xyx");
        smtp.put("smtp_SB_port", "465");
        smtp.put("smtp_SB_connectionType", "SSL");
        //Negative values
        smtp.put("smtp_SBc1", "SSL");
        Map<String, Map<String, String>> output = externalConfigLoader.parseCred(smtp);
        Assert.assertEquals("email-smtp.us-east-1.amazonaws.com", output.get("manager").get("url"));


    }

    @Test
    public void testSmtpEnvVarsNegative() {
        ExternalConfigLoader externalConfigLoader = new ExternalConfigLoader();
        Map<String, String> smtp = new HashMap<>();
        smtp.put("smtp_manager_url", "email-smtp.us-east-1.amazonaws.com");
        smtp.put("smtp_manager_username", "rathna");
        smtp.put("smtp_manager_password", "xyx");
        smtp.put("smtp_manager_port", "465");
        smtp.put("smtp_manager_connectionType", "SSL");
        smtp.put("smtp_SB_url", "email-smtp.us-east-1.amazonaws.com");
        smtp.put("smtp_SB_username", "rathna");
        smtp.put("smtp_SB_password", "xyx");
        smtp.put("smtp_SB_port", "465");
        smtp.put("smtp_SB_connectionType", "SSL");
        //Negative values
        smtp.put("smtp_SBc1", "SSL");
        Map<String, Map<String, String>> output = externalConfigLoader.parseCred(smtp);
        Assert.assertNull(output.get("SBc1"));


    }

}
