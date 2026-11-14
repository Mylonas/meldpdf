#!/usr/bin/env bash
# Fails the job unless the on-device tests really ran and all passed.
# Needed because a failed APK install can leave `connectedDebugAndroidTest`
# reporting BUILD SUCCESSFUL with zero tests executed.
set -uo pipefail
DIR=app/build/outputs/androidTest-results
python3 - "$DIR" <<'PY'
import glob, sys, xml.etree.ElementTree as ET
files = glob.glob(sys.argv[1] + "/**/*.xml", recursive=True)
tests = fails = 0
for f in files:
    try:
        root = ET.parse(f).getroot()
    except ET.ParseError:
        continue
    for s in ([root] if root.tag == "testsuite" else root.iter("testsuite")):
        tests += int(s.get("tests", 0))
        fails += int(s.get("failures", 0)) + int(s.get("errors", 0))
print(f"on-device tests: {tests} run, {fails} failed, {len(files)} result file(s)")
if tests == 0:
    print("::error::no on-device tests ran (did the APK install fail?)"); sys.exit(1)
if fails:
    print("::error::on-device tests failed"); sys.exit(1)
PY
