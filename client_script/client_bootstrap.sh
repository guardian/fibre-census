#!/bin/bash

#Downloads and installs the client-side agent. This should be run as root, and assumes that curl is installed.

mkdir -p /tmp/fibrecensus-client
cd /tmp/fibrecensus-client
curl https://multimedia-public-downloadables.s3-eu-west-1.amazonaws.com/fibrecensus/master/fibrecensus-client.zip > fibrecensus-client.zip
unzip fibrecensus-client.zip

bash ./install.sh

cd
rm -rf /tmp/fibrecensus-client
