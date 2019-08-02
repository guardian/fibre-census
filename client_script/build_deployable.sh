#!/bin/bash -e

if [ "${BUILD_NUM}" == "" ]; then
    BUILD_NUM="DEV"
fi

echo building number ${BUILD_NUM} on ${BUILD_BRANCH}

zip fibrecensus-client-${BUILD_NUM}.zip profile_reader.pl fibrecensus-launcher.plist install.sh
shasum -a 256 fibrecensus-client-${BUILD_NUM}.zip > fibrecensus-client-${BUILD_NUM}.sha

aws s3 cp fibrecensus-client-${BUILD_NUM}.zip s3://multimedia-public-downloadables/fibrecensus/${BUILD_NUM}/fibrecensus-client-${BUILD_NUM}.zip
aws s3 cp fibrecensus-client-${BUILD_NUM}.sha s3://multimedia-public-downloadables/fibrecensus/${BUILD_NUM}/fibrecensus-client-${BUILD_NUM}.sha

if [ "${BUILD_BRANCH}" == "master" ]; then
    aws s3 cp fibrecensus-client-${BUILD_NUM}.zip s3://multimedia-public-downloadables/fibrecensus/master/fibrecensus-client.zip
    aws s3 cp fibrecensus-client-${BUILD_NUM}.sha s3://multimedia-public-downloadables/fibrecensus/master/fibrecensus-client.sha

fi