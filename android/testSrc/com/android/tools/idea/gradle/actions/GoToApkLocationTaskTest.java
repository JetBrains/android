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

import static com.intellij.notification.NotificationType.INFORMATION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tools.idea.gradle.actions.GoToApkLocationTask.OpenFolderNotificationListener;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.PlatformTestCase;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.gradle.tooling.BuildCancelledException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link GoToApkLocationTask}.
 */
public class GoToApkLocationTaskTest extends PlatformTestCase {
  private static final String NOTIFICATION_TITLE = "Build APK";
  private boolean isShowFilePathActionSupported;
  @Mock private AndroidNotification myMockNotification;
  private GoToApkLocationTask myTask;
  private File myApkPath;
  private List<Module> modules;
  private SortedMap<String, File> modulesToPaths;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.initMocks(this);
    isShowFilePathActionSupported = true;
    modules = new ArrayList<>();
    // Simulate the path of the APK for the project's module.
    myApkPath = createTempDir("apkLocation");
    modulesToPaths = new TreeMap<>();
    modulesToPaths.put(getModule().getName(), myApkPath);
    modules.add(getModule());
    IdeComponents ideComponents = new IdeComponents(getProject());
    BuildsToPathsMapper mockGenerator = ideComponents.mockProjectService(BuildsToPathsMapper.class);
    when(mockGenerator.getBuildsToPaths(any(), any(), any(), anyBoolean(), any())).thenReturn(modulesToPaths);
    myTask = new GoToApkLocationTask(getProject(), modules, NOTIFICATION_TITLE) {
      @Override
      boolean isShowFilePathActionSupported() {
        return isShowFilePathActionSupported;  // Inject ability to simulate both behaviors.
      }
    };
    ideComponents.replaceProjectService(AndroidNotification.class, myMockNotification);
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
    myTask.execute(createBuildResult(null /* build successful - no errors */));
    String moduleName = getModule().getName();
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
                                           new GoToApkLocationTask.OpenEventLogHyperlink());
  }

  public void testExecuteWithSuccessfulBuildOfDynamicApp() throws IOException {
    // Create and setup dynamic feature module
    Module featureModule = createModule("feature1");
    modules.add(featureModule);
    // Simulate the path of the APK for the project's module.
    File featureApkPath = createTempDir("featureApkLocation");
    modulesToPaths.put(featureModule.getName(), featureApkPath);
    myTask.execute(createBuildResult(null /* build successful - no errors */));
    String moduleName = getModule().getName();
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
    return "APK(s) generated successfully for 1 module";
  }

  @NotNull
  private static GradleInvocationResult createBuildResult(@Nullable Throwable buildError) {
    return new GradleInvocationResult(Collections.emptyList(), buildError);
  }
}
