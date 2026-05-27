#!/bin/sh
set -e

# prepare
mkdir windows_installer
cd windows_installer

unzip ../dist/tinyMediaManager*-windows-*.zip
cd tinyMediaManager
cp ../../AppBundler/installer.iss .

VERSION_T=$(grep 'human.version' version | cut -d'=' -f2)
VERSION_N=$(echo "$VERSION_T" | sed "s/[-].*//")
touch .userdir

# build
iscc installer.iss "/DMyAppVersionText=$VERSION_T" "/DMyAppVersionNum=$VERSION_N"

# sign
# 1. get temp. access token via from azure
AZURE_ACCESS_TOKEN=$(curl -s -X POST "https://login.microsoftonline.com/${AZURE_TENANT_ID}/oauth2/v2.0/token" \
  -d "client_id=${AZURE_CLIENT_ID}" \
  -d "scope=https://codesigning.azure.net/.default" \
  -d "client_secret=${AZURE_CLIENT_SECRET}" \
  -d "grant_type=client_credentials" | jq -r .access_token)

#2. sign the installer with jsign
java -jar ../../AppBundler/jsign.jar \
  --storetype TRUSTEDSIGNING \
  --keystore "${AZURE_KEYSTORE}" \
  --storepass "${AZURE_ACCESS_TOKEN}" \
  --alias "${AZURE_ALIAS}" \
  --tsaurl http://timestamp.acs.microsoft.com/ \
  --tsmode RFC3161 \
  "Output/tinyMediaManager-$VERSION_T-Setup.exe"

mv Output/tinyMediaManager-$VERSION_T-Setup.exe ../../dist/tinyMediaManager-$VERSION_T-Setup.exe

# cleanup
cd ..
cd ..
rm -rf windows_installer
