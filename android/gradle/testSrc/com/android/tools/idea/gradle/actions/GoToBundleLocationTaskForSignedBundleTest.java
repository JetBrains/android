/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.intellij.testFramework.HeavyPlatformTestCase;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import org.gradle.tooling.BuildCancelledException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link GoToBundleLocationTask}.
 */
public class GoToBundleLocationTaskForSignedBundleTest extends HeavyPlatformTestCase {
  private static final String NOTIFICATION_TITLE = "Build Bundle(s)";
  private static final String buildVariant1 = "FreeDebug";
  private static final String buildVariant2 = "PaidDebug";
  private boolean isShowFilePathActionSupported;
  @Mock private AndroidNotification myMockNotification;
  private GoToBundleLocationTask myTask;
  private SortedMap<String, File> buildsToPaths;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.initMocks(this);
    isShowFilePathActionSupported = true;
    List<Module> modules = Collections.singletonList(getModule());
    List<String> buildVariants = new ArrayList<>();
    buildVariants.add(buildVariant1);
    buildVariants.add(buildVariant2);
    // Simulate the path of the bundle file for the project's module.
    File myBundleFilePath1 = createTempDir(buildVariant1 + "bundleFileLocation");
    File myBundleFilePath2 = createTempDir(buildVariant2 + "bundleFileLocation");
    buildsToPaths = new TreeMap<>();
    buildsToPaths.put(buildVariant1, myBundleFilePath1);
    buildsToPaths.put(buildVariant2, myBundleFilePath2);
    IdeComponents ideComponents = new IdeComponents(getProject());
    BuildsToPathsMapper mockGenerator = ideComponents.mockProjectService(BuildsToPathsMapper.class);
    when(mockGenerator.getBuildsToPaths(any(), any(), any(), anyBoolean())).thenReturn(buildsToPaths);
    myTask = new GoToBundleLocationTask(getProject(), modules, NOTIFICATION_TITLE, buildVariants) {
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
    String message = getExpectedModuleNotificationMessage(moduleName, buildVariant1, buildVariant2);
    verify(myMockNotification).showBalloon(NOTIFICATION_TITLE, message, INFORMATION,
                                           new OpenFolderNotificationListener(myProject, buildsToPaths));
  }

  public void testExecuteWithSuccessfulBuildNoShowFilePathAction() {
    isShowFilePathActionSupported = false;
    myTask.executeWhenBuildFinished(Futures.immediateFuture(createBuildResult(null /* build successful - no errors */)));
    String message = getExpectedModuleNotificationMessageNoShowFilePathAction(getModule().getName());
    verify(myMockNotification).showBalloon(NOTIFICATION_TITLE, message, INFORMATION,
                                           new GoToBundleLocationTask.OpenEventLogHyperlink());
  }

  @NotNull
  private static String getExpectedModuleNotificationMessage(@NotNull String moduleName,
                                                             @Nullable String buildVariant1Name,
                                                             @Nullable String buildVariant2Name) {
    return "App bundle(s) generated successfully for module '" +
           moduleName +
           "' with 2 build variants:" +
           getExpectedModuleLineNotificationMessage(buildVariant1Name) +
           getExpectedModuleLineNotificationMessage(buildVariant2Name);
  }


  @NotNull
  private static String getExpectedModuleLineNotificationMessage(@NotNull String moduleName) {
    return "<br/>Build variant '" +
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
  private static String getExpectedModuleNotificationMessageNoShowFilePathAction(@NotNull String moduleName) {
    return "App bundle(s) generated successfully for module '" + moduleName + "' with 2 build variants";
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
