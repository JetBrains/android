/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.compiler.PostProjectBuildTasksExecutor;
import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.ProjectBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.util.ThreeState;
import com.intellij.util.messages.MessageBusConnection;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ComponentFixture;
import org.fest.swing.timing.Condition;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

import static com.android.tools.idea.gradle.GradleSyncState.GRADLE_SYNC_TOPIC;
import static com.android.tools.idea.gradle.compiler.PostProjectBuildTasksExecutor.GRADLE_BUILD_TOPIC;
import static com.android.tools.idea.gradle.util.BuildMode.COMPILE_JAVA;
import static com.android.tools.idea.gradle.util.BuildMode.SOURCE_GEN;
import static com.android.tools.idea.tests.gui.framework.GuiTests.LONG_TIMEOUT;
import static junit.framework.Assert.assertNotNull;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.timing.Pause.pause;
import static org.fest.util.Strings.quote;
import static org.jetbrains.android.AndroidPlugin.EXECUTE_BEFORE_PROJECT_SYNC_TASK_IN_GUI_TEST_KEY;
import static org.junit.Assert.fail;

public class IdeFrameFixture extends ComponentFixture<IdeFrameImpl> {
  private EditorFixture myEditor;

  @NotNull private final File myProjectPath;

  @NotNull private final GradleProjectEventListener myGradleProjectEventListener;

  @NotNull
  public static IdeFrameFixture find(@NotNull final Robot robot, @NotNull final File projectPath, @Nullable final String projectName) {
    final GenericTypeMatcher<IdeFrameImpl> matcher = new GenericTypeMatcher<IdeFrameImpl>(IdeFrameImpl.class) {
      @Override
      protected boolean isMatching(IdeFrameImpl frame) {
        Project project = frame.getProject();
        if (project != null && projectPath.getPath().equals(project.getBasePath())) {
          return projectName == null || projectName.equals(project.getName());
        }
        return false;
      }
    };

    pause(new Condition("IdeFrame " + quote(projectPath.getPath()) + " to show up") {
      @Override
      public boolean test() {
        Collection<IdeFrameImpl> frames = robot.finder().findAll(matcher);
        return !frames.isEmpty();
      }
    }, LONG_TIMEOUT);

    IdeFrameImpl ideFrame = robot.finder().find(matcher);
    return new IdeFrameFixture(robot, ideFrame, projectPath);
  }

  public IdeFrameFixture(@NotNull Robot robot, @NotNull IdeFrameImpl target, @NotNull File projectPath) {
    super(robot, target);
    myProjectPath = projectPath;
    final Project project = getProject();

    Disposable disposable = new NoOpDisposable();
    Disposer.register(project, disposable);

    myGradleProjectEventListener = new GradleProjectEventListener();

    MessageBusConnection connection = project.getMessageBus().connect(disposable);
    connection.subscribe(GRADLE_SYNC_TOPIC, myGradleProjectEventListener);
    connection.subscribe(GRADLE_BUILD_TOPIC, myGradleProjectEventListener);
  }

  @NotNull
  public File getProjectPath() {
    return myProjectPath;
  }

  @NotNull
  public IdeFrameFixture requireModuleCount(int expected) {
    Module[] modules = getModuleManager().getModules();
    assertThat(modules).as("Module count in project " + quote(getProject().getName())).hasSize(expected);
    return this;
  }

  @NotNull
  public IdeaAndroidProject getAndroidProjectForModule(@NotNull String name) {
    Module module = getModule(name);
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null && facet.isGradleProject()) {
      IdeaAndroidProject androidProject = facet.getIdeaAndroidProject();
      if (androidProject != null) {
        return androidProject;
      }
    }
    throw new AssertionError("Unable to find IdeaAndroidProject for module " + quote(name));
  }

  @NotNull
  public Module getModule(@NotNull String name) {
    for (Module module : getModuleManager().getModules()) {
      if (name.equals(module.getName())) {
        return module;
      }
    }
    throw new AssertionError("Unable to find module with name " + quote(name));
  }

  @NotNull
  private ModuleManager getModuleManager() {
    return ModuleManager.getInstance(getProject());
  }

  @NotNull
  public Project getProject() {
    Project project = target.getProject();
    assertNotNull(project);
    return project;
  }

  @NotNull
  public EditorFixture getEditor() {
    if (myEditor == null) {
      myEditor = new EditorFixture(robot, this);
    }

    return myEditor;
  }

  @NotNull
  public GradleInvocationResult invokeProjectMake() {
    myGradleProjectEventListener.reset();

    final AtomicReference<GradleInvocationResult> resultRef = new AtomicReference<GradleInvocationResult>();
    ProjectBuilder.getInstance(getProject()).addAfterProjectBuildTask(new ProjectBuilder.AfterProjectBuildTask() {
      @Override
      public void execute(@NotNull GradleInvocationResult result) {
        resultRef.set(result);
      }

      @Override
      public boolean execute(CompileContext context) {
        return false;
      }
    });
    selectProjectMakeAction();
    waitForBuildToFinish(COMPILE_JAVA);

    GradleInvocationResult result = resultRef.get();
    assertNotNull(result);

    return result;
  }

  @NotNull
  public CompileContext invokeProjectMakeUsingJps() {
    final Project project = getProject();
    AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
    buildConfiguration.USE_EXPERIMENTAL_FASTER_BUILD = false;

    final AtomicReference<CompileContext> contextRef = new AtomicReference<CompileContext>();
    CompilerManager compilerManager = CompilerManager.getInstance(project);

    Disposable disposable = new NoOpDisposable();
    compilerManager.addCompilationStatusListener(new CompilationStatusListener() {
      @Override
      public void compilationFinished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
        contextRef.set(compileContext);
      }

      @Override
      public void fileGenerated(String outputRoot, String relativePath) {
      }
    }, disposable);

    try {
      selectProjectMakeAction();

      pause(new Condition("Build (" + COMPILE_JAVA + ") for project " + quote(project.getName()) + " to finish'") {
        @Override
        public boolean test() {
          CompileContext context = contextRef.get();
          return context != null;
        }
      }, LONG_TIMEOUT);

      CompileContext context = contextRef.get();
      assertNotNull(context);

      return context;
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  protected void selectProjectMakeAction() {
    JMenuItem makeProjectMenuItem = findActionMenuItem("Build", "Make Project");
    robot.click(makeProjectMenuItem);
  }

  @NotNull
  private JMenuItem findActionMenuItem(@NotNull String... path) {
    assertThat(path).isNotEmpty();
    int segmentCount = path.length;
    Container root = target;
    for (int i = 0; i < segmentCount; i++) {
      final String segment = path[i];
      JMenuItem found = robot.finder().find(root, new GenericTypeMatcher<JMenuItem>(JMenuItem.class) {
        @Override
        protected boolean isMatching(JMenuItem menuItem) {
          return segment.equals(menuItem.getText());
        }
      });
      if (i < segmentCount - 1) {
        robot.click(found);
        root = robot.findActivePopupMenu();
        continue;
      }
      return found;
    }
    throw new AssertionError("Menu item with path " + Arrays.toString(path) + " should have been found already");
  }

  private void waitForBuildToFinish(@NotNull final BuildMode buildMode) {
    final Project project = getProject();
    pause(new Condition("Build (" + buildMode + ") for project " + quote(project.getName()) + " to finish'") {
      @Override
      public boolean test() {
        if (buildMode == SOURCE_GEN) {
          PostProjectBuildTasksExecutor tasksExecutor = PostProjectBuildTasksExecutor.getInstance(project);
          if (tasksExecutor.getLastBuildTimestamp() > -1) {
            // This will happen when creating a new project. Source generation happens before the IDE frame is found and build listeners
            // are created. It is fairly safe to assume that source generation happened if we have a timestamp for a "last performed build".
            return true;
          }
        }
        return myGradleProjectEventListener.isBuildFinished(buildMode);
      }
    }, LONG_TIMEOUT);

    waitForBackgroundTasksToFinish();
    robot.waitForIdle();
  }

  @NotNull
  public FileFixture findExistingFileByRelativePath(@NotNull String relativePath) {
    VirtualFile file = findFileByRelativePath(relativePath, true);
    return new FileFixture(getProject(), file);
  }

  @Nullable
  @Contract("_, true -> !null")
  public VirtualFile findFileByRelativePath(@NotNull String relativePath, boolean requireExists) {
    Project project = getProject();
    VirtualFile file = project.getBaseDir().findFileByRelativePath(relativePath);
    if (requireExists) {
      assertNotNull("Unable to find file with relative path " + quote(relativePath), file);
    }
    return file;
  }

  @NotNull
  public IdeFrameFixture requestProjectSyncAndExpectFailure() {
    requestProjectSync();
    return waitForGradleProjectSyncToFail();
  }

  @NotNull
  public IdeFrameFixture requestProjectSyncAndSimulateFailure(@NotNull final String failure) {
    Runnable failTask = new Runnable() {
      @Override
      public void run() {
        throw new RuntimeException(failure);
      }
    };
    ApplicationManager.getApplication().putUserData(EXECUTE_BEFORE_PROJECT_SYNC_TASK_IN_GUI_TEST_KEY, failTask);
    // When simulating the error, we don't have to wait for sync to happen. Sync never happens because the error is thrown before it (sync)
    // is started.
    return requestProjectSync();
  }

  @NotNull
  public IdeFrameFixture requestProjectSync() {
    myGradleProjectEventListener.reset();
    findActionButtonByActionId("Android.SyncProject").click();
    return this;
  }

  @NotNull
  public IdeFrameFixture waitForGradleProjectSyncToFail() {
    try {
      waitForGradleProjectSyncToFinish(true);
      fail("Expecting project sync to fail");
    }
    catch (RuntimeException expected) {
      // expected failure.
    }
    return waitForBackgroundTasksToFinish();
  }

  @NotNull
  public IdeFrameFixture waitForGradleProjectSyncToFinish() {
    waitForGradleProjectSyncToFinish(false);
    return this;
  }

  private void waitForGradleProjectSyncToFinish(final boolean expectSyncFailure) {
    final Project project = getProject();

    // ensure GradleInvoker (in-process build) is always enabled.
    AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
    buildConfiguration.USE_EXPERIMENTAL_FASTER_BUILD = true;

    pause(new Condition("Syncing project " + quote(project.getName()) + " to finish") {
      @Override
      public boolean test() {
        GradleSyncState syncState = GradleSyncState.getInstance(project);
        boolean syncFinished =
          (myGradleProjectEventListener.isSyncFinished() || syncState.isSyncNeeded() != ThreeState.YES) && !syncState.isSyncInProgress();
        if (expectSyncFailure) {
          syncFinished = syncFinished && myGradleProjectEventListener.hasSyncError();
        }
        return syncFinished;
      }
    }, LONG_TIMEOUT);

    if (myGradleProjectEventListener.hasSyncError()) {
      RuntimeException syncError = myGradleProjectEventListener.getSyncError();
      myGradleProjectEventListener.reset();
      throw syncError;
    }

    if (!myGradleProjectEventListener.isSyncSkipped()) {
      waitForBuildToFinish(SOURCE_GEN);
    }

    waitForBackgroundTasksToFinish();
  }

  @NotNull
  public IdeFrameFixture waitForBackgroundTasksToFinish() {
    pause(new Condition("Background tasks to finish") {
      @Override
      public boolean test() {
        ProgressManager progressManager = ProgressManager.getInstance();
        return !progressManager.hasModalProgressIndicator() &&
               !progressManager.hasProgressIndicator() &&
               !progressManager.hasUnsafeProgressIndicator();
      }
    }, LONG_TIMEOUT);
    robot.waitForIdle();
    return this;
  }

  @NotNull
  private ActionButtonFixture findActionButtonByActionId(String actionId) {
    return ActionButtonFixture.findByActionId(actionId, robot, target);
  }

  @NotNull
  public MessagesToolWindowFixture getMessagesToolWindow() {
    return new MessagesToolWindowFixture(getProject(), robot);
  }

  private static class NoOpDisposable implements Disposable {
    @Override
    public void dispose() {
    }
  }
}
