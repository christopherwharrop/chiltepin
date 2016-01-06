#!/bin/sh

# Generate a key password
keypass=`cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 64 | head -1`

# Generate a key store password
storepass=`cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 64 | head -1`

# Generate a trust store password
trustpass=`cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 64 | head -1`

# Create config directory for the keys
securitydir=~/.chiltepin/etc/security
rm -rf $securitydir
mkdir -p $securitydir
chmod 700 $securitydir

# Generate a keystore
keytool -genkeypair -alias chiltepin -keyalg RSA -validity 1500 -keystore $securitydir/keystore -keypass $keypass -storepass $storepass -dname "CN=Chiltepin"
chmod 600 $securitydir/keystore
echo $keypass > $securitydir/keypass; chmod 600 $securitydir/keypass
echo $storepass > $securitydir/storepass; chmod 600 $securitydir/storepass

# Export certificate from the keystore
keytool -export -alias chiltepin -keystore $securitydir/keystore -storepass $storepass -rfc -file $securitydir/chiltepin.cer
chmod 600 $securitydir/chiltepin.cer

# Import certificate into truststore
echo Y | keytool -import -alias chiltepin -file $securitydir/chiltepin.cer -keystore $securitydir/truststore -storepass $trustpass
chmod 600 $securitydir/truststore
rm -f $securitydir/chiltepin.cer
echo $trustpass > $securitydir/trustpass; chmod 600 $securitydir/trustpass
