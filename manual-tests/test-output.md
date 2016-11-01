## Test Output Test

This is a regression test for issue
[201968](https://code.google.com/p/android/issues/detail?id=201968).

First, download the sample project provided
[here](https://code.google.com/p/android/issues/detail?id=201968#c6).
Open this project and sync it with the Gradle files.

Run the "ExampleInstrumentedT..." configuration with an AVD, and verify that
all 10 tests pass. There should be a green "OK" icon before each entry in the
test console, which should depict a tree of the following structure:

* Test Results
  * com.example.grl.testproject.ExampleInstrumentedTest
    * useAppContext2
    * useAppContext3
    * useAppContext4
    * useAppContext5
    * useAppContext6
    * useAppContext7
    * useAppContext8
    * useAppContext9
    * useAppContext10
    * useAppContext

The order of the useAppContext entries does not matter, but all 10 must be
present, passing, and nested under the ExampleInstrumentedTest entry.

Finally, Debug "ExampleInstrumentedT...", and verify that it also produces the
same output.

### Race Condition Evaluation

_If you are QA,_ ___you may ignore this section.___

Because the original issue was a race condition, it may not occur every time
the above manual test is performed. It is possible to reduce the chance of a false
negative by slowing the execution of one of the threads, making it more likely
to lose the race if the race condition still exists.

To do this, open the Android Studio source, and navigate to the `stdout` method
of `ProcessHandlerConsolePrinter`. This class is located in the following file:
`tools/adt/idea/android/src/com/android/tools/idea/run/ProcessHandlerConsolePrinter.java`

At line 57, where `stdout` calls `print`, create a breakpoint. Set the
breakpoint to 'Evaluate and log' the following:

    int baz = 0;
    for (int i = 0; i < 10000; i++)
        baz += 10;
    return baz;

Make sure the breakpoint is not set to suspend any threads. Increasing the 10000
will make the thread even slower, lowering the probability of a false negative but
increasing the test run time.

Debug Android Studio with the breakpoint enabled. Then perform the normal reproduction
steps in this debugged instance.
