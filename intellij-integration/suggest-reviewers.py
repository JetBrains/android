#!/usr/bin/env python3
from collections import Counter
from concurrent.futures import Executor, ThreadPoolExecutor
from dataclasses import dataclass
from typing import Iterator
import argparse
import os
import re
import subprocess


# Main idea: use git-diff to compute the file regions touched by a given commit, then use
# git-blame to find who authored those regions prior to the commit. Print out the authors
# sorted by the number of lines they touched. Do everything concurrently because git-blame
# can be a little slow.
def main():
    parser = argparse.ArgumentParser(description='Use git blame to find good reviewers for a change')
    parser.add_argument('commits', nargs='+', help='the changes needing code review')
    parser.add_argument('-U', '--context', type=int, default=2, help='num context lines per hunk')
    parser.add_argument('-v', '--verbose', action='store_true', help='print author info per file')
    args = parser.parse_args()
    os.chdir(run('git', 'rev-parse', '--show-toplevel'))  # Move to git root directory.
    commits = run('git', 'rev-parse', *args.commits).splitlines()  # Get stable SHAs.
    print(f'Finding reviewers for {len(commits)} commit(s)')
    with ThreadPoolExecutor() as executor:
        find_reviewers(commits, args.context, args.verbose, executor)


def find_reviewers(commits: list[str], context: int, verbose: bool, executor: Executor):
    # Note: we currently do not parallelize across commits because nested usages of
    # ThreadPoolExecutor can too easily lead to pool exhaustion and deadlock.
    for commit in commits:
        desc = run('git', 'log', '-1', '--format=%H: %s', commit)
        underline = '=' * 40
        print(f'{underline}\n{desc}\n{underline}')
        try:
            author_counts = find_reviewers_for_commit(commit, context, executor)
        except Exception as e:
            print(e)
            print()
            continue
        if verbose:
            print('Total')
        print_author_counts(author_counts.total)
        print()
        if verbose:
            for file, counts in author_counts.by_file.items():
                print(file)
                print_author_counts(counts)
                print()


@dataclass
class AuthorLineCounts:
    """Map from author emails to the corresponding number of blamed lines."""
    total: Counter
    by_file: dict[str,Counter]


def find_reviewers_for_commit(commit: str, context: int, executor: Executor) -> AuthorLineCounts:
    files = run('git', 'diff-tree', '-r', '--name-only', '--diff-filter=MD', '--no-commit-id', commit).splitlines()
    if len(files) == 0:
        raise Exception(f'No files modified by commit {commit:.10}')
    if len(files) > 50:
        raise Exception(f'Too many files modified by commit {commit:.10}')
    jobs = [executor.submit(find_reviewers_in_file, commit, context, file) for file in files]
    results = [job.result() for job in jobs]
    authors_total = sum(results, Counter())
    authors_by_file = dict(zip(files, results))
    return AuthorLineCounts(authors_total, authors_by_file)


def find_reviewers_in_file(commit: str, context: int, file: str) -> Counter:
    diff = run('git', 'diff', f'-U{context}', f'{commit}~', commit, '--', file)
    region_args = [f'-L{r.start},{r.end}' for r in extract_regions(diff)]
    if len(region_args) == 0:
        print(f'Assuming binary file: {file}')
        last_modifier = run('git', 'log', '-1', '--format=%ae', f'{commit}~', '--', file)
        return Counter([last_modifier])
    blame = run('git', 'blame', '--line-porcelain', *region_args, f'{commit}~', '--', file)
    authors = re.findall(r'^author-mail <(.*)>$', blame, re.MULTILINE)
    return Counter(authors)


def print_author_counts(counts: Counter):
    for author, count in counts.most_common():
            print(f' {count:4d} {author}')


@dataclass
class Region:
    start: int
    end: int


def extract_regions(diff: str) -> Iterator[Region]:
    # Parse line offsets from hunk headers. Example header: @@ -50,7 +50,12 @@
    for start, offset in re.findall(r'^@@ -(\d+),(\d+) \+\d+,\d+ @@', diff, re.MULTILINE):
        start = int(start)
        end = start + int(offset) - 1
        yield Region(start, end)


# Run a command and return stdout.
def run(*args: str, **kwargs) -> str:
    return subprocess.check_output(args, **kwargs).decode().strip()


if __name__ == '__main__':
    main()
