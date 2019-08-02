#!/bin/bash

cp profile_reader.pl /usr/local/bin
cp fibrecensus-launcher.plist /Library/LaunchDaemons/com.gu.fibrecensus-launcher.plist

cd /Library/LaunchDaemons
launchctl unload com.gu.fibrecensus-launcher.plist  2>/dev/null #in case it hass already been started. Silence error to avoid confusion..
launchctl load com.gu.fibrecensus-launcher.plist
launchctl start com.gu.fibrecensus-launcher.plist
