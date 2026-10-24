#!/usr/bin/env python3
"""Counts the device tests that ran, per project, from the JUnit results.

Shows each count as a note on the run, and fails if a project ran none, so a
run that found no tests can never pass quietly.
"""
import glob
import sys
import xml.etree.ElementTree as ElementTree

android = sys.argv[1]
ok = True
for project in ["core", "androidApp"]:
    tests = failures = 0
    for path in glob.glob(f"{project}/build/outputs/androidTest-results/**/*.xml", recursive=True):
        root = ElementTree.parse(path).getroot()
        for suite in [root] if root.tag == "testsuite" else root.iter("testsuite"):
            tests += int(suite.get("tests", 0))
            failures += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
    print(f"::notice title={project} on Android {android}::{tests} device tests ran, {failures} failed")
    if tests == 0:
        print(f"::error title=No device tests::No device tests ran for {project} on Android {android}")
        ok = False
sys.exit(0 if ok else 1)
