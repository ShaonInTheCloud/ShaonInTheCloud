#!/usr/bin/env python3
"""Run optional owned-account UI checks on an already installed disposable emulator.

Install a matching playDebug target/test APK pair from the same build first.
Credentials enter app-private storage through stdin, never adb command arguments.
No screenshots or UI trees containing credentials are collected.
"""
import argparse
import json
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--credentials', type=Path, required=True)
args = parser.parse_args()
if not args.serial.startswith('emulator-'):
    parser.error('Only a disposable emulator is supported')
adb = ['adb','-s',args.serial]
def run(parts, **kwargs):
    return subprocess.run(adb+parts,check=True,capture_output=True,timeout=300,**kwargs)

config=json.loads(args.credentials.read_text())
if config.get('consent')!='72-hour-disposable-test' or config.get('plan') not in ['monthly','quarterly','annual']:
    parser.error('Explicit disposable trial consent and plan required')
if run(['shell','getprop','ro.kernel.qemu']).stdout.strip()!=b'1':
    parser.error('Target is not an emulator')
pkg='com.safenest.app'
run(['shell','run-as',pkg,'mkdir','-p','files'])
try:
    run(['exec-in','run-as',pkg,'dd','of=files/trial-qa.json'],input=json.dumps(config).encode())
    output=run(['shell','am','instrument','-w','-r','-e','class',
      'com.safenest.app.TrialDeviceAcceptanceTest#ownedLiveTrialConsentActivationAndAcceleratedDeviceCleanup',
      'com.safenest.app.test/androidx.test.runner.AndroidJUnitRunner']).stdout.decode(errors='replace')
    # Keep raw instrumentation output private: a platform failure may contain UI
    # entry semantics even though our test suppresses its own original exception.
    if 'OK (1 test)' not in output or 'FAILURES' in output or 'INSTRUMENTATION_FAILED' in output:
        raise RuntimeError('Device check failed; private instrumentation output suppressed')
    report=run(['exec-out','run-as',pkg,'cat','files/trial-device-result.json']).stdout
    parsed=json.loads(report)
    if parsed.get('live_login_consent_start_activation') is not True or parsed.get('accelerated_device_cleanup') is not True:
        raise RuntimeError('Live device report missing; skipped tests never pass acceptance')
    print(json.dumps(parsed,indent=2))
finally:
    run(['shell','run-as',pkg,'rm','-f','files/trial-qa.json'])
