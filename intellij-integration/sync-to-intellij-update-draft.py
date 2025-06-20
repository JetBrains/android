#!/usr/bin/env python3
from pathlib import Path
import json
import os
import shlex
import subprocess
import sys
import tempfile


# Background: while iterating on IntelliJ Platform updates, we occasionally
# upload work in progress as a Gerrit topic so others can try it out.
# We also upload a pinned repo manifest to ensure everyone sees the same state.
# The following script finds the latest copy of this pinned manifest and syncs
# to it temporarily using 'repo sync -m manifest.xml'.
def main():
    # Any passed args are forwarded to repo sync.
    args = sys.argv[1:]
    if '-h' in args or '--help' in args:
        sys.exit(
            'This script downloads the latest IntelliJ update draft topic from Gerrit.\n'
            'Any passed arguments are forwarded to repo sync.'
        )

    # Move to the repo root.
    os.chdir(find_repo_root(Path(os.getcwd())))

    # Download the pinned repo manifest.
    revision = find_latest_manifest_revision()
    run('git', '-C', '.repo/manifests', 'fetch', 'origin', revision)
    manifest_content = run('git', '-C', '.repo/manifests', 'show', f'{revision}:default.xml')

    # Sync.
    with tempfile.NamedTemporaryFile(suffix='-intellij-update-draft-manifest.xml') as manifest:
        manifest.write(manifest_content.encode())
        sync_cmd = ['repo', 'sync', '--detach', '-m', manifest.name, *args]
        print('Running:', shlex.join(sync_cmd))
        if subprocess.run(sync_cmd).returncode != 0:
            sys.exit('ERROR: repo sync failed')

    print()
    print('Done. To return to studio-main, just run repo sync.')
    print()


# Uses the Gerrit REST API to get the latest manifest revision for the IntelliJ update topic.
# The Change-Id is hard-coded to match the one in push-intellij-update-draft.py.
def find_latest_manifest_revision():
    # See http://ag/Documentation/rest-api-changes.html.
    endpoint = 'https://googleplex-android-review.git.corp.google.com/changes/'
    query = 'q=project:platform/manifest+branch:studio-main+change:I75eb93541&o=CURRENT_REVISION'
    response = run('gob-curl', f'{endpoint}?{query}')
    response = response.removeprefix(")]}'") # Remove XSSI prefix.
    info = json.loads(response)
    if len(info) != 1:
        sys.exit(f'ERROR: expected exactly one matching Gerrit change (found {len(info)})')
    return info[0]['current_revision']


def find_repo_root(cd: Path) -> Path:
    while cd != cd.root:
        if cd.joinpath('.repo').is_dir():
            return cd
        cd = cd.parent
    sys.exit('ERROR: failed to find repo root')


# Like subprocess.check_output() but with logging.
def run(*args: str):
    print('Running:', shlex.join(args))
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if result.returncode != 0:
        sys.exit(f'ERROR: command failed\n{result.stdout}\n{result.stderr}')
    return result.stdout


if __name__ == '__main__':
    main()
