package com.axway;

import java.security.KeyStore;

public class CertHelper {

    public String getCertFromP12(String content, String password){}
    //Get the Key store
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    Bas
    io = FileInputStream(file)
        # Loads the KeyStore from the file input stream and uses the password
        # to unlock the keystore, or to check the integrity of the keystore data.
            # If the password is not given for integrity checking, then
        # integrity checking is not performed.
            ks.load(io, String(password).toCharArray())
            io.close()

            # Lists all the alias names of this keystore.
            aliases = ks.aliases()

            # Returns the first Certificate that matches an alias name
        while (aliases.hasMoreElements()):
    alias = aliases.nextElement()
            if (ks.isKeyEntry(alias)):
            return ks.getCertificate(alias)
            return None

    # -------------------------------------------------------------------------
            # Utility method to get private key
    # -------------------------------------------------------------------------
    @classmethod
    def getKeyFromPEM(cls, file, password=None):
    io = FileInputStream(file)
        # PEMReader is used for reading OpenSSL PEM encoded streams containing
        # X509 certificates, PKCS8 encoded keys and PKCS7 objects.
        if password == None:
    reader = PEMReader(FileReader(file))
            else:
    reader = PEMReader(FileReader(file), MyPasswordFinder(password))
    keyPair = reader.readPemObject().readContent();
        if keyPair != None:
    kf = KeyFactory.getInstance("RSA")
            return kf.generatePrivate(PKCS8EncodedKeySpec(pemContent))
            return None

}
