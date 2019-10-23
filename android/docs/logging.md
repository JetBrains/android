# IDE logging system

Logging in the IDE is based on the [`com.intellij.openapi.diagnostic.Logger` class](../../../../idea/platform/util/src/com/intellij/openapi/diagnostic/Logger.java).
This is an abstraction layer that allows different logging libraries (or their configurations) to be chosen by the IDE, without affecting
most of the code. In the current setup, Log4J is used by the IDE at runtime. For details, see
[`IdeaLogger`](../../../../idea/platform/platform-impl/src/com/intellij/idea/IdeaLogger.java),
[`LoggerFactory`](../../../../idea/platform/platform-impl/src/com/intellij/idea/LoggerFactory.java) and
[`StartupUtil.prepareAndStart`](../../../../idea/platform/platform-impl/src/com/intellij/idea/StartupUtil.java).

Logging is configured slightly differently during testing, where additional output is produced at the end of the test run and separate files
are used. See [`TestLoggerFactory`](../../../../idea/platform/testFramework/src/com/intellij/testFramework/TestLoggerFactory.java).

## Reading logs
Configuration for the logging system can be found in [`log.xml`](../../../../idea/bin/log.xml). By default all messages with level `INFO`
and above are written to the log file, `idea.log`. When running Studio locally, you can find it under `tools/idea/system/log`. Warnings are
additionally written to standard output and errors are made visible to the user by flashing a red icon in the status bar. See the section
below for details on how to use the `DEBUG` level.

When you start Studio from IntelliJ, the run configuration will open two tabs in the Run/Debug tool window: "Console" and "idea.log". The
former displays standard output (and thus all warnings), the latter the full log (note that there's UI there to filter displayed log
entries).

## Using loggers
Because loggers can be turned on and off in various ways, it's important to use a logger with a name that's related to the code that's
using it (logger names typically come from class and package names). There are three ways of getting a `Logger` instance:

- Calling `Logger.getInstance(Class)`, the most common.
- From Kotlin, calling `com.intellij.openapi.diagnostic.LoggerKt#logger()`, e.g. `val LOG = logger<FeatureUsageSettingsEventScheduler>()`.
- From Kotlin, calling `com.intellij.openapi.diagnostic.LoggerKt#logger(KProperty)` to be used in top-level functions.

If you are only logging in exceptional situations, consider constructing the `Logger` instance only once you need it, e.g. when dealing
with an exception. The logging infrastructure has a cost, so this way you don't pay for what you don't need. If you log during normal IDE
operation (on `INFO` or `DEBUG` level), store the logger in a static final field, so it's not recreated every time it's used. See
[go/adtstyle](http://go/adtstyle) for more details.

Make sure to provide enough information and a `Throwable` instance when available. The `idea.log` file is often the only piece of
information we get from users and it makes life a lot easier if it can be used to fix a problem that cannot be reproduced locally.

Pick the right level for every message. Scanning attached log files for errors and warnings is the first step in triaging a bug, so try not
to spam these levels. Use the `ERROR` log level only in the case of genuinely unexpected errors. Error logs grab user's attention since they
flash a red icon in the status bar. In addition, the number of times these happen is tracked via metrics as a proxy for Studio quality.
Typically, scenarios that you can recover from should not be errors. Exceptions logged as errors can be found in our exceptions dashboard.

Remember that formatting the strings and writing to the log file takes time, so consider using `DEBUG` level for any additional information
(and see the section below on how to get that information back).

## `DEBUG` level

By default `DEBUG` messages are not logged anywhere, but this doesn't mean the level is useless. You (or the user) can turn on debug logging
for a given logger (or all loggers in a given package) by using Help, Debug Log Settings. To use, paste name of a logger in the box and save
your changes. You should start seeing relevant log output in `idea.log`. Remember that `Logger.getInstance` prepends a # character to the
class name! When running Studio locally, you will have to change the display settings in the idea.log tab to show all levels (it shows only
`INFO` and above by default). The set of enabled loggers is a sticky setting and will survive a restart. You can ask your users to go
through this flow before attaching logs, to get additional information.

Remember that debug messages are ignored most of the time, so make sure not to do any work just for the purpose of logging (including
calling `String.format`). Most of the time, you should check `Logger.isDebugEnabled` before computing the information that needs to be
logged. In Kotlin you can use the `Logger.debug(e: Exception?, lazyMessage: () -> String)` extension method which will do nothing if debug
output is not needed.
