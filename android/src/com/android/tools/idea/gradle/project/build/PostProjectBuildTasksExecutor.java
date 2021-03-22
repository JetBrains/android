/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build;

import static com.android.tools.idea.gradle.util.BuildMode.DEFAULT_BUILD_MODE;
import static com.android.tools.idea.gradle.util.BuildMode.SOURCE_GEN;
import static com.android.tools.idea.gradle.util.GradleProjects.isOfflineBuildModeEnabled;
import static com.android.tools.idea.gradle.util.GradleProjects.isSyncRequestedDuringBuild;
import static com.android.tools.idea.gradle.util.GradleProjects.setSyncRequestedDuringBuild;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_BUILD_SYNC_NEEDED_AFTER_BUILD;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_USER_REQUEST_WHILE_BUILDING;
import static com.intellij.util.ThreeState.YES;

import com.android.ide.common.blame.Message;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.AbstractIterator;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

/**
 * After a build is complete, this class will execute the following tasks:
 * <ul>
 * <li>Notify user that unresolved dependencies were detected in offline mode, and suggest to go <em>online</em></li>
 * <li>Refresh Studio's view of the file system (to see generated files)</li>
 * <li>Remove any build-related data stored in the project itself (e.g. modules to build, current "build mode", etc.)</li>
 * <li>Notify projects that source generation is finished (if applicable)</li>
 * </ul>
 * Both JPS and the "direct Gradle invocation" build strategies ares supported.
 */
public class PostProjectBuildTasksExecutor {
  private static final Key<Long> PROJECT_LAST_BUILD_TIMESTAMP_KEY = Key.create("android.gradle.project.last.build.timestamp");

  @NotNull private final Project myProject;

  @NotNull
  public static PostProjectBuildTasksExecutor getInstance(@NotNull Project project) {
    return project.getService(PostProjectBuildTasksExecutor.class);
  }

  public PostProjectBuildTasksExecutor(@NotNull Project project) {
    myProject = project;
  }

  public void onBuildCompletion(@NotNull CompileContext context) {
    Iterator<String> errors = Collections.emptyIterator();
    CompilerMessage[] errorMessages = context.getMessages(CompilerMessageCategory.ERROR);
    if (errorMessages.length > 0) {
      errors = new CompilerMessageIterator(errorMessages);
    }
    onBuildCompletion(errors, errorMessages.length);
  }

  @Nullable
  public Long getLastBuildTimestamp() {
    return myProject.getUserData(PROJECT_LAST_BUILD_TIMESTAMP_KEY);
  }

  private static class CompilerMessageIterator extends AbstractIterator<String> {
    @NotNull private final CompilerMessage[] myErrors;
    private int counter;

    CompilerMessageIterator(@NotNull CompilerMessage[] errors) {
      myErrors = errors;
    }

    @Override
    @Nullable
    protected String computeNext() {
      if (counter >= myErrors.length) {
        return endOfData();
      }
      return myErrors[counter++].getMessage();
    }
  }

  private static class MessageIterator extends AbstractIterator<String> {
    private final Iterator<Message> myIterator;

    MessageIterator(@NotNull Collection<Message> compilerMessages) {
      myIterator = compilerMessages.iterator();
    }

    @Override
    @Nullable
    protected String computeNext() {
      if (!myIterator.hasNext()) {
        return endOfData();
      }
      Message msg = myIterator.next();
      return msg != null ? msg.getText() : null;
    }
  }

  public void onBuildCompletion(@NotNull GradleInvocationResult result) {
    Iterator<String> errors = Collections.emptyIterator();
    List<Message> errorMessages = result.getCompilerMessages(Message.Kind.ERROR);
    if (!errorMessages.isEmpty()) {
      errors = new MessageIterator(errorMessages);
    }
    onBuildCompletion(errors, errorMessages.size());
  }

  @VisibleForTesting
  void onBuildCompletion(Iterator<String> errorMessages, int errorCount) {
    if (AndroidProjectInfo.getInstance(myProject).requiresAndroidModel()) {
      if (isOfflineBuildModeEnabled(myProject)) {
        while (errorMessages.hasNext()) {
          String error = errorMessages.next();
          if (error != null && unresolvedDependenciesFound(error)) {
            notifyUnresolvedDependenciesInOfflineMode();
            break;
          }
        }
      }

      BuildSettings buildSettings = BuildSettings.getInstance(myProject);
      BuildMode buildMode = buildSettings.getBuildMode();
      String runConfigurationTypeId = buildSettings.getRunConfigurationTypeId();
      buildSettings.clear();

      // Refresh Studio's view of the file system after a compile. This is necessary for Studio to see generated code.
      // If this build is invoked from a run configuration, then we should refresh synchronously since subsequent task,
      // e.g. unit test run, might need updated VFS state immediately.
      refreshProject(runConfigurationTypeId == null);

      myProject.putUserData(PROJECT_LAST_BUILD_TIMESTAMP_KEY, System.currentTimeMillis());

      // Don't invoke Gradle Sync if the build is invoked by run configuration.
      // That will cause problems because Gradle Sync and Deploy will both run at the same time.
      // During Gradle sync, AndroidModuleModel is re-created, while Deploy relies on AndroidModuleModel to know apk location.
      if (runConfigurationTypeId != null) {
        return;
      }

      if (isSyncNeeded(buildMode, errorCount)) {
        // Start sync once other events have finished (b/76017112).
        requestSyncAfterBuild(TRIGGER_BUILD_SYNC_NEEDED_AFTER_BUILD);
      }

      if (isSyncRequestedDuringBuild(myProject)) {
        setSyncRequestedDuringBuild(myProject, null);
        // Sync was invoked while the project was built. Now that the build is finished, request a full sync after previous events have
        // finished (b/76017112).
        requestSyncAfterBuild(TRIGGER_USER_REQUEST_WHILE_BUILDING);
      }
    }
  }

  private void requestSyncAfterBuild(GradleSyncStats.Trigger trigger) {
    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(trigger);
    runWhenEventsFinished(() -> {
      if (!myProject.isDisposed()) {
        GradleSyncInvoker.getInstance().requestProjectSync(myProject, request);
      }
    });
  }

  private boolean isSyncNeeded(@Nullable BuildMode buildMode, int errorCount) {
    // Never sync if the build had errors, this will pull the focus of the Build tool window from build and make it much
    // harder to see what went wrong, especially if the resulting sync succeeds.
    if (errorCount != 0) {
      return false;
    }


    // The project build is doing a MAKE and the previous Gradle sync failed. It is likely that if the
    // project build is successful, Gradle sync will be successful too.
    if (DEFAULT_BUILD_MODE.equals(buildMode) && GradleSyncState.getInstance(myProject).lastSyncFailed()) {
      return true;
    }

    // If any build.gradle files or setting.gradle file was modified *after* last Gradle sync (we check file timestamps vs the
    // timestamp of the last Gradle sync.) We don't perform this check if project build is SOURCE_GEN because, in this case,
    // the project build was triggered by a Gradle sync (thus unlikely to have a stale model.) This sync is performed regardless the
    // build was successful or not. If isGradleSyncNeeded returns UNSURE, the previous sync may have failed, if this happened
    // an automatic sync should have been triggered already. No need to trigger a new one.
    if (!SOURCE_GEN.equals(buildMode) && GradleSyncState.getInstance(myProject).isSyncNeeded().equals(YES)) {
      return true;
    }

    return false;
  }

  /**
   * Run task once other pending events have finished.
   *
   * @param task to be run
   */
  private static void runWhenEventsFinished(@NotNull Runnable task) {
    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      application.invokeAndWait(task);
    }
    else {
      application.invokeLater(task);
    }
  }

  private static boolean unresolvedDependenciesFound(@NotNull String errorMessage) {
    return errorMessage.contains("Could not resolve all dependencies");
  }

  private void notifyUnresolvedDependenciesInOfflineMode() {
    NotificationHyperlink disableOfflineModeHyperlink = new NotificationHyperlink("disable.gradle.offline.mode", "Disable offline mode") {
      @Override
      protected void execute(@NotNull Project project) {
        GradleSettings.getInstance(myProject).setOfflineWork(false);
      }
    };
    String title = "Unresolved Dependencies";
    String text = "Unresolved dependencies detected while building project in offline mode. Please disable offline mode and try again.";
    AndroidNotification.getInstance(myProject).showBalloon(title, text, NotificationType.ERROR, disableOfflineModeHyperlink);
  }

  /**
   * Refreshes the cached view of the project's contents.
   */
  private void refreshProject(boolean asynchronous) {
    String projectPath = myProject.getBasePath();
    if (projectPath != null) {
      VirtualFile rootDir = LocalFileSystem.getInstance().findFileByPath(projectPath);
      if (rootDir != null && rootDir.isDirectory()) {
        rootDir.refresh(asynchronous, true);
      }
    }
  }
}
