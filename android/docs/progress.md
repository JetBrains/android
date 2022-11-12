# Progress system

IntelliJ's progress is a tool for controlling long-running operations. This may mean:

* Displaying a dialog box with a progress bar while the operation is running.
* Allowing user to cancel the operation.
* Allowing the operation to update the text and completion percentage as it runs.
* Associating background tasks with a [`Disposable`](../../../../idea/platform/util/src/com/intellij/openapi/Disposable.java), so they get
cancelled when e.g. a dialog or the whole project is closed.
* Running a long operation on the UI thread (with the write lock held), while still pumping AWT messages from time to time.

Start by reading the [official docs](https://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/general_threading_rules.html#background-processes-and-processcanceledexception)
on the subject, see below for more in-depth details.

## Progress Indicator

[`ProgressIndicator`](../../../../idea/platform/core-api/src/com/intellij/openapi/progress/ProgressIndicator.java) is an object that allows
the task and whoever started it to exchange information. A task should generally work with any `ProgressIndicator` and it's the job of the
caller to pick the right indicator and run tasks using it.

The task should call `checkCancelled()` from time to time, which will throw a
[`ProcessCancelledException`](../../../../idea/platform/util/src/com/intellij/openapi/progress/ProcessCanceledException.java) if the task
has indeed been cancelled. `ProcessCanceledException` is not considered a crash by the IDE, so you don't have to catch it and should avoid
wrapping it in `RuntimeException`, `ExecutionException` or `UncheckedExecutionException`. If you do catch it, finish what you're doing
quickly and avoid running for a long time after your task has been cancelled.

The progress indicator to use may be passed to you from the caller, but it can also be implicit for a given thread. That's why the most
common way of checking for cancellation is calling the static method `ProgressManager.checkCancelled()`. This is what most PSI methods do,
so just doing anything with PSI means you're already using the progress system.

By default the IDE cancels certain operations if they take too long, e.g. code completion. If you want to disable this for debugging,
use the "Disable ProcessCanceledException"
[internal action](../../../../idea/platform/platform-impl/src/com/intellij/internal/DisablePCEAction.java).

You can look at `checkCancelled` as a form of cooperative multitasking: your thread gives the IDE a chance to either throw an exception
(effectively freeing the OS thread to do other things) or execute other maintenance operations. This is how the UI gets updated during
long-running refactorings that otherwise would block the UI thread (see
[`PotemkinProgress`](../../../../idea/platform/platform-impl/src/com/intellij/openapi/progress/util/PotemkinProgress.java)) or how the IDE
prioritizes some threads by parking all other threads (see `ProgressManagerImpl#sleepIfNeededToGivePriorityToAnotherThread`).

Useful indicator implementations:
* [`EmptyProgressIndicator`](../../../../idea/platform/core-api/src/com/intellij/openapi/progress/EmptyProgressIndicator.java)
* [`ProgressWindow`](../../../../idea/platform/platform-impl/src/com/intellij/openapi/progress/util/ProgressWindow.java), but most likely
this can be handled by `ProgressManager` (see below).
* [`SmoothProgressAdapter`](../../../../idea/platform/platform-impl/src/com/intellij/openapi/progress/util/SmoothProgressAdapter.java),
typically used with `ProgressWindow`.

## Progress manager
[`ProgressManager`](../../../../idea/platform/core-api/src/com/intellij/openapi/progress/ProgressManager.java) is an application service
used to "run tasks under progress" and managing progress indicators.

The most basic entry point is `runProcess` which makes the IDE aware of the task and allows you to choose a custom progress indicator. The
benefit of using it is that the IDE will ask the user for confirmation if they try to close the IDE while a task is still running.

Other obvious use case is running the task in a background thread, with a responsive modal dialog in the foreground. This is done by calling
`runProcessWithProgressSynchronously` and is the easiest way of moving expensive operations off the UI thread. Of course the modal dialog
prevents the user from doing anything, so it's only a bit better than freezing the UI. The best way to compute something in the background
(if possible) is to use the `*Asynchronously` methods and consider if the progress UI should start minimized.

Note that `ProgressManager` has two equivalent APIs: you can either call methods like `runProcessWithProgressSynchronously` or create your
own [Task](../../../../idea/platform/core-api/src/com/intellij/openapi/progress/Task.java) objects and pass them to `run`, which is
equivalent. There are also some Kotlin extensions, like [runBackgroundableTask](../../../../idea/platform/platform-impl/src/com/intellij/openapi/progress/progress.kt).

Finally, the last piece of functionality available in `ProgressManager` is running a potentially long read action under a progress indicator
that will get cancelled whenever there's a pending write action, which means the action should be able to recover from earlier failures and
the caller needs to keep submitting it in a loop. The benefit is that write lock is not blocked, so write operations like typing are more
responsive. There are a few APIs in IntelliJ that let you do this, and they all end up calling the same code that is based on the progress
system and assumes the task will call `checkCancelled` often enough:
* `ProgressManager.runInReadActionWithWriteActionPriority`
* `ReadAction.nonBlocking` combined with `NonBlockingReadAction.submit`. This API handles rescheduling, so is preferable.
* `ProgressIndicatorUtils.runInReadActionWithWritePriority` and `ProgressIndicatorUtils.scheduleWithWriteActionPriority` (for running on
EDT). This is what other APIs call into.

If you'd like to play with non-blocking read action, you can use this code as template. This can be registered as an internal action
to execute and you should see the action cancelled as you try typing in the editor:

```kotlin
package com.android.tools.idea.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.concurrency.AppExecutorUtil

private val LOG = Logger.getInstance(TryNonBlockingReadAction::class.java)

/**
 * Use by adding this to android-plugin.xml:
 * `<action internal="true" id="Android.TryNonBlockingReadAction" class="com.android.tools.idea.actions.TryNonBlockingReadAction"/`
 */
class TryNonBlockingReadAction : AnAction("Start a long non-blocking read action") {
  override fun actionPerformed(e: AnActionEvent) {
    ReadAction.nonBlocking {
      try {
        LOG.info("TryNonBlockingReadAction starting")
        repeat(1000) {
          ProgressManager.checkCanceled()
          Thread.sleep(10)
          LOG.info("TryNonBlockingReadAction running on ${Thread.currentThread().name}")
        }
      }
      finally {
        LOG.info("TryNonBlockingReadAction stack unwinding.")
      }
    }.submit(AppExecutorUtil.getAppExecutorService())
      .onSuccess { LOG.info("success") }
      .onError { t -> LOG.info("error: ${t::class.java.name}") }
  }
}
```

## Other related APIs

[`BackgroundTaskUtil`](../../../../idea/platform/platform-impl/src/com/intellij/openapi/progress/util/BackgroundTaskUtil.java) contains
methods for executing a piece of code, synchronously or on a pooled thread, under a progress indicator tied to a given `Disposable`, which
means the indicator (and thus the task itself) gets cancelled when the `Disposable` is disposed.
