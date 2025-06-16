#!/usr/bin/env python3
import argparse
import os
import shlex
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description='Rebase and push individual commits to studio-main', allow_abbrev=False)
    parser.add_argument('commit')
    parser.add_argument('-f', '--push', action='store_true', help='push to remote')
    parser.add_argument('-p', '--presubmit', action='store_true')
    parser.add_argument('-t', '--topic')
    parser.add_argument('-ht', '--hashtag')
    args = parser.parse_args()

    if not args.push:
        print('Dry run. Use -f to push to remote.')

    # Rebase.
    rebased = cherry_pick('goog/studio-main', args.commit)

    # Sanity-check the number of new refs.
    new_refs = run('git', 'log', '--format=%H', rebased, '^goog/studio-main').splitlines()
    if len(new_refs) != 1:
        sys.exit(f'ERROR: cherry-pick {rebased:.10} has too many new refs on top of studio-main')

    # Push.
    push_args = []
    if args.presubmit: push_args.append(f'l=Presubmit-Ready+1')
    if args.topic: push_args.append(f'topic={args.topic}')
    if args.hashtag: push_args.append(f'hashtag={args.hashtag}')
    push_args = ','.join(push_args)
    push_cmd = ['git', 'push', 'goog', f'{rebased}:refs/for/studio-main%{push_args}']
    print(f'Will run:', shlex.join(push_cmd))
    if args.push:
        run(*push_cmd)
    else:
        subprocess.check_call(['git', 'show', rebased])


# Half copied from 'repo-smart-rebase'.
def cherry_pick(head: str, commit: str) -> str:
    """Like git-cherry-pick but does not touch the working tree."""
    new_tree, *_ = run('git', 'merge-tree', f'--merge-base={commit}^1', head, commit).splitlines()

    # Preserve authorship; see https://git-scm.com/book/en/v2/Git-Internals-Environment-Variables.
    author, email, date, *msg = run('git', 'log', '-1', '--format=%an%n%ae%n%ad%n%B', commit).splitlines()
    msg_args = ['-m', '\n'.join(msg)]
    env = dict(GIT_AUTHOR_NAME=author, GIT_AUTHOR_EMAIL=email, GIT_AUTHOR_DATE=date, **os.environ)
    cherry_pick = run('git', 'commit-tree', '-p', f'{head}^{{commit}}', *msg_args, new_tree, env=env)

    # Double-check that the cherry-pick looks reasonably similar to the original commit.
    # Note: we use git-diff-tree because for some reason it is way faster than git-show.
    tree_diff = ['git', 'diff-tree', '-r', '--name-only', '--diff-merges=1', '--no-commit-id']
    old_changed_files = run(*tree_diff, commit).splitlines()
    new_changed_files = run(*tree_diff, cherry_pick).splitlines()
    assert old_changed_files and new_changed_files, 'Expected a nonzero number of changed files'
    if new_changed_files != old_changed_files:
        sys.exit(f'ERROR: cherry-pick {cherry_pick:.10} modifies different files than its original commit {commit:.10}')

    return cherry_pick


# Run a command and return stdout.
def run(*args: str, **kwargs) -> str:
    return subprocess.check_output(args, **kwargs).decode().strip()


if __name__ == '__main__':
    main()
