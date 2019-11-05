package com.axway;


import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.Enumeration;

public class CertHelper {

    public PKCS12 parseP12(String content, String password) throws KeyStoreException, NoSuchAlgorithmException, IOException, CertificateException, UnrecoverableKeyException {
        //Get the Key store
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        InputStream io = new ByteArrayInputStream(Base64.getDecoder().decode(content));
        //Loads the KeyStore from the file input stream and uses the password
        //to unlock the keystore, or to check the integrity of the keystore data.
        //    #If the password is not given for integrity checking, then
        //#integrity checking is not performed.
        keyStore.load(io, password.toCharArray());
        io.close();
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) {
                Certificate certificate =  keyStore.getCertificate(alias);
                PrivateKey key = (PrivateKey)keyStore.getKey(alias, password.toCharArray());
                PKCS12 pkcs12 = new PKCS12();
                pkcs12.setCertificate(certificate);
                pkcs12.setPrivateKey(key);
                return  pkcs12;
            }
        }

        return null;
    }

}
