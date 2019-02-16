# jSMTP [![Build Status](https://travis-ci.org/hell-sh/jSMTP.svg?branch=master)](https://travis-ci.org/hell-sh/jSMTP)

A **good** Java implementation of SMTP and extended SMTP.

## Maven

jSMTP is using slf4j, so Maven is the best way to include jSMTP:

    <repositories>
        <repository>
            <id>hellsh</id>
            <url>https://mvn2.hell.sh</url>
        </repository>
    </repositories>
    <dependencies>
        <dependency>
            <groupId>sh.hell</groupId>
            <artifactId>jsmtp</artifactId>
            <version>0.2.0</version>
        </dependency>
    </dependencies>

## Certificates

Using the power of openssl & keytools, you can convert a certificate.crt + private.key into a .jsmtp_keystore using password 123456 which you can then use as follows:

    new SMTPServer(mySMTPEventHandler, ".jsmtp_keystore", "123456");
