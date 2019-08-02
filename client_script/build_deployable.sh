#!/bin/bash -e

cd "$( dirname "${BASH_SOURCE[0]}" )"   #make sure we are in the client_script dir

if [ -x $(which shasum) ]; then
    SHACMD="shasum -a 256"
else
    SHACMD="sha256sum"
fi

if [ "${BUILD_NUM}" == "" ]; then
    BUILD_NUM="DEV"
fi

echo building number ${BUILD_NUM} on ${BUILD_BRANCH}

zip fibrecensus-client-${BUILD_NUM}.zip profile_reader.pl fibrecensus-launcher.plist install.sh
$SHACMD fibrecensus-client-${BUILD_NUM}.zip > fibrecensus-client-${BUILD_NUM}.sha

aws s3 cp fibrecensus-client-${BUILD_NUM}.zip s3://multimedia-public-downloadables/fibrecensus/${BUILD_NUM}/fibrecensus-client-${BUILD_NUM}.zip
aws s3 cp fibrecensus-client-${BUILD_NUM}.sha s3://multimedia-public-downloadables/fibrecensus/${BUILD_NUM}/fibrecensus-client-${BUILD_NUM}.sha

if [ "${BUILD_BRANCH}" == "master" ]; then
    aws s3 cp fibrecensus-client-${BUILD_NUM}.zip s3://multimedia-public-downloadables/fibrecensus/master/fibrecensus-client.zip
    aws s3 cp fibrecensus-client-${BUILD_NUM}.sha s3://multimedia-public-downloadables/fibrecensus/master/fibrecensus-client.sha
fi