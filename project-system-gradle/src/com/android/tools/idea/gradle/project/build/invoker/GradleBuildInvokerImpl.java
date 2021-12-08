/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.project.build.invoker;

import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createProjectProperty;
import static com.android.tools.idea.gradle.util.BuildMode.ASSEMBLE;
import static com.android.tools.idea.gradle.util.BuildMode.BUNDLE;
import static com.android.tools.idea.gradle.util.BuildMode.CLEAN;
import static com.android.tools.idea.gradle.util.BuildMode.COMPILE_JAVA;
import static com.android.tools.idea.gradle.util.BuildMode.REBUILD;
import static com.android.tools.idea.gradle.util.BuildMode.SOURCE_GEN;
import static com.android.tools.idea.gradle.util.GradleBuilds.CLEAN_TASK_NAME;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.convert;
import static com.intellij.openapi.ui.Messages.CANCEL;
import static com.intellij.openapi.ui.Messages.NO;
import static com.intellij.openapi.ui.Messages.YES;
import static com.intellij.openapi.ui.Messages.YesNoCancelResult;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static one.util.streamex.MoreCollectors.onlyOne;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.filters.AndroidReRunBuildFilter;
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.build.attribution.BuildAttributionManager;
import com.android.tools.idea.gradle.project.build.attribution.BuildAttributionOutputLinkFilter;
import com.android.tools.idea.gradle.project.build.attribution.BuildAttributionUtil;
import com.android.tools.idea.gradle.project.build.output.BuildOutputParserManager;
import com.android.tools.idea.gradle.run.OutputBuildActionUtil;
import com.android.tools.idea.gradle.util.BuildMode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.build.BuildConsoleUtils;
import com.intellij.build.BuildEventDispatcher;
import com.intellij.build.BuildViewManager;
import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.FailureResult;
import com.intellij.build.events.impl.FinishBuildEventImpl;
import com.intellij.build.events.impl.SkippedResultImpl;
import com.intellij.build.events.impl.StartBuildEventImpl;
import com.intellij.build.events.impl.SuccessResultImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemTaskExecutionEvent;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemEventDispatcher;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.xdebugger.XDebugSession;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.gradle.tooling.BuildAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;


/**
 * Invokes Gradle tasks directly. Results of tasks execution are displayed in both the "Messages" tool window and the new "Gradle Console"
 * tool window.
 */
@SuppressWarnings("UnstableApiUsage")
public class GradleBuildInvokerImpl implements GradleBuildInvoker {
  @NotNull private final Project myProject;
  @NotNull private final FileDocumentManager myDocumentManager;
  @NotNull private final GradleTasksExecutor myTaskExecutor;

  @NotNull private final List<String> myOneTimeGradleOptions = new ArrayList<>();
  @NotNull private final Multimap<String, String> myLastBuildTasks = ArrayListMultimap.create();
  @NotNull private final BuildStopper myBuildStopper = new BuildStopper();
  @NotNull private final NativeDebugSessionFinder myNativeDebugSessionFinder;

  @SuppressWarnings("unused") // This constructor is use by the component manager
  public GradleBuildInvokerImpl(@NotNull Project project) {
    this(project, FileDocumentManager.getInstance(), new GradleTasksExecutorImpl(), new NativeDebugSessionFinder(project));
  }

  @NonInjectable
  @VisibleForTesting
  protected GradleBuildInvokerImpl(@NotNull Project project,
                                   @NotNull FileDocumentManager documentManager,
                                   @NotNull GradleTasksExecutor tasksExecutor,
                                   @NotNull NativeDebugSessionFinder nativeDebugSessionFinder) {
    myProject = project;
    myDocumentManager = documentManager;
    myTaskExecutor = tasksExecutor;
    myNativeDebugSessionFinder = nativeDebugSessionFinder;
  }

  @Override
  public @NotNull ListenableFuture<GradleMultiInvocationResult> cleanProject() {
    if (stopNativeDebugSessionOrStopBuild()) {
      return Futures.immediateFuture(new GradleMultiInvocationResult(emptyList()));
    }
    // Collect the root project path for all modules, there is one root project path per included project.
    Set<File> projectRootPaths = Arrays.stream(ModuleManager.getInstance(getProject()).getModules())
      .map(module -> ProjectStructure.getInstance(myProject).getModuleFinder().getRootProjectPath(module).toFile())
      .collect(Collectors.toSet());
    return combineGradleInvocationResults(
      projectRootPaths.stream()
        .map(projectRootPath -> executeTasks(CLEAN, projectRootPath, Collections.singletonList(CLEAN_TASK_NAME)))
        .collect(toList()));
  }

  @Override
  public @NotNull ListenableFuture<GradleMultiInvocationResult> generateSources(@NotNull Module @NotNull [] modules) {
    BuildMode buildMode = SOURCE_GEN;

    GradleTaskFinder gradleTaskFinder = GradleTaskFinder.getInstance();
    ListMultimap<Path, String> tasks = gradleTaskFinder.findTasksToExecute(modules, buildMode, TestCompileType.NONE);
    return combineGradleInvocationResults(
      tasks.keySet().stream()
        .map(rootPath -> executeTasks(buildMode, rootPath.toFile(), tasks.get(rootPath),
                                      Collections.singletonList(createGenerateSourcesOnlyProperty())))
        .collect(toList()));
  }

  /**
   * @return {@code true} if the user selects to stop the current build.
   */
  private boolean stopNativeDebugSessionOrStopBuild() {
    XDebugSession nativeDebugSession = myNativeDebugSessionFinder.findNativeDebugSession();
    if (nativeDebugSession != null) {
      Ref<Integer> yesNoCancelRef = new Ref<>();
      Application application = ApplicationManager.getApplication();
      application.invokeAndWait(() -> yesNoCancelRef.set(promptUserToStopNativeDebugSession()), ModalityState.NON_MODAL);
      @YesNoCancelResult int yesNoCancel = yesNoCancelRef.get();
      switch (yesNoCancel) {
        case YES:
          // User selected "Terminate".
          nativeDebugSession.stop();
          break;
        case CANCEL:
          // User selected "Cancel". Do not continue with build.
          return true;
      }
    }
    return false;
  }

  @YesNoCancelResult
  private int promptUserToStopNativeDebugSession() {
    // If we have a native debugging process running, we need to kill it to release the files from being held open by the OS.
    String propKey = "gradle.project.build.invoker.clean-terminates-debugger";
    // We set the property globally, rather than on a per-project basis since the debugger either keeps files open on the OS or not.
    // If the user sets the property, it is stored in <config-dir>/config/options/options.xml
    String value = PropertiesComponent.getInstance().getValue(propKey);

    if (value == null) {
      Ref<Integer> yesNoCancelRef = new Ref<>();
      ApplicationManager.getApplication().invokeAndWait(() -> {
        String message = "Cleaning or rebuilding your project while debugging can lead to unexpected " +
                         "behavior.\n" +
                         "You can choose to either terminate the debugger before cleaning your project " +
                         "or keep debugging while cleaning.\n" +
                         "Clicking \"Cancel\" stops Gradle from cleaning or rebuilding your project, " +
                         "and preserves your debug process.";
        MessageDialogBuilder.YesNoCancel dialogBuilder = MessageDialogBuilder.yesNoCancel("Terminate debugging", message);
        int answer = dialogBuilder.yesText("Terminate").noText("Do not terminate").cancelText("Cancel").doNotAsk(
          new DialogWrapper.DoNotAskOption.Adapter() {
            @Override
            public void rememberChoice(boolean isSelected, int exitCode) {
              if (isSelected) {
                PropertiesComponent.getInstance().setValue(propKey, Boolean.toString(exitCode == YES));
              }
            }
          })
          .show(myProject);
        yesNoCancelRef.set(answer);
      }, ModalityState.NON_MODAL);
      @YesNoCancelResult int answer = yesNoCancelRef.get();
      return answer;
    }
    getLogger().debug(propKey + ": " + value);
    return value.equals("true") ? YES : NO;
  }

  @Override
  public boolean getInternalIsBuildRunning() {
    return myTaskExecutor.internalIsBuildRunning(getProject());
  }

  @NotNull
  @Override
  public ListenableFuture<AssembleInvocationResult> executeAssembleTasks(@NotNull Module @NotNull [] assembledModules,
                                                                         @NotNull List<Request> request) {
    BuildMode buildMode = request.stream()
      .map(Request::getMode)
      .filter(Objects::nonNull)
      .distinct()
      .collect(onlyOne())
      .orElseThrow(() -> new IllegalArgumentException("Each request requires the same not null build mode to be set"));
    Map<String, List<Module>> modulesByRootProject = Arrays.stream(assembledModules)
      .map(it -> Pair.create(it, toSystemIndependentName(
        ProjectStructure.getInstance(myProject).getModuleFinder().getRootProjectPath(it).toFile().getPath())))
      .filter(it -> it.second != null)
      .collect(groupingBy(it -> it.second, mapping(it -> it.first, toList())));
    ListenableFuture<GradleMultiInvocationResult> resultFuture = executeTasks(
      request.stream()
        .map(it -> Pair
          .<Request, BuildAction<?>>create(it, OutputBuildActionUtil
            .create(modulesByRootProject.get(toSystemIndependentName(it.getRootProjectPath().getPath())))))
        .collect(toList())
    );
    return Futures.transform(resultFuture, it -> new AssembleInvocationResult(it, buildMode), directExecutor());
  }

  @NotNull
  private static String createGenerateSourcesOnlyProperty() {
    return createProjectProperty(AndroidProject.PROPERTY_GENERATE_SOURCES_ONLY, true);
  }

  /**
   * Execute Gradle tasks that compile the relevant Java sources.
   *
   * @param modules         Modules that need to be compiled
   * @param testCompileType Kind of tests that the caller is interested in. Use {@link TestCompileType#ALL} if compiling just the
   *                        main sources, {@link TestCompileType#UNIT_TESTS} if class files for running unit tests are needed.
   */
  @Override
  public @NotNull ListenableFuture<GradleMultiInvocationResult> compileJava(@NotNull Module @NotNull [] modules, @NotNull TestCompileType testCompileType) {
    BuildMode buildMode = COMPILE_JAVA;
    ListMultimap<Path, String> tasks = GradleTaskFinder.getInstance().findTasksToExecute(modules, buildMode, testCompileType);
    return combineGradleInvocationResults(
      tasks.keySet().stream()
        .map(rootPath -> executeTasks(buildMode, rootPath.toFile(), tasks.get(rootPath)))
        .collect(toList()));
  }

  @Override
  @NotNull
  public ListenableFuture<AssembleInvocationResult> assemble(Module @NotNull [] modules, @NotNull TestCompileType testCompileType) {
    BuildMode buildMode = ASSEMBLE;
    ListMultimap<Path, String> tasks = GradleTaskFinder.getInstance().findTasksToExecute(modules, buildMode, testCompileType);
    return executeAssembleTasks(
      modules,
      tasks.keySet().stream()
        .map(rootPath ->
               Request.builder(myProject, rootPath.toFile(), tasks.get(rootPath))
                 .setMode(buildMode)
                 .build()
        )
        .collect(toList()));
  }

  @Override
  @NotNull
  public ListenableFuture<AssembleInvocationResult> bundle(Module @NotNull [] modules) {
    BuildMode buildMode = BUNDLE;
    ListMultimap<Path, String> tasks =
      GradleTaskFinder.getInstance().findTasksToExecute(modules, buildMode, TestCompileType.NONE);
    return executeAssembleTasks(
      modules,
      tasks.keySet().stream()
        .map(rootPath ->
               Request.builder(myProject, rootPath.toFile(), tasks.get(rootPath))
                 .setMode(buildMode)
                 .build()
        )
        .collect(toList()));
  }

  @Override
  public @NotNull ListenableFuture<GradleMultiInvocationResult> rebuild() {
    BuildMode buildMode = REBUILD;
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    ListMultimap<Path, String> tasks =
      GradleTaskFinder.getInstance().findTasksToExecute(moduleManager.getModules(), buildMode, TestCompileType.NONE);
    return combineGradleInvocationResults(
      tasks.keySet().stream()
        .map(rootPath -> executeTasks(buildMode, rootPath.toFile(), tasks.get(rootPath)))
        .collect(toList()));
  }

  /**
   * Execute the last run set of Gradle tasks, with the specified gradle options prepended before the tasks to run.
   */
  @Override
  public @NotNull ListenableFuture<GradleMultiInvocationResult> rebuildWithTempOptions(@NotNull File rootProjectPath,
                                                                                       @NotNull List<String> options) {
    myOneTimeGradleOptions.addAll(options);
    try {
      // TODO(solodkyy): Rework to preserve the last requests? This may not work when build involves multiple roots.
      Collection<String> tasks = myLastBuildTasks.get(rootProjectPath.getPath());
      if (tasks.isEmpty()) {
        // For some reason the IDE lost the Gradle tasks executed during the last build.
        return rebuild();
      }
      else {
        // The use case for this is the following:
        // 1. the build fails, and the console has the message "Run with --stacktrace", which now is a hyperlink
        // 2. the user clicks the hyperlink
        // 3. the IDE re-runs the build, with the Gradle tasks that were executed when the build failed, and it adds "--stacktrace"
        //    to the command line arguments.
        List<String> tasksFromLastBuild = new ArrayList<>(tasks);
        return combineGradleInvocationResults(
          ImmutableList.of(
            executeTasks(
              Request
                .builder(myProject, rootProjectPath, tasksFromLastBuild)
                .setCommandLineArguments(myOneTimeGradleOptions)
                .build())));
      }
    }
    finally {
      // Don't reuse them on the next rebuild.
      myOneTimeGradleOptions.clear();
    }
  }

  private @NotNull ListenableFuture<GradleInvocationResult> executeTasks(@NotNull BuildMode buildMode, @NotNull File rootProjectPath, @NotNull List<String> gradleTasks) {
    return executeTasks(buildMode, rootProjectPath, gradleTasks, myOneTimeGradleOptions);
  }

  @NotNull
  public ListenableFuture<GradleMultiInvocationResult> executeTasks(@NotNull List<Pair<Request, BuildAction<?>>> requests) {
    List<ListenableFuture<GradleInvocationResult>> futures =
      requests.stream()
        .map(it -> executeTasks(it.first, it.second))
        .collect(toList());
    return combineGradleInvocationResults(futures);
  }

  @NotNull
  private ListenableFuture<GradleMultiInvocationResult> combineGradleInvocationResults(List<ListenableFuture<GradleInvocationResult>> futures) {
    Futures.FutureCombiner<GradleInvocationResult> result = Futures.whenAllComplete(futures);
    return result.call(() -> {
      List<GradleInvocationResult> results = new ArrayList<>();
      for (ListenableFuture<GradleInvocationResult> future : futures) {
        results.add(future.get());
      }
      return new GradleMultiInvocationResult(results);
    }, directExecutor());
  }

  @VisibleForTesting
  @NotNull
  public ListenableFuture<GradleInvocationResult> executeTasks(@NotNull BuildMode buildMode,
                                                               @NotNull File rootProjectPath,
                                                               @NotNull List<String> gradleTasks,
                                                               @NotNull List<String> commandLineArguments) {
    return executeTasks(
      Request.builder(myProject, rootProjectPath, gradleTasks)
        .setMode(buildMode)
        .setCommandLineArguments(commandLineArguments)
        .build());
  }

  /**
   * Do not use. This method exposes implementation details that may change. The existing usages need to be re-implemented.
   */
  @TestOnly
  @Deprecated
  public ExternalSystemTaskNotificationListener temporaryCreateBuildTaskListenerForTests(@NotNull Request request,
                                                                                         @NotNull String executionName) {
    return createBuildTaskListener(request, executionName, null);
  }

  @NotNull
  private ExternalSystemTaskNotificationListener createBuildTaskListener(@NotNull Request request,
                                                                         @NotNull String executionName,
                                                                         @Nullable ExternalSystemTaskNotificationListener delegate) {
    @NotNull BuildViewManager buildViewManager = myProject.getService(BuildViewManager.class);
    // This is resource is closed when onEnd is called or an exception is generated in this function bSee b/70299236.
    // We need to keep this resource open since closing it causes BuildOutputInstantReaderImpl.myThread to stop, preventing parsers to run.
    //noinspection resource, IOResourceOpenedButNotSafelyClosed
    @NotNull BuildEventDispatcher eventDispatcher = new ExternalSystemEventDispatcher(request.getTaskId(), buildViewManager);
    try {
      return new MyListener(eventDispatcher, request, buildViewManager, executionName, delegate);
    }
    catch (Exception exception) {
      eventDispatcher.close();
      throw exception;
    }
  }

  @Override
  @NotNull
  public ListenableFuture<GradleInvocationResult> executeTasks(@NotNull Request request) {
    return executeTasks(request, null);
  }

  @VisibleForTesting
  public ListenableFuture<GradleInvocationResult> executeTasks(@NotNull Request request, @Nullable BuildAction<?> buildAction) {
    String rootProjectPath = request.getRootProjectPath().getPath();
    // Remember the current build's tasks, in case they want to re-run it with transient gradle options.
    myLastBuildTasks.removeAll(rootProjectPath);
    List<String> gradleTasks = request.getGradleTasks();
    myLastBuildTasks.putAll(rootProjectPath, gradleTasks);

    getLogger().info("About to execute Gradle tasks: " + gradleTasks);
    if (gradleTasks.isEmpty()) {
      return Futures.immediateFuture(new GradleInvocationResult(request.getRootProjectPath(), request.getGradleTasks(), null));
    }
    String executionName = getExecutionName(request);
    ExternalSystemTaskNotificationListener buildTaskListener = createBuildTaskListener(request, executionName, request.getListener());
    return internalExecuteTasks(request, buildAction, buildTaskListener);
  }

  private ListenableFuture<GradleInvocationResult> internalExecuteTasks(@NotNull Request request,
                                                                        @Nullable BuildAction<?> buildAction,
                                                                        @NotNull ExternalSystemTaskNotificationListener buildTaskListener) {
    ApplicationManager.getApplication().invokeAndWait(myDocumentManager::saveAllDocuments);

    ListenableFuture<GradleInvocationResult> resultFuture = myTaskExecutor.execute(request, buildAction, myBuildStopper, buildTaskListener);

    if (request.isWaitForCompletion() && !ApplicationManager.getApplication().isDispatchThread()) {
      try {
        resultFuture.get();
      }
      catch (InterruptedException e) {
        resultFuture.cancel(true);
        Thread.currentThread().interrupt();
      }
      catch (ExecutionException ignored) {
        // Ignore. We've been asked to wait for any result. That's it.
      }
    }
    return resultFuture;
  }

  @NotNull
  private String getExecutionName(@NotNull Request request) {
    File projectPath = request.getRootProjectPath();
    return "Build " + projectPath.getName();
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(GradleBuildInvoker.class);
  }

  @Override
  public boolean stopBuild(@NotNull ExternalSystemTaskId id) {
    if (myBuildStopper.contains(id)) {
      myBuildStopper.attemptToStopBuild(id, null);
      return true;
    }
    return false;
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  private class MyListener extends ExternalSystemTaskNotificationListenerAdapter {
    @NotNull private final Request myRequest;
    @NotNull private final BuildViewManager myBuildViewManager;
    @NotNull private final String myExecutionName;
    @NotNull private final BuildEventDispatcher myBuildEventDispatcher;
    private boolean myBuildFailed;

    private boolean myStartBuildEventPosted = false;

    public MyListener(@NotNull BuildEventDispatcher eventDispatcher,
                      @NotNull Request request,
                      @NotNull BuildViewManager buildViewManager,
                      @NotNull String executionName,
                      @Nullable ExternalSystemTaskNotificationListener delegate) {
      super(delegate);
      myRequest = request;
      myBuildViewManager = buildViewManager;
      myExecutionName = executionName;
      myBuildEventDispatcher = eventDispatcher;
      myBuildFailed = false;
    }

    @Override
    public void onStart(@NotNull ExternalSystemTaskId id, String workingDir) {
      AnAction restartAction = new RestartAction(myRequest);

      myBuildFailed = false;
      Presentation presentation = restartAction.getTemplatePresentation();
      presentation.setText("Restart");
      presentation.setDescription("Restart");
      presentation.setIcon(AllIcons.Actions.Compile);

      // If build is invoked in the context of a task that has already opened the build output tool window by sending a similar event
      // sending another one replaces the mapping from the buildId to the build view breaking the build even pipeline. (See: b/190426050).
      if (myBuildViewManager.getBuildView(id) == null) {
        long eventTime = System.currentTimeMillis();
        DefaultBuildDescriptor buildDescriptor = new DefaultBuildDescriptor(id, myExecutionName, workingDir, eventTime);
        if (myRequest.getDoNotShowBuildOutputOnFailure()) {
          buildDescriptor.setActivateToolWindowWhenFailed(false);
        }
        StartBuildEventImpl event = new StartBuildEventImpl(buildDescriptor, "running...");
        event.withRestartAction(restartAction).withExecutionFilter(new AndroidReRunBuildFilter(workingDir));
        if (BuildAttributionUtil.isBuildAttributionEnabledForProject(myProject)) {
          event.withExecutionFilter(new BuildAttributionOutputLinkFilter());
        }
        myStartBuildEventPosted = true;
        myBuildEventDispatcher.onEvent(id, event);
      }
      super.onStart(id, workingDir);
    }

    @Override
    public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
      if (event instanceof ExternalSystemBuildEvent) {
        BuildEvent buildEvent = ((ExternalSystemBuildEvent)event).getBuildEvent();
        myBuildEventDispatcher.onEvent(event.getId(), buildEvent);
      }
      else if (event instanceof ExternalSystemTaskExecutionEvent) {
        BuildEvent buildEvent = convert(((ExternalSystemTaskExecutionEvent)event));
        myBuildEventDispatcher.onEvent(event.getId(), buildEvent);
      }
      super.onStatusChange(event);
    }

    @Override
    public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
      myBuildEventDispatcher.setStdOut(stdOut);
      myBuildEventDispatcher.append(text);
      super.onTaskOutput(id, text, stdOut);
    }

    @Override
    public void onEnd(@NotNull ExternalSystemTaskId id) {
      CountDownLatch eventDispatcherFinished = new CountDownLatch(1);
      myBuildEventDispatcher.invokeOnCompletion((t) -> {
        if (myBuildFailed) {
          myProject.getService(BuildOutputParserManager.class).sendBuildFailureMetrics();
        }
        eventDispatcherFinished.countDown();
      });
      myBuildEventDispatcher.close();

      // The underlying output parsers are closed asynchronously. Wait for completion in tests.
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        try {
          //noinspection ResultOfMethodCallIgnored
          eventDispatcherFinished.await(10, SECONDS);
        }
        catch (InterruptedException ex) {
          throw new RuntimeException("Timeout waiting for event dispatcher to finish.", ex);
        }
      }
      super.onEnd(id);
    }

    @Override
    public void onSuccess(@NotNull ExternalSystemTaskId id) {
      addBuildAttributionLinkToTheOutput(id);
      if (myStartBuildEventPosted) {
        FinishBuildEventImpl event = new FinishBuildEventImpl(id, null, System.currentTimeMillis(), "finished",
                                                              new SuccessResultImpl());
        myBuildEventDispatcher.onEvent(id, event);
      }
      super.onSuccess(id);
    }

    private void addBuildAttributionLinkToTheOutput(@NotNull ExternalSystemTaskId id) {
      if (BuildAttributionUtil.isBuildAttributionEnabledForProject(myProject)) {
        BuildAttributionManager manager = myProject.getService(BuildAttributionManager.class);
        if (manager != null && manager.shouldShowBuildOutputLink()) {
          String buildAttributionTabLinkLine = BuildAttributionUtil.buildOutputLine();
          onTaskOutput(id, "\n" + buildAttributionTabLinkLine + "\n", true);
        }
      }
    }

    @Override
    public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) {
      myBuildFailed = true;
      if (myStartBuildEventPosted) {
        String title = myExecutionName + " failed";
        DataContext dataContext = BuildConsoleUtils.getDataContext(id, myBuildViewManager);
        FailureResult failureResult = ExternalSystemUtil.createFailureResult(title, e, GRADLE_SYSTEM_ID, myProject, dataContext);
        myBuildEventDispatcher.onEvent(id, new FinishBuildEventImpl(id, null, System.currentTimeMillis(), "failed", failureResult));
      }
      super.onFailure(id, e);
    }

    @Override
    public void onCancel(@NotNull ExternalSystemTaskId id) {
      if (myStartBuildEventPosted) {
        // Cause build view to show as skipped all pending tasks (b/73397414)
        FinishBuildEventImpl event = new FinishBuildEventImpl(id, null, System.currentTimeMillis(), "cancelled", new SkippedResultImpl());
        myBuildEventDispatcher.onEvent(id, event);
        myBuildEventDispatcher.close();
      }
      super.onCancel(id);
    }
  }

  private class RestartAction extends AnAction {
    private @NotNull final GradleBuildInvoker.Request myRequest;

    public RestartAction(@NotNull GradleBuildInvoker.Request request) {myRequest = request;}

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(!myBuildStopper.contains(myRequest.getTaskId()));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      GradleBuildInvoker.Request newRequest = GradleBuildInvoker.Request.copyRequest(myRequest);

      String executionName = getExecutionName(newRequest);
      ExternalSystemTaskNotificationListener buildTaskListener = createBuildTaskListener(newRequest, executionName, newRequest.getListener());
      internalExecuteTasks(newRequest, null, buildTaskListener);
    }
  }
}
