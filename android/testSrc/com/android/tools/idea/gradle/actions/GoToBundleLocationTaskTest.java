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

import com.android.tools.idea.gradle.actions.GoToBundleLocationTask.OpenFolderNotificationListener;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.project.AndroidNotification;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.JavaProjectTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import org.gradle.tooling.BuildCancelledException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import static com.intellij.notification.NotificationType.INFORMATION;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link GoToBundleLocationTask}.
 */
public class GoToBundleLocationTaskTest extends JavaProjectTestCase {
  private static final String NOTIFICATION_TITLE = "Build Bundle(s)";

  @Mock private AndroidNotification myMockNotification;
  private GoToBundleLocationTask myTask;
  private File myBundleFilePath;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.initMocks(this);

    // Simulate the path of the bundle file for the project's module.
    myBundleFilePath = createTempDir("bundleFileLocation");
    Map<Module, File> modulesToPaths = Collections.singletonMap(getModule(), myBundleFilePath);

    myTask = new GoToBundleLocationTask(getProject(), NOTIFICATION_TITLE, null, modulesToPaths, null);
    ServiceContainerUtil
      .replaceService(myProject, AndroidNotification.class, myMockNotification, getTestRootDisposable());
  }

  public void testExecuteWithCancelledBuild() {
    String message = "Build cancelled.";
    GradleInvocationResult result = createBuildResult(new BuildCancelledException(message));
    myTask.execute(result);
    verify(myMockNotification).showBalloon(NOTIFICATION_TITLE, message, INFORMATION);
  }

  public void testExecuteWithFailedBuild() {
    String message = "Errors while building Bundle file. You can find the errors in the 'Messages' view.";
    myTask.execute(createBuildResult(new Throwable("Unknown error with gradle build")));
    verify(myMockNotification).showBalloon(NOTIFICATION_TITLE, message, NotificationType.ERROR);
  }

  public void testExecuteWithSuccessfulBuild() {
    Module module = getModule();

    myTask.execute(createBuildResult(null /* build successful - no errors */));
    if (RevealFileAction.isSupported()) {
      String moduleName = module.getName();
      String message = getModuleNotificationMessage(moduleName);
      Map<String, File> bundlePathsPerModule = Collections.singletonMap(moduleName, myBundleFilePath);
      verify(myMockNotification).showBalloon(NOTIFICATION_TITLE, message, INFORMATION,
                                             new OpenFolderNotificationListener(myProject, bundlePathsPerModule, null));
    }
  }

  @NotNull
  private static String getModuleNotificationMessage(@NotNull String moduleName) {
    return "App bundle(s) generated successfully:" + getModuleLineNotificationMessage(moduleName);
  }

  @NotNull
  private static String getModuleLineNotificationMessage(@NotNull String moduleName) {
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
  private static GradleInvocationResult createBuildResult(@Nullable Throwable buildError) {
    return new GradleInvocationResult(Collections.emptyList(), Collections.emptyList(), buildError);
  }
}
