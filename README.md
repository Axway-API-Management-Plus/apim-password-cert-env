#APIM Environmentalization 

## Build the project

- Edit the pom.xml system property and jar version

System property
```xml
<apim.lib.path>D:\Axway\Axway-7.7.0\apigateway\system\lib</apim.lib.path>
```    
Check each dependency and change the jar version: For example the version of jar apigw-common-7.7.0-4.jar might changes in each release
```xml
    <dependency>
      <groupId>apigw-common</groupId>
      <artifactId>apigw-common</artifactId>
      <scope>system</scope>
      <version>${api.version}</version>
      <systemPath>${apim.lib.path}\plugins\apigw-common-7.7.0-4.jar</systemPath>
    </dependency>
```

- Build with maven
```bash
$mvn clean install
```

## Add Loadable Module to API Gateway 

- Copy the apim-env-module-x.x.jar from project target folder to gateways instance folder $INSTALLDIR/apigateway/groups/{groupname}/{instancename}/ext/lib

- Add Loadable module to running gateway using publish script.

- Parameters of publish command
```bash
Options:
  -h, --help            show this help message and exit
  -g GROUP, --group=GROUP
                        API Server Group
  -n SERVICE, --service=SERVICE
                        API Server
  -u USER, --username=USER
                        Username
  -p PASS, --password=PASS
                        Password
  -d URL, --url=URL     Traffic monitor URL for API
  -i TSLOC, --typeset=TSLOC
                        TypeSet file location
  -s STORETYPE, --storetype=STORETYPE
                        Store type
  -t TYPENAME, --entitytype=TYPENAME
                        Entity type name
```
- Example command 

```bash
$cd $INSTALLDIR/apigateway/samples/scripts
$./run.sh publish/publish.py -i /home/axway/apim-policy-password-cert-env/src/main/resources/typeSet.xml -t ExternalConfigLoader -g test -n server1
```
The above script connects to local Node manager and deploys the new LoadableModule. If  Node manager is running on some other machine, add url. Also, the username and password is hardcoded to default values, use the username and password parameters to provide new value. 

## Classic APIM
Use export command to add environment variables 
- Example
```bash
export cert_domain=`cat cert.pem`
```

## EMT APIM
Add environment variables to deployment yaml

```yaml
 env:
        - name: EMT_ANM_HOSTS
          value: anm-int:8090
        - name: cert_domain
          value: "-----BEGIN CERTIFICATE-----
MIIDRjCCAi6gAwIBAgIGAW5HwjW8MA0GCSqGSIb3DQEBCwUAMBExDzANBgNVBAMM
BkRvbWFpbjAgFw0xOTEwMzEyMTI1NDFaGA8yMTE5MTAxNDIxMjU0MVowQjEWMBQG
CgmSJomT8ixkARkWBmhvc3QtMTEQMA4GA1UECwwHZ3JvdXAtMTEWMBQGA1UEAwwN
bm9kZW1hbmFnZXItMTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAL8V
Oqt5OKndTAlSHY1/LATaAdvUUPRrRvyh/BfBGWueQKoG2AQAUA5dN1B1MvPzPaaL
FFYgfrckdmG47MFwkpyFgchl7IVkMhvJYy0Ku+aCoT0Gou9dkEKr9A5W9ZHzuWQM
YRCSfIZqRednP9qFRTma185+jj7EaGiPuglkk8nplNeCbxhMBfGPewEuTBDPIOMw
Ep7ChaRd07/mwmfKCjwh2C910wOg1qH+MEC+yjC3BwaNINAZtHd0lzJRji8Fjtrc
DzTVZf0MF3E8QhW0x1kS/53BQCm6YMxjxUEgorDWrzrmyyanlICsBIASMtMWQQug
P6qfEvj8WLH9VcGSQlMCAwEAAaNxMG8wCQYDVR0TBAIwADALBgNVHQ8EBAMCA7gw
OwYDVR0lBDQwMgYIKwYBBQUHAwEGCCsGAQUFBwMCBg0rBgEEAYGMTgoBAQIBBg0r
BgEEAYGMTgoBAQICMBgGA1UdEQQRMA+CB2FwaS1lbnaHBAqBPDkwDQYJKoZIhvcN
AQELBQADggEBAFfGAtf5Rdn3EkPTsT5CcUo2+kgT3Er9y3D+SeyraM3UcwqR0+gb
JHeLD6xnnkxbDIEr8ZvTL5BNqZad7Iu3mS7QVK7cBi9nHmr7HSzapD6ODli8whtn
daElSKsO9EPAB04rVLIFZ5NIfWHLTDJSyFdvC5JFPuYxWluQwN+KOFJMjs7zVGvm
MXO6WwSd0Q4+NlqgnvRl6viuo14M6Qu9TsidkZhdE+AIRPveYZm9J0FzanYOAoDf
ZGIu5manaCW4XJKyZU/Kp04JR6ojQai65R/OLaFOxQhdZ9rtIN1DAsyTBp/6tqqC
s2+QnHEKNi5n6eyF81l1X3AGOMp2uUF4CfU=
-----END CERTIFICATE-----"
```

[More Details](index.md)