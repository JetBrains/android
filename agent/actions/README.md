# Studio Auto-Dev on Linux (Cloudtop)

## One-time setup

1. Install Gemini CLI for Cloudtop: go/gemini-cli-install#install-corp-cloudtop

2. Get a GEMINI_KEY and export it to your environment: go/aistudio-apikey

### Installing MCP servers (Linux):

1. Install P&D SWE MCP for Android (which includes MCP servers for Gerrit, Buganizer, etc...):
   ```shell
   /google/src/head/depot/google3/pnd/swe_agents/mcp/quickstart/gemini_cli_setup.sh --codebase=android
   ```

2. Alternatively, install Buganizer directly.
   ```shell
   gemini mcp add --scope=user onemcp-server /google/bin/releases/corp-mcp-proxy/server.par --mcp_server=blade:devtools.buganizer.mcpservice-prod --use_loas_transact_dat=True
   ```

### Installing skills

1. `gemini skills install <YOUR_STUDIO_MAIN_DIRECTORY>/tools/adt/idea/agent/skills/android-studio-development`

2. `gemini skills enable android-studio-development`

3. `cd <YOUR_STUDIO_MAIN_DIRECTORY> && gemini` and then "Trust" the folder. You
    may then quit out of Gemini CLI.

## Running Studio Auto-Dev

Warning: This runs Gemini CLI with `yolo` mode which gives it auto-approved tool usage in your working directory. It is recommended to run Auto-Dev in `studio-main` or a subdirectory of `studio-main`.

1. `cd <YOUR_STUDIO_MAIN_DIRECTORY>`

2. `python tools/adt/idea/agent/actions/android-studio-autodev.py` (with Python 3)
