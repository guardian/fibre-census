#!/bin/bash -e

cp profile_reader.pl /usr/local/bin
cp fibrecensus-launcher.plist /Library/LaunchDaemons/com.gu.fibrecensus-launcher.plist

cd /Library/LaunchDaemons
launchctl load com.gu.fibrecensus-launcher.plist
launchctl start com.gu.fibrecensus-launcher.plist