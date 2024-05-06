/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.actions;

import static com.intellij.notification.NotificationType.INFORMATION;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tools.idea.gradle.actions.GoToBundleLocationTask.OpenFolderNotificationListener;
import com.android.tools.idea.gradle.project.build.invoker.AssembleInvocationResult;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.build.invoker.GradleMultiInvocationResult;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.PlatformTestCase;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.gradle.tooling.BuildCancelledException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link GoToBundleLocationTask}.
 */
public class GoToBundleLocationTaskTest extends PlatformTestCase {
  private static final String NOTIFICATION_TITLE = "Build Bundle(s)";
  boolean isShowFilePathActionSupported;
  @Mock private AndroidNotification myMockNotification;
  private GoToBundleLocationTask myTask;
  private Map<String, File> modulesToPaths;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.initMocks(this);
    List<Module> modules = Collections.singletonList(getModule());
    isShowFilePathActionSupported = true;
    // Simulate the path of the bundle file for the project's module.
    File myBundleFilePath = createTempDir("bundleFileLocation");
    modulesToPaths = Collections.singletonMap(getModule().getName(), myBundleFilePath);
    IdeComponents ideComponents = new IdeComponents(getProject());
    BuildsToPathsMapper mockGenerator = ideComponents.mockProjectService(BuildsToPathsMapper.class);
    when(mockGenerator.getBuildsToPaths(any(), any(), any(), anyBoolean())).thenReturn(modulesToPaths);
    myTask = new GoToBundleLocationTask(getProject(), modules, NOTIFICATION_TITLE) {
      @Override
      boolean isShowFilePathActionSupported() {
        return isShowFilePathActionSupported;  // Inject ability to simulate both behaviors.
      }
    };
    ideComponents.replaceProjectService(AndroidNotification.class, myMockNotification);
  }

  public void testExecuteWithCancelledBuild() {
    String message = "Build cancelled.";
    AssembleInvocationResult result = createBuildResult(new BuildCancelledException(message));
    myTask.executeWhenBuildFinished(Futures.immediateFuture(result));
    verify(myMockNotification).showBalloon(NOTIFICATION_TITLE, message, INFORMATION);
  }

  public void testExecuteWithFailedBuild() {
    String message = "Errors while building Bundle file. You can find the errors in the 'Build' view.";
    myTask.executeWhenBuildFinished(Futures.immediateFuture(createBuildResult(new Throwable("Unknown error with gradle build"))));
    verify(myMockNotification).showBalloon(NOTIFICATION_TITLE, message, NotificationType.ERROR);
  }

  public void testExecuteWithSuccessfulBuild() {
    myTask.executeWhenBuildFinished(Futures.immediateFuture(createBuildResult(null /* build successful - no errors */)));
    String moduleName = getModule().getName();
    String message = getExpectedModuleNotificationMessage(moduleName);
    verify(myMockNotification).showBalloon(NOTIFICATION_TITLE, message, INFORMATION,
                                           new OpenFolderNotificationListener(myProject, modulesToPaths));
  }

  public void testExecuteWithSuccessfulBuildNoShowFilePathAction() {
    isShowFilePathActionSupported = false;
    myTask.executeWhenBuildFinished(Futures.immediateFuture(createBuildResult(null /* build successful - no errors */)));
    String message = getExpectedModuleNotificationMessageNoShowFilePathAction();
    verify(myMockNotification).showBalloon(NOTIFICATION_TITLE, message, INFORMATION, new GoToBundleLocationTask.OpenEventLogHyperlink());
  }

  @NotNull
  private static String getExpectedModuleNotificationMessage(@NotNull String moduleName) {
    return "App bundle(s) generated successfully for 1 module:" + getExpectedModuleLineNotificationMessage(moduleName);
  }


  @NotNull
  private static String getExpectedModuleLineNotificationMessage(@NotNull String moduleName) {
    return "<br/>Module '" +
           moduleName +
           "': <a href=\"" +
           GoToBundleLocationTask.LOCATE_URL_PREFIX +
           moduleName +
           "\">locate</a> or " +
           "<a href=\"" +
           GoToBundleLocationTask.ANALYZE_URL_PREFIX +
           moduleName +
           "\">analyze</a> the app bundle.";
  }

  @NotNull
  private static String getExpectedModuleNotificationMessageNoShowFilePathAction() {
    return "App bundle(s) generated successfully for 1 module";
  }

  @NotNull
  private AssembleInvocationResult createBuildResult(@Nullable Throwable buildError) {
    return new AssembleInvocationResult(
      new GradleMultiInvocationResult(
        ImmutableList.of(
          new GradleInvocationResult(new File(getProject().getBasePath()), Collections.emptyList(), buildError)
        )),
      BuildMode.BUNDLE);
  }
}
