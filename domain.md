# Externalize API Gateway domain certificates

## Admin Node manager

- Create a CSR file
```bash
./gen_domain_cert.py --domain-id=dss --out=csr --O=Axway --OU=DSS --C=US --ST=AZ --L=Scottsdale --pass-file=rootcerts/pass.txt
```
command creates a folder named dss under apigw-emt-scripts-2.1.0-SNAPSHOT/certs/ with following files

    - dss.csr
    - dss-key.pem

- Create CA CSR, certificate and key
```bash
openssl genrsa -aes256 -out CA.key 2048
openssl req -new -sha256 -key CA.key -out CA.csr -subj "/C=US/ST=AZ/L=Scottsdale/O=AXWAY/CN=CACERTIFICATE"
openssl x509 -signkey CA.key -in CA.csr -req -days 3650 -out CA.pem
```
command creates following files

    - CA.key
    - CA.csr
    - CA.pem
    - CA.srl

- Create a file openssl.cnf with following content

```text
[policy_any]
domainComponent = optional
organizationalUnitName = optional
commonName = supplied

[req]
distinguished_name = req_distinguished_name

[req_distinguished_name]

[x509_extensions]

[domain_extensions]
basicConstraints = CA:TRUE, pathlen:0
keyUsage = digitalSignature, keyEncipherment, dataEncipherment, keyAgreement, keyCertSign

[admin_node_manager_extensions]
basicConstraints = CA:FALSE
keyUsage = digitalSignature, dataEncipherment, keyAgreement, keyEncipherment
extendedKeyUsage = serverAuth, clientAuth, 1.3.6.1.4.1.17998.10.1.1.2.1, 1.3.6.1.4.1.17998.10.1.1.2.2
subjectAltName = @alt_names

[gateway_extensions]
basicConstraints = CA:FALSE
keyUsage = digitalSignature, dataEncipherment, keyAgreement
extendedKeyUsage = serverAuth, clientAuth, 1.3.6.1.4.1.17998.10.1.1.2.3
subjectAltName = @alt_names

[alt_names]

DNS.1 = localhost
IP.1 = 127.0.0.1

```

- Sign dss.csr with CA certificate and key using openssl configuration 

```bash
openssl x509 -req -days 360 -in dss.csr -CA CA.pem -CAkey CA.key -CAcreateserial -out signedbyCA.crt -sha256 -extensions admin_node_manager_extensions -extfile openssl.cnf
```

- Create a P12 file from CA signed certificate and key file

```bash
openssl pkcs12 -export -in signedbyCA.crt -inkey dss-key.pem -out domain.p12 -chain -CAfile CA.pem -name 'topology-cert'
```
**alias name should be 'topology-cert'**

- Prepare Admin Node Manager fed file

    - Export Admin Node manager fed from classic installation, remove existing topology-cert and rename port name - "Management HTTPS Interface". The name should not contain any blank space (e.g sslport)

    - Import loadable module
  Policystudio using File -> Import -> Import Custom filters -> select apim-policy-password-cert-env/src/main/resources/typeSet.xml.
      
    - Export fed file
    
- Configure environment variable (docker-compose / kubernetes deployment)

```yaml
 volumes:
   - /Users/rnatarajan/APIM/apigw-emt-scripts-2.1.0-SNAPSHOT/certs/dss/p12:/opt/Axway/apigateway/groups/certs/
 # docker-compose.yaml example
 environment:
      EMT_TOPOLOGY_LOG_ENABLED: 'true'
      EMT_TOPOLOGY_LOG_DEST: 3
      certandkey_sslport: /opt/Axway/apigateway/domain.p12
      certandkeypassword_sslport: changeme
      certandkeymtls_sslport: 'true'
```
    
- comment lines related to certificate generation in apigw-emt-scripts-2.1.0-SNAPSHOT/Dockerfiles/emt-nodemanager/scripts/setup_emt_nodemanager.py
```python
  try:
            # self._generateTopologyCert(nmHandler)
            # self._storeCertsInEntityStore(nmHandler)

            localNodeManager, topology, topologyParams = self._createTopologyJson()
            # print("Enabling SSL on management interface")
            # nmHandler.enableSSLInterface(True, TopologyCertificate.CERT_ALIAS, topologyParams)
            # self._updateConfigFiles(localNodeManager, topology)

            # Delete the cert generation temp directory
            shutil.rmtree(nmHandler.tempCertPath)

        except Exception, e:
            _fail("Error generating topology cert: %s" % e)
```
- Build Admin Node Manger Image

```bash
./build_anm_image.py --default-cert --default-user --parent-image=apigw-base --merge-dir=/Users/rnatarajan/APIM/apigw-emt-scripts-2.1.0-SNAPSHOT/apigateway --fed extanm.fed --out-image=admin-node-manager-ext-ca-env:latest
```
**param default-cert is not used, but it is a mandatory argument for building anm image**

## Configure Gateway

- Create a CSR file

```bash
./gen_domain_cert.py --domain-id=dssgateway --out=csr --O=Axway --OU=DSS --C=US --ST=AZ --L=Scottsdale --pass-file=rootcerts/pass.txt
```
command creates a folder named dssgateway under apigw-emt-scripts-2.1.0-SNAPSHOT/certs/ with following files

    - dssgateway.csr
    - dssgateway-key.pem
- Copy CA.pem, CA.key, CA.srl and openssl files from dss folder to dssgateway folder

```bash
dssgateway$cp ../dss/CA.pem .
dssgateway$cp ../dss/CA.key .
dssgateway$cp ../dss/CA.srl .
dssgateway$cp ../dss/openssl.cnf .
```
- Sign dss.csr with CA certificate and key using openssl configuration

```bash
openssl x509 -req -days 360 -in dssgateway.csr -CA CA.pem -CAkey CA.key -CAcreateserial -out signedbygatewayCA.crt -sha256 -extensions gateway_extensions -extfile openssl.cnf
```

command creates a file named signedbygatewayCA.crt

- Create p12 file **without password** if policy project is not protected with password

```bash
openssl pkcs12 -export -in signedbygatewayCA.crt -inkey dssgateway-key.pem -out topology.p12 -chain -CAfile CA.pem -name 'topology-cert' -passout pass:
```

- Create p12 file  if policy project is not protected with password

```bash
openssl pkcs12 -export -in signedbygatewayCA.crt -inkey dssgateway-key.pem -out topology.p12 -chain -CAfile CA.pem -name 'topology-cert' -passout pass:changeme
```

- Prepare API Gateway fed file

    - Import loadable module
      Policystudio using File -> Import -> Import Custom filters -> select apim-policy-password-cert-env/src/main/resources/typeSet.xml.

    - Export fed file

- Configure environment variable (docker-compose / kubernetes deployment)

```yaml
 # docker-compose.yaml example
 # Mandatory 
 volumes:
   - /Users/rnatarajan/APIM/apigw-emt-scripts-2.1.0-SNAPSHOT/certs/dssgateway/p12:/opt/Axway/apigateway/groups/certs/
 environment:
      EMT_ANM_HOSTS: nodemgr:8090
      CASS0: host.docker.internal
      CASS_HOST: host.docker.internal
      CASS_USER: dba
      CASS_PASSWORD: super
      CASS_KEYSPACE: axwayapim
      # We should use same path
      gatewaytoplogycertandkey_domain: /opt/Axway/apigateway/groups/certs/topology.p12
      gatewaytoplogycertandkeypassword_domain: ''
```

- comment lines related to certificate generation in apigw-emt-scripts-2.1.0-SNAPSHOT/Dockerfiles/emt-gateway/scripts/setup_emt_instance.py

```python
def _setup():
    _mergePolAndEnvToFed()
    _installCustomFedFile()
    _setupApiManager()
    _createInstanceDirStructure()
    _customizeInstallation()
    _checkLicense()

    # ch = CertHandler()
    # ch.generateCert()
    # ch.enableSSLInterface()
```

- Build API Gateway Image
```bash
./build_gw_image.py  --default-cert --license=/Users/rnatarajan/APIM/apigw-emt-scripts-2.1.0-SNAPSHOT/licenses/apim.lic --parent-image=apigw-base --merge-dir=/Users/rnatarajan/APIM/apigw-emt-scripts-2.1.0-SNAPSHOT/apigateway --fed=container_env.fed --out-image=apim-cert-ca-env:latest
```
