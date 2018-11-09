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
import com.android.tools.idea.gradle.actions.GoToApkLocationTask.OpenEventLogHyperlink;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.IdeaTestCase;
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
public class GoToApkLocationTaskTest extends IdeaTestCase {
  private static final String NOTIFICATION_TITLE = "Build APK";

  @Mock private AndroidNotification myMockNotification;
  private GoToApkLocationTask myTask;
  boolean isShowFilePathActionSupported = true;
  private File myApkPath;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.initMocks(this);

    // Simulate the path of the APK for the project's module.
    myApkPath = createTempDir("apkLocation");
    Map<Module, File> modulesToPaths = Collections.singletonMap(getModule(), myApkPath);

    myTask = new GoToApkLocationTask(getProject(), modulesToPaths, null, NOTIFICATION_TITLE) {
      @Override
      boolean isShowFilePathActionSupported() {
        return isShowFilePathActionSupported;  // Inject ability to simulate both behaviors.
      }
    };
    new IdeComponents(myProject).replaceProjectService(AndroidNotification.class, myMockNotification);
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
    String moduleName = module.getName();
    String message = getExpectedModuleNotificationMessage(moduleName, null);
    Map<String, File> apkPathsPerModule = Collections.singletonMap(moduleName, myApkPath);
    verify(myMockNotification).showBalloon(NOTIFICATION_TITLE, message, INFORMATION,
                                           new OpenFolderNotificationListener(apkPathsPerModule, myProject));
  }

  public void testExecuteWithSuccessfulBuildNoShowFilePathAction() {
    isShowFilePathActionSupported = false;

    myTask.execute(createBuildResult(null /* build successful - no errors */));
    String message = getExpectedModuleNotificationMessageNoShowFilePathAction();
    verify(myMockNotification).showBalloon(NOTIFICATION_TITLE, message, INFORMATION,
                                           new OpenEventLogHyperlink());
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

    myTask = new GoToApkLocationTask(getProject(), modulesToPaths, null, NOTIFICATION_TITLE) {
      @Override
      boolean isShowFilePathActionSupported() {
        return isShowFilePathActionSupported;  // Inject ability to simulate both behaviors.
      }
    };

    myTask.execute(createBuildResult(null /* build successful - no errors */));
    String moduleName = module.getName();
    String featureModuleName = featureModule.getName();
    Map<String, File> apkPathsPerModule = new HashMap<>();
    apkPathsPerModule.put(moduleName, myApkPath);
    apkPathsPerModule.put(featureModuleName, featureApkPath);
    String message = getExpectedModuleNotificationMessage(featureModuleName, moduleName);
    verify(myMockNotification).showBalloon(NOTIFICATION_TITLE, message, INFORMATION,
                                           new OpenFolderNotificationListener(apkPathsPerModule, myProject));
  }

  @NotNull
  private static String getExpectedModuleNotificationMessage(@NotNull String moduleName, @Nullable String module2Name) {
    if (module2Name == null) {
      return "APK(s) generated successfully for 1 module:" + getExpectedModuleLineNotificationMessage(moduleName);
    }
    else {
      return "APK(s) generated successfully for 2 modules:" + getExpectedModuleLineNotificationMessage(moduleName) +
             getExpectedModuleLineNotificationMessage(module2Name);
    }
  }

  @NotNull
  private static String getExpectedModuleLineNotificationMessage(@NotNull String moduleName) {
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
  private static String getExpectedModuleNotificationMessageNoShowFilePathAction() {
    return "APK(s) generated successfully for 1 module.";
  }

  @NotNull
  private static GradleInvocationResult createBuildResult(@Nullable Throwable buildError) {
    return new GradleInvocationResult(Collections.emptyList(), Collections.emptyList(), buildError);
  }
}
