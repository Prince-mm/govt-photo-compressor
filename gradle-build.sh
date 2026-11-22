#!/bin/bash
# Wrapper: refreshes the rotating proxy password, then runs Gradle.
# The sandbox egress proxy password rotates; static credentials go stale.
set -e
cd "$(dirname "$0")"
python3 - <<'EOF'
import os, re
p = os.environ.get('https_proxy', '') or os.environ.get('HTTPS_PROXY', '')
m = re.match(r'http://([^:]+):([^@]+)@([^:]+):(\d+)', p)
if not m:
    raise SystemExit("no proxy in env")
user, pwd, host, port = m.groups()
with open('/home/hatch/workspace/.gradle-home/gradle.properties', 'w') as f:
    f.write(f"""systemProp.http.proxyHost={host}
systemProp.http.proxyPort={port}
systemProp.https.proxyHost={host}
systemProp.https.proxyPort={port}
systemProp.http.proxyUser={user}
systemProp.http.proxyPassword={pwd}
systemProp.https.proxyUser={user}
systemProp.https.proxyPassword={pwd}
systemProp.http.nonProxyHosts=localhost|127.0.0.1
systemProp.https.nonProxyHosts=localhost|127.0.0.1
systemProp.java.net.preferIPv4Stack=true
""")
print("proxy credentials refreshed")
EOF
chmod 600 /home/hatch/workspace/.gradle-home/gradle.properties
export JAVA_HOME=$HOME/workspace/jdk-17.0.12+7
export GRADLE_USER_HOME=$HOME/workspace/.gradle-home
export PATH=$JAVA_HOME/bin:$HOME/workspace/gradle-8.7/bin:$PATH
export JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true"
gradle --stop > /dev/null 2>&1 || true
exec gradle "$@"
