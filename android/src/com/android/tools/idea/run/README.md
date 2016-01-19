# Run Configurations

This document gives a quick overview of the various classes involved in launching applications.
While this document focuses mostly on the android plugin itself, parts of this code are also used by
the NDK and Bazel plugins to support other types of launches.

[TOC]

## References

IntelliJ has a short [overview](http://www.jetbrains.org/intellij/sdk/docs/basics/run_configurations.html)
of how run configurations work.

## Run Configuration UX

The android `plugin.xml` declares two configuration types: `AndroidRunConfigurationType` and `AndroidTestRunConfigurationType`.
The factory methods in those classes are used to instantiate an `AndroidRunConfiguration` or an `AndroidTestRunConfiguration` respectively.
These represent a single run configuration that is configured by the user. The `getConfigurationEditor` method in these run configurations
returns the settings editor that defines the UX. The `AndroidRunConfigurationEditor` also has a configuration specific panel so that
some clients can customize just a few things (e.g. launching an activity in run config vs launching tests in the test config).

## Run Configuration Execution Flow

The following steps are taken to actually perform a launch:

1. User first selects a particular configuration. This would be an instance of `AndroidRunConfiguration` (which implements `RunProfile`).
2. She then clicks on Run/Debug or Profile, each of which corresponds to a different `Executor`.
3. A `ProgramRunner` is selected based on the above two (configuration + executor). The android plugin defines a `AndroidProgramRunner`,
   which is very simple and mostly just ends up calling the run state. (The infrastructure chooses the first runner that `canRun` the given
   configuration state and executor.
4. An `ExecutionEnvironment` is created, and the `ProgramRunner.execute()` is called.
     1. This results in a call to `RunProfile.getState()`, which maps to `AndroidRunConfiguration.getState()`:
     2. We do a bunch of checks, then pick a device to deploy to, extract other necessary parameters and return a `RunProfileState`.
        (IntelliJ docs suggest that the returned `RunProfileState` object should describe a process about to be started.
        At this stage, the command line parameters, environment variables and other information required to start the process is initialized).
        In the android plugin, the returned state may be:
          1. `AndroidRunState` if this we are deploying to a local device
          3. One of the various Cloud launch specific states if deploying to the cloud.
     3. `ExecutionManager.startRunProfile` ends up calling `compileAndRun`
          1. We look at the `BeforeRunTask` instances registered on the run configuration.
          2. All the `BeforeRunTask`'s are executed on a pooled thread, and once they are done, the run process continues again on the EDT
     4. Back again on the EDT, the `ExecutionManager` performs the following:

```java
          final RunContentDescriptor descriptor = starter.execute(project, executor, state, environment.getContentToReuse(), environment);
          environment.setContentToReuse(descriptor);
          myRunningConfigurations.add((descriptor, env.getRunnerAndConfigurationSettings(), executor));
          Disposer.register(descriptor, () -> {myRunningConfigurations.remove(trinity);});
          getContentManager().showRunContent(executor, descriptor, environment.getContentToReuse());
          final ProcessHandler processHandler = descriptor.getProcessHandler();
          processHandler.startNotify();
          processHandler.addProcessListener(new ProcessExecutionListener(project, profile, processHandler));
```

5. The first step in the above snippet (`starter.execute`) results in the ProgramRunner's execute method being called.
   In the case of '`AndroidDebugRunner.doExecute()`, we simply end up calling `super.doExecute()`
6. Finally, we call `AndroidRunningState.execute`. IntelliJ docs say that the `RunProfileState.execute` method "starts the process,
   attaches a ProcessHandler to its input and output streams, creates a console to display the process output, and returns an
   `ExecutionResult` object aggregating the console and the process handler." In the Android plugin, we do pretty much the same thing,
   except that actually starting the process is moved to a separate pooled thread. This means that all error conditions will have to
   be communicated via the process handler..





