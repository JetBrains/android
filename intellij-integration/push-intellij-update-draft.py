#!/usr/bin/env python3
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from subprocess import DEVNULL, PIPE
from typing import NoReturn
import argparse
import os
import re
import subprocess
import xml.etree.ElementTree as ET


DRAFT_CHANGE_ID = 'Change-Id: I75eb93541a6ff66ad00a738bc490185210b23601'

COMMIT_MSG = '\n'.join([
    '[do not submit] IntelliJ update draft',
    '',
    'This is a synthetic commit squashing together all changes needed to',
    'integrate the next IntelliJ release (so far). The purpose is to make',
    'it easier to share work-in-progress without polluting Gerrit with',
    'dozens of incomplete or temporary commits.',
    '',
    'It is OK to develop new commits on top of this one and upload to',
    'studio-main as usual. Eventually your commits will be added to the',
    '"real" IntelliJ update topic that we submit to studio-main.',
    '',
    'Bug: n/a',
    'Test: n/a',
    DRAFT_CHANGE_ID,
])

MANIFEST_DIR = Path('.repo/manifests')


def main():
    parser = argparse.ArgumentParser(description='Push local work to the IntelliJ update draft branch')
    parser.add_argument('branch', help='which local branch to push')
    parser.add_argument('-f', '--push', action='store_true', help='push to remotes')
    args = parser.parse_args()
    if not args.push:
        print('Dry run. Use -f to push to remote.')

    os.chdir(find_repo_root(Path(os.getcwd())))

    # Use nil dates for git commits to avoid unnecessary churn when pushing refs.
    os.environ['GIT_COMMITTER_DATE'] = '2000-01-01 00:00:00+0000'
    os.environ['GIT_AUTHOR_DATE'] = '2000-01-01 00:00:00+0000'

    # Parse the current repo manifest.
    manifest = ET.fromstring(run('git', 'show', 'm/studio-main:default.xml', cwd=MANIFEST_DIR))

    # Push to remotes in parallel since it can take a while.
    # A ThreadPoolExecutor is sufficient because the GIL is released while running subprocesses.
    jobs = []
    with ThreadPoolExecutor() as executor:
        for project in manifest.iter('project'):
            project.set('dest-branch', 'studio-main') # Ensure uploaded changes go to studio-main.
            path = Path(project.get('path'))
            if not path.is_dir():
                # Nonexistent project generally correspond to prebuilts for other platforms.
                # Just assume these can stay synced to studio-main (we have no revision to pin anyway).
                pass
            elif check('git', 'rev-parse', '-q', '--verify', args.branch, cwd=path, stdout=DEVNULL):
                # Pin revision to a synthetic draft commit that we push to Gerrit.
                ref = run('git', 'commit-tree', '-p', 'm/studio-main^{}', '-m', COMMIT_MSG, f'{args.branch}^{{tree}}', cwd=path)
                if str(path) in ['tools/idea', 'tools/vendor/intellij/cidr']:
                    # Gerrit falls over when trying to render huge commits in IntelliJ/CIDR. We do
                    # not need these commits in Gerrit anyway because we compile against prebuilts.
                    # So instead we push these commits to the special draft branch.
                    jobs.append(executor.submit(push_to_draft_branch, path, ref, 'goog', args.push))
                else:
                    jobs.append(executor.submit(push_to_gerrit, path, ref, 'goog', args.push))
                project.set('revision', ref)
            else:
                # Pin revision to m/studio-main.
                project.set('revision', run('git', 'rev-parse', 'm/studio-main', cwd=path))

    # Check if any jobs raised an exception.
    for job in jobs:
        job.result()

    # Now push the updated manifest to Gerrit (for presubmit) and the draft branch (for sharing).
    jobs = []
    with ThreadPoolExecutor() as executor:
        manifest_str = ET.tostring(manifest)
        jobs.append(executor.submit(push_manifest_to_gerrit, manifest_str, args.push))
        jobs.append(executor.submit(push_manifest_to_draft_branch, manifest_str, args.push))

    # Check if any jobs raised an exception.
    for job in jobs:
        job.result()

    print('Finished successfully.')


def push_to_gerrit(path: Path, ref: str, remote: str, push: bool):
    if push:
        print(f'Uploading {ref:.10} in {path}')
        opts = 'wip,topic=intellij-update-draft-do-not-submit'
        try:
            run('git', 'push', '-o', 'banned-words~skip', remote, f'{ref}:refs/for/studio-main%{opts}', stderr=PIPE, cwd=path)
        except subprocess.CalledProcessError as e:
            stderr = e.stderr.decode().strip()
            if 'remote rejected' in stderr and 'no new changes' in stderr:
                pass  # Gerrit already has the change.
            else:
                print(stderr)
                raise e
    else:
        print(f'Would upload {ref:.10} in {path}')


def push_to_draft_branch(path: Path, ref: str, remote: str, push: bool):
    if push:
        print(f'Force-pushing {ref:.10} in {path}')
        run('git', 'push', '-f', '-o', 'banned-words~skip', remote, f'{ref}:studio-feature-ij2017.3', cwd=path)
    else:
        print(f'Would force-push {ref:.10} in {path}')


def push_manifest_to_draft_branch(manifest: str, push: bool):
    ref = create_manifest_commit(manifest, '[do not submit] IntelliJ update draft')
    push_to_draft_branch(MANIFEST_DIR, ref, 'origin', push)


def push_manifest_to_gerrit(manifest: str, push: bool):
    message = '\n'.join([
        '[do not submit] Temporarily pin project revisions',
        '',
        'To avoid churn while running tests against the next IntelliJ version.',
        '',
        DRAFT_CHANGE_ID,
    ])
    ref = create_manifest_commit(manifest, message)
    push_to_gerrit(MANIFEST_DIR, ref, 'origin', push)


def create_manifest_commit(manifest: str, message: str) -> str:
    # Commit an updated manifest without touching the working tree.
    oid = run('git', 'hash-object', '-w', '--stdin', input=manifest, cwd=MANIFEST_DIR)
    tree = run('git', 'ls-tree', 'm/studio-main', cwd=MANIFEST_DIR)
    tree = re.sub('.*default.xml', f'100644 blob {oid}\tdefault.xml', tree)
    tree = run('git', 'mktree', input=tree.encode(), cwd=MANIFEST_DIR)
    ref = run('git', 'commit-tree', '-p', 'm/studio-main^{}', '-m', message, tree, cwd=MANIFEST_DIR)
    return ref


def find_repo_root(cd: Path) -> Path:
    while cd != cd.root:
        if cd.joinpath('.repo').is_dir():
            return cd
        cd = cd.parent
    fail('Failed to find repo root')


# Run a command and return stdout.
def run(*args: str, **kwargs) -> str:
    return subprocess.check_output(args, **kwargs).decode().strip()


# Run a command and return whether it succeeded.
def check(*args: str, **kwargs) -> bool:
    return subprocess.run(args, **kwargs).returncode == 0


def fail(msg: str = 'unreachable') -> NoReturn:
    raise AssertionError(msg)


if __name__ == '__main__':
    main()
