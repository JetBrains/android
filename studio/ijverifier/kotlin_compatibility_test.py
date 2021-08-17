"""Runs the IntelliJ Plugin Verifier on Kotlin and reports any issues found"""

import argparse
import os
import subprocess
import shutil
import sys


tmpdir = os.getenv("TEST_TMPDIR")
outdir = os.getenv("TEST_UNDECLARED_OUTPUTS_DIR")


def fail(msg):
  print("ERROR:", msg)
  sys.exit(1)


def run_verifier(verifier, ide, jre, ignored_problems, report_dir):
  verifier_home = os.path.join(tmpdir, "plugin-verifier-home")
  log = os.path.join(outdir, "plugin-verifier-log.txt")

  # See https://github.com/JetBrains/intellij-plugin-verifier#check-plugin.
  cmd = [
    verifier,
    "--jvm_flag=-Dplugin.verifier.home.dir=%s" % verifier_home,
    "check-plugin",
    "path:%s/plugins/Kotlin" % ide,
    ide,
    "-runtime-dir", jre,
    "-ignored-problems", ignored_problems,
    "-verification-reports-dir", report_dir,
    "-offline",
  ]

  print("Running the verifier and logging to %s" % log, flush=True)
  with open(log, 'w') as logfile:
    result = subprocess.run(cmd, stdout=logfile)
    if result.returncode != 0:
      fail("The IntelliJ Plugin Verifier returned a non-zero exit code")


def check_verifier_result(report_dir):
  verdict = None

  # Note: the report directory layout is specified at
  # https://github.com/JetBrains/intellij-plugin-verifier#results.
  for dirpath, _, filenames in os.walk(report_dir):
    for name in filenames:
      path = os.path.join(dirpath, name)
      short_path = os.path.relpath(path, report_dir)
      print("Found report file at", short_path)

      if name == "verification-verdict.txt":
        if verdict is not None:
          fail("Expected only one verdict file")
        with open(path) as verdict_file:
          verdict = verdict_file.read().strip()

      if name in ["invalid-plugin.txt", "compatibility-problems.txt"]:
        with open(path) as error_file:
          print("\nERRORS:\n\n%s\n" % error_file.read().strip())
        fail("Found problems in %s (see log)" % short_path)

  if verdict is None:
    fail("Failed to find verification-verdict.txt")

  print("Verdict:", verdict)


if __name__ == '__main__':
  parser = argparse.ArgumentParser()
  parser.add_argument("--verifier", required=True)
  parser.add_argument("--ide_zip", required=True)
  parser.add_argument("--java_runtime", required=True)
  parser.add_argument("--ignored_problems", required=True)
  args = parser.parse_args()

  print("Unzipping IDE from %s" % args.ide_zip)
  ide_unzipped = os.path.join(tmpdir, "ide_unzipped")
  shutil.unpack_archive(args.ide_zip, ide_unzipped)

  report_dir = os.path.join(outdir, "plugin-verifier-reports")
  run_verifier(
    verifier = args.verifier,
    ide = ide_unzipped,
    jre = args.java_runtime,
    ignored_problems = args.ignored_problems,
    report_dir = report_dir,
  )

  check_verifier_result(report_dir)
