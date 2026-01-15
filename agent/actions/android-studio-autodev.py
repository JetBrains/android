import subprocess
import time
import sys
import logging

# Configure standard logging to stderr
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stderr)]
)

def run_gemini_loop() -> None:
    """Runs the gemini CLI command in a loop, displaying all output."""
    prompt_path = "tools/adt/idea/agent/prompts/android-studio-autodev.md"

    # Construct the shell command.
    # We use a single string because shell=True handles the pipe logic.
    command = f"cat {prompt_path} | gemini -s --yolo --model=gemini-3-flash-preview"

    logging.info("Starting Gemini loop. Press Ctrl+C to stop.")

    try:
        while True:
            logging.info("--- Invoking Gemini ---")

            # By default, subprocess.run sends stdout and stderr
            # directly to the parent process's (this script's) output.
            result = subprocess.run(command, shell=True)

            while result.returncode != 0:
                logging.info(f"Gemini exited with non-zero return code: {result.returncode}. Resuming session...")
                result = subprocess.run(f"cat {prompt_path} | gemini -s --yolo --model=gemini-3-flash-preview --resume", shell=True)

            logging.info("--- Iteration complete. Waiting 5 seconds ---")
            time.sleep(5)

    except KeyboardInterrupt:
        logging.info("Loop interrupted by user. Exiting.")
        sys.exit(0)

def main() -> None:
    run_gemini_loop()

if __name__ == "__main__":
    main()
