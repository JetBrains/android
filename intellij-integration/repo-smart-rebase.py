#!/usr/bin/env python3
from concurrent.futures import ThreadPoolExecutor, Future
from pathlib import Path
from subprocess import CalledProcessError, SubprocessError, DEVNULL
from typing import NoReturn
import argparse
import functools
import os
import shlex
import subprocess
import sys


# In contrast to 'repo rebase', this script:
#
#   Rebases merge commits and preserve the 2nd parent.
#
#   Detects when m/studio-main is already an ancestor and bails early if so.
#
#   Does the rebase in-memory and only updates the working tree once the entire rebase succeeds
#     (thereby minimizing disk I/O and keeping the working tree clean if the rebase fails).
#
#   Does a "dry run" to anticipate results and merge conflicts before committing.
#
# Example usage: ./repo-smart-rebase.py --sync --apply --skip-up-to-date my-large-branch


def main():
    parser = argparse.ArgumentParser(description='A better version of repo-rebase that handles merge commits and more')
    parser.add_argument('branch', help='which local branch to rebase')
    parser.add_argument('projects', type=Path, nargs='*', help='optionally update only specific projects')
    parser.add_argument('-s', '--sync', action='store_true', help='run repo sync beforehand')
    parser.add_argument('-f', '--apply', action='store_true', help='update HEAD and working tree')
    parser.add_argument('--skip-up-to-date', action='store_true', help='skip assertions for projects already up-to-date')
    args = parser.parse_args()
    if not args.apply:
        print('Dry run. Use -f to move HEAD and update the working tree.')

    # Run repo sync.
    if args.sync:
        print('Running repo sync')
        print('===')
        subprocess.check_call(['repo', 'sync', '-cvnj16', *args.projects])
        print('===')

    # Compute project list and move to repo root.
    projects = [Path(p) for p in run('repo', 'list', '--path-only', *args.projects).splitlines()]
    os.chdir(find_repo_root(Path(os.getcwd())))

    # Rebase projects in parallel and keep track of (project, job) pairs.
    project_jobs: list[tuple[Path,Future]] = []
    with ThreadPoolExecutor() as executor:
        for project in projects:
            job = executor.submit(rebase_project, project, args.branch, args.apply, args.skip_up_to_date)
            project_jobs.append((project, job))
            job.add_done_callback(functools.partial(report_done, project))

    # Analyze the results.
    print('===')
    for (project, job) in project_jobs:
        if job.exception() is None and job.result() != '<quiet>':
            print(f'{project}: {job.result()}')
    for (project, job) in project_jobs:
        err = job.exception()
        if isinstance(err, CalledProcessError):
            print(f'ERROR: {project}: command failed: {shlex.join(err.cmd)}', file=sys.stderr)
        elif err is not None:
            print(f'ERROR: {project}: {err}', file=sys.stderr)
    success = all(job.exception() is None for (_, job) in project_jobs)
    print('===')
    print('Finished successfully.' if success else 'Some projects aborted due to errors.')
    sys.exit(0 if success else 1)


def rebase_project(project: Path, branch: str, apply: bool, skip_up_to_date: bool) -> str:
    # Check if we are on the target branch. If not, just detach to m/studio-main.
    current_branch = run('git', 'rev-parse', '--abbrev-ref', 'HEAD', cwd=project)
    if current_branch != branch:
        if apply:
            run('git', 'checkout', '-q', 'm/studio-main', cwd=project)
        already_detached = current_branch == 'HEAD'
        return '<quiet>' if already_detached else f'detached from {current_branch}'

    # Check if m/studio-main is already an ancestor.
    already_up_to_date = check('git', 'merge-base', '--is-ancestor', 'm/studio-main', branch, cwd=project)
    if already_up_to_date and skip_up_to_date:
        return 'already up-to-date'

    # Verify that no upstream changes are merge commits, since those may require special care.
    upstream_changes = run('git', 'rev-list', '--first-parent', f'{branch}..m/studio-main', cwd=project).splitlines()
    for change in upstream_changes:
        if len(query_parents(project, change)) > 1:
            fail(f'Cannot rebase on top of new merge commit in studio-main: {change:.10}')

    # Start the rebase.
    changes = run('git', 'rev-list', '--first-parent', '--reverse', f'm/studio-main..{branch}', cwd=project).splitlines()
    print(f'Progress: {project}: starting rebase')
    orig_head = run('git', 'rev-parse', branch, cwd=project)
    head = run('git', 'rev-parse', 'm/studio-main', cwd=project)
    for change in changes:
        if len(query_parents(project, change)) == 0:
            # No parents? Must be a grafted commit in a prebuilts project. We can generally
            # assume that grafted commits are ancestors of studio-main.
            print(f'Warning: {project}: skipping grafted commit {change:.10}')
            continue
        head = smart_cherry_pick(project, head, change)

    # If m/studio-main was already an ancestor, assert that our rebase was a no-op (just to help shake out bugs).
    if already_up_to_date:
        run('git', 'diff', '--quiet', branch, head, cwd=project)
        return 'already up-to-date'

    # Move HEAD and update the working tree.
    if apply:
        run('git', 'branch', '-f', 'tmp-repo-smart-rebase-backup', branch, cwd=project)
        print(f'Progress: {project}: checking out {head:.10}')
        run('git', 'checkout', '-q', '-B', branch, head, cwd=project)

    count = run('git', 'rev-list', '--count', '--first-parent', f'm/studio-main..{head}', cwd=project)
    action = 'moved' if apply else 'would move'
    return f'{action} {count} from {orig_head:.10} to {head:.10}'


def smart_cherry_pick(project: Path, head: str, commit: str) -> str:
    """Like git-cherry-pick but preserves merge commits and does not touch the working tree."""
    # Compute parents.
    _, *extra_parents = query_parents(project, commit)
    parent_args = ['-p', head]
    for extra_parent in extra_parents:
        parent_args += ['-p', extra_parent]

    # Compute the new tree (or thrown an exception if there are merge conflicts).
    try:
        new_tree, *_ = run('git', 'merge-tree', f'--merge-base={commit}^1', head, commit, cwd=project).splitlines()
    except SubprocessError:
        # Assume git-merge-tree failed due to merge conflicts.
        fail(f'aborting due to merge conflicts with {commit:.10}')
    if check('git', 'diff', '--quiet', head, new_tree, cwd=project):
        print(f'Warning: {project}: skipping empty commit {commit:.10}')
        return head

    # Preserve authorship; see https://git-scm.com/book/en/v2/Git-Internals-Environment-Variables.
    author, email, date, *msg = run('git', 'log', '-1', '--format=%an%n%ae%n%ad%n%B', commit, cwd=project).splitlines()
    msg_args = ['-m', '\n'.join(msg)]
    env = dict(GIT_AUTHOR_NAME=author, GIT_AUTHOR_EMAIL=email, GIT_AUTHOR_DATE=date, **os.environ)
    cherry_pick = run('git', 'commit-tree', *parent_args, *msg_args, new_tree, cwd=project, env=env)

    # Double-check that the cherry-pick looks reasonably similar to the original commit.
    # Note: we use git-diff-tree because for some reason it is way faster than git-show.
    tree_diff = ['git', 'diff-tree', '-r', '--name-only', '--diff-merges=1', '--no-commit-id']
    old_changed_files = run(*tree_diff, commit, cwd=project).splitlines()
    new_changed_files = run(*tree_diff, cherry_pick, cwd=project).splitlines()
    assert old_changed_files and new_changed_files, 'Expected a nonzero number of changed files'
    if new_changed_files != old_changed_files:
        fail(f'Cherry-pick {cherry_pick:.10} modifies different files than its original commit {commit:.10}')

    return cherry_pick


def query_parents(project: Path, commit: str) -> list[str]:
    parents = run('git', 'log', '-1', '--format=%P', commit, cwd=project)
    return parents.split(' ') if parents else []


def report_done(project: Path, f: Future[str]):
    if f.exception() is None and f.result() != '<quiet>':
        print(f'Progress: {project}: done')


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
    raise Exception(msg)


if __name__ == '__main__':
    main()
