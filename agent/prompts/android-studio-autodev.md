# Your task

You are a fully autonomous coding agent working on the Android Studio product at Google.
Your goal is to find the first un-triaged bug, fix it, and then upload a Changelist (CL) for review.

Steps:

1.  Query Buganizer for `hotlistid:7830549 -hotlistid:7829499`.
    1. If there are 0 bugs, then just return. Do not try to perform any other Buganizer queries.
    2. Do not rerun the query just to double check that there are no bugs.
2.  Otherwise, claim the first bug by adding the hotlist `7829499` to it.
3.  Retrieve the contents of the first bug and understand the issue / request.
4.  Now you will write code to fix the issue and upload a CL, you may now activate the android-studio-development skill.
5.  To start, always run:
    1. `repo sync -j 16 --rebase` (to get the latest code)
    2. `repo start <new-branch-name> .` (to create our working branch)
6.  Then make your changes.
    1. Refer to SKILL.md and other prompts in the android-studio-development skill while working in this Android Studio codebase to learn how to search the code, build it, and run tests.
    2. **VERY IMPORTANT**: Scope your `grep`s and other file searches to small subdirectories under `/tools`. NEVER search in `/prebuilts` or it will take a very long time.
7.  Once your changes are done prepare a SINGLE commit with your changes:
    1. `git commit -m "<description>" -m "Bug: XXXXXX" -m "Test: Need Human"`
    2. NOTE: It is important to include the 2 additional `-m` for the Bug ID and the "Test: Need Human". Do not forget those.
8.  Use the Repo Upload command to upload the CL:
    1. `repo upload --cbr --yes .`
    2. Always accept formatting checks if asked. If there are formatting changes, `repo upload` command will exit, in which you will then need to update your commit by performing a git commit amend, followed by `repo upload --cbr --yes .` again.
    3. Once completed, you should see a URL for the newly uploaded CL.
9.  In a new comment on the original bug in Buganizer, report your summary along with the CL URL link.
10. You can now exit.
