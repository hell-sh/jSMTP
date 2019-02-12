@echo off

REM Converts certificate.crt + private.key files into .jsmtp_keystore with password 123456 so the SMTPServer can present the certificate.

openssl pkcs12 -export -in certificate.crt -inkey private.key -out jsmtp.p12 -name mykey -CAfile ca_bundle.crt -caname root -password pass:123456
keytool -importkeystore -deststorepass 123456 -destkeypass 123456 -destkeystore .jsmtp_keystore -srckeystore jsmtp.p12 -srcstoretype PKCS12 -srcstorepass 123456 -alias mykey
del jsmtp.p12

echo Press any key to close.
pause > NUL
