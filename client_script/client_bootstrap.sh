#!/bin/bash

#Downloads and installs the client-side agent. This should be run as root, and assumes that curl is installed.

if [ "$1" == "" ]; then
    echo You should specify the server to target as the first argument, i.e. ./client_bootstrap.sh https://my-server.company.co.uk
    exit 1
fi

mkdir -p /tmp/fibrecensus-client
cd /tmp/fibrecensus-client
curl https://multimedia-public-downloadables.s3-eu-west-1.amazonaws.com/fibrecensus/master/fibrecensus-client.zip > fibrecensus-client.zip
unzip fibrecensus-client.zip

mv fibrecensus-launcher.plist fibrecensus-launcher.plist.old
cat fibrecensus-launcher.plist.old | sed s?https://server-uri-here?$1? > fibrecensus-launcher.plist

bash ./install.sh

cd
rm -rf /tmp/fibrecensus-client
