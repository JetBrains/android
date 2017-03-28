## Lint Baseline Test

First, create a new project.

Next, open up the Gradle file, locate the android { } block, and
insert the following in there:

```groovy
android {
    lintOptions {
        baseline file('my-baseline.xml')
    }
}
```

Perform a Gradle sync. Then run lint: Analyze > Inspect Code; choose
the whole project.

Lint should now run, the Inspections View opens up, and in the bottom
right corner a bubble tells you that lint has created a baseline file
with the current issues.

Click on Refresh/rerun, the green icon in the top left corner of the
Inspections window to run inspections again.  This time note that all
the lint warnings disappear and they're replaced by a single
"baseline" issue which explains that most warnings have been hidden
due to the baseline. (There may be other warnings, from IntelliJ, such
as Spelling errors; IntelliJ inspections other than lint aren't
included in the baseline feature.)

Next add a new warning (for example, in a Java file add the string

```java
String s = "/sdcard/";
```

Rerun inspections. Notice how this new bug *does* show up in the
inspections results.

### Quickfixes

In the Inspections view, locate the warning that explains that issues
have been filtered out. It has a quickfix (look in the context menu or
over on the right). The quickfix lets you run inspections again,
without the baseline. Invoke it and confirm that all the original
issues show up.

If you re-run again, the baseline should kick in again.

Next go to the directory where the baseline file was created, and
delete the file. Now re-run inspections again. The IDE recreates the
baseline -- but now it has our new Java error added above.

Go and delete that added error. Re-run inspections. Now the IDE gives
another lint baseline warning, explaining that some issues have been
fixed. Invoke the quickfix to update the baseline removing this
fix. You can now put the warning back in and re-run inspections and
the error comes back.
