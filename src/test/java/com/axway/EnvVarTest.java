package com.axway;

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
        Map<String, Map<String, String>> output = externalConfigLoader.parseCred(smtp);
        System.out.println(output);


    }

}
