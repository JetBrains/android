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


## Running on a specific virtual server

Another option is to run a server first like this:

    Xfvb :1234

And set the argument `--test_env=DISPLAY:1234` to tell the test to use that one. Then you can connect to it as described above.


