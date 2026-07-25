#!/bin/bash
if [ -f "app/build/outputs/apk/release/app-release.apk" ]; then
    APK_PATH="app/build/outputs/apk/release/app-release.apk"
elif [ -f ".build-outputs/app-release.apk" ]; then
    APK_PATH=".build-outputs/app-release.apk"
else
    APK_PATH="/app/applet/app/build/outputs/apk/release/app-release.apk"
fi

echo "Fetching GoFile server..."
SERVER=$(python3 -c '
import urllib.request, json, ssl
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
req = urllib.request.Request("https://api.gofile.io/servers", headers={"User-Agent": "Mozilla/5.0"})
try:
    with urllib.request.urlopen(req, context=ctx) as r:
        data = json.loads(r.read().decode())
        print(data["data"]["servers"][0]["name"])
except Exception as e:
    import sys
    print("error", file=sys.stderr)
')

if [ -z "$SERVER" ] || [ "$SERVER" == "error" ]; then
    echo "Failed to get GoFile server"
    exit 1
fi

echo "Uploading to $SERVER..."
RESULT=$(curl -s -F file=@"$APK_PATH" "https://$SERVER.gofile.io/contents/uploadfile")

echo "Result JSON: $RESULT"

DOWNLOAD_LINK=$(python3 -c '
import sys, json
try:
    data = json.loads(sys.argv[1])
    print(data["data"]["downloadPage"])
except Exception as e:
    print("error")
' "$RESULT")

if [ "$DOWNLOAD_LINK" == "error" ] || [ -z "$DOWNLOAD_LINK" ]; then
    echo "Failed to extract download link"
    exit 1
fi

echo "DOWNLOAD_LINK:$DOWNLOAD_LINK"
