import atexit
import logging
import os
import signal
import subprocess
import sys
import time

# Configure standard logging to stderr
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stderr)]
)

# Global variable to hold the subprocess information
gemini_process = None


def cleanup() -> None:
  """Ensures the Gemini process is terminated."""
  global gemini_process
  if gemini_process and gemini_process.poll() is None:
    logging.info("Terminating Gemini process with SIGKILL...")
    try:
      pgrp = os.getpgid(gemini_process.pid)
      os.killpg(pgrp, signal.SIGKILL)
    except ProcessLookupError:
      # Process or process group may have already terminated.
      logging.info("Gemini process group already terminated.")
    gemini_process.wait()  # Wait for the main process to be reaped.
    logging.info("Gemini process terminated.")


atexit.register(cleanup)


def run_gemini_loop() -> None:
  """Runs the gemini CLI command in a loop, displaying all output."""
  global gemini_process
  prompt_path = "tools/adt/idea/agent/prompts/android-studio-autodev.md"
  command = f"cat {prompt_path} | gemini --yolo --model=gemini-3-flash-preview"
  resume_command = (
      f"cat {prompt_path} | gemini --yolo --model=gemini-3-flash-preview"
      " --resume"
  )

  logging.info("Starting Gemini loop. Press Ctrl+C to stop.")

  while True:
    logging.info("--- Invoking Gemini ---")
    gemini_process = subprocess.Popen(
          command, shell=True, preexec_fn=os.setsid
      )
    gemini_process.wait()

    while gemini_process.returncode != 0:
      logging.info(
            "Gemini exited with non-zero return code:"
            f" {gemini_process.returncode}. Resuming session..."
        )
      gemini_process = subprocess.Popen(
            resume_command, shell=True, preexec_fn=os.setsid
        )
      gemini_process.wait()

    logging.info("--- Iteration complete. Waiting 5 seconds ---")
    time.sleep(5)


def _handle_signal(signum, frame) -> None:
  """Handles termination signals to ensure cleanup is called."""
  logging.info(f"Received signal {signum}, performing cleanup.")
  # We call sys.exit() to allow the atexit-registered cleanup to run.
  sys.exit(1)


def main() -> None:
  """Main function to start the Gemini loop."""
  # Register signal handlers for graceful shutdown.
  signal.signal(signal.SIGTERM, _handle_signal)
  signal.signal(signal.SIGINT, _handle_signal)

  run_gemini_loop()

if __name__ == "__main__":
    main()
