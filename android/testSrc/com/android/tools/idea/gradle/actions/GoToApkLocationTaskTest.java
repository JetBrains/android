/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.gradle.actions.GoToApkLocationTask.OpenFolderNotificationListener;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.project.AndroidNotification;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.JavaProjectTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import org.gradle.tooling.BuildCancelledException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.intellij.notification.NotificationType.INFORMATION;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link GoToApkLocationTask}.
 */
public class GoToApkLocationTaskTest extends JavaProjectTestCase {
  private static final String NOTIFICATION_TITLE = "Build APK";

  @Mock private AndroidNotification myMockNotification;
  private GoToApkLocationTask myTask;
  private File myApkPath;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.initMocks(this);

    // Simulate the path of the APK for the project's module.
    myApkPath = createTempDir("apkLocation");
    Map<Module, File> modulesToPaths = Collections.singletonMap(getModule(), myApkPath);

    myTask = new GoToApkLocationTask(getProject(), modulesToPaths, null, NOTIFICATION_TITLE);
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
    String message = "Errors while building APK. You can find the errors in the 'Messages' view.";
    myTask.execute(createBuildResult(new Throwable("Unknown error with gradle build")));
    verify(myMockNotification).showBalloon(NOTIFICATION_TITLE, message, NotificationType.ERROR);
  }

  public void testExecuteWithSuccessfulBuild() {
    Module module = getModule();

    myTask.execute(createBuildResult(null /* build successful - no errors */));
    if (RevealFileAction.isSupported()) {
      String moduleName = module.getName();
      String message = getModuleNotificationMessage(moduleName, null);
      Map<String, File> apkPathsPerModule = Collections.singletonMap(moduleName, myApkPath);
      verify(myMockNotification).showBalloon(NOTIFICATION_TITLE, message, INFORMATION,
                                             new OpenFolderNotificationListener(apkPathsPerModule, myProject));
    }
  }

  public void testExecuteWithSuccessfulBuildOfDynamicApp() throws IOException {
    Module module = getModule();

    // Create and setup dynamic feature module
    Module featureModule = createModule("feature1");

    // Simulate the path of the APK for the project's module.
    File featureApkPath = createTempDir("featureApkLocation");
    SortedMap<Module, File> modulesToPaths = new TreeMap<>((o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), false));
    modulesToPaths.put(getModule(), myApkPath);
    modulesToPaths.put(featureModule, featureApkPath);

    myTask = new GoToApkLocationTask(getProject(), modulesToPaths, null, NOTIFICATION_TITLE);

    myTask.execute(createBuildResult(null /* build successful - no errors */));
    if (RevealFileAction.isSupported()) {
      String moduleName = module.getName();
      String featureModuleName = featureModule.getName();
      Map<String, File> apkPathsPerModule = new HashMap<>();
      apkPathsPerModule.put(moduleName, myApkPath);
      apkPathsPerModule.put(featureModuleName, featureApkPath);
      String message = getModuleNotificationMessage(featureModuleName, moduleName);
      verify(myMockNotification).showBalloon(NOTIFICATION_TITLE, message, INFORMATION,
                                             new OpenFolderNotificationListener(apkPathsPerModule, myProject));
    }
  }

  @NotNull
  private static String getModuleNotificationMessage(@NotNull String moduleName, @Nullable String module2Name) {
    return "APK(s) generated successfully:" +
           getModuleLineNotificationMessage(moduleName) +
           (module2Name == null ? "" : getModuleLineNotificationMessage(module2Name));
  }

  @NotNull
  private static String getModuleLineNotificationMessage(@NotNull String moduleName) {
    return "<br/>Module '" +
           moduleName +
           "': <a href=\"" +
           GoToApkLocationTask.MODULE +
           moduleName +
           "\">locate</a> or <a href=\"" +
           GoToApkLocationTask.ANALYZE +
           moduleName +
           "\">analyze</a> the APK.";
  }

  @NotNull
  private static GradleInvocationResult createBuildResult(@Nullable Throwable buildError) {
    return new GradleInvocationResult(Collections.emptyList(), Collections.emptyList(), buildError);
  }
}
