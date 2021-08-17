# Running UI tests with Bazel

This is for now only supported on Linux. UI tests are run as any other test, to run it simply do:

    bazel test //tools/adt/idea/android-uitests:GuiTestRuleTest

## Running tests on the current DISPLAY

To connect to another display, first the sandbox needs to be disabled and then passing a `DISPLAY` test envvar will tell the test to use that

    bazel test //tools/adt/idea/android-uitests:GuiTestRuleTest --spawn_strategy=standalone --test_env=DISPLAY=:0

## Connecting to an already running test

You will probably have to install this the first time:

    sudo apt-get install vncviewer
    sudo apt-get install x11vnc

Then, if you are running the test with `--test_output=streamed`, you will see a line in the output like:

    Launched xvfb on ":30519"

Then you can run a vncserver on that display:

    x11vnc -display :30519 -localhost

And then connect to it:

    vncviewer :0

Or use the handy:

    x11vnc -display :30519 -localhost & vncviewer :0

To avoid accidentally clicking something in the instance of Android Studio
running as part of the test, you can pass --ViewOnly to vncviewer, e.g.

    vncviewer --ViewOnly :0

## Running on a specific virtual server

Another option is to run a server first like this:

    Xfvb :1234

And set the argument `--test_env=DISPLAY=:1234` to tell the test to use that one. Then you can connect to it as described above.

## Running through remote desktop

If the test is running through remote desktop, the main display of remote desktop may not be ":0".
If this is the case, run the following to get the main display's string:

    ps -u $(id -u) -o pid= \
        | xargs -I PID -r cat /proc/PID/environ 2> /dev/null \
        | tr '\0' '\n' \
        | grep ^DISPLAY=: \
        | sort -u

(Courtesy of: https://superuser.com/questions/647464/how-to-get-the-display-number-i-was-assigned-by-x)

Then use the result of the above query in the command earlier. I.e.:

    bazel test //tools/adt/idea/android-uitests:GuiTestRuleTest --spawn_strategy=standalone --test_env=DISPLAY=<insert_display_string_here>
