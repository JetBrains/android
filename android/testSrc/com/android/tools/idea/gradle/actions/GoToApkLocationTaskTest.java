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

import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.notification.NotificationType;
import com.intellij.testFramework.IdeaTestCase;
import org.gradle.tooling.BuildCancelledException;

import java.io.File;
import java.util.Collections;

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link GoToApkLocationTask}.
 */
public class GoToApkLocationTaskTest extends IdeaTestCase {
  private GoToApkLocationTask myTask;
  private AndroidGradleNotification myMockNotification;
  private AndroidGradleNotification myOriginalNotification;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myTask = new GoToApkLocationTask("Build APK", getModule(), null);
    myOriginalNotification = AndroidGradleNotification.getInstance(myProject);
    myMockNotification = IdeComponents.replaceServiceWithMock(myProject, AndroidGradleNotification.class);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myOriginalNotification != null) {
        IdeComponents.replaceService(myProject, AndroidGradleNotification.class, myOriginalNotification);
      }
    }
    finally {
      super.tearDown();
    }
  }

  public void testExecuteWithCancelledBuild() {
    String message = "Build cancelled.";
    GradleInvocationResult result =
      new GradleInvocationResult(Collections.EMPTY_LIST, Collections.EMPTY_LIST, new BuildCancelledException(message));
    myTask.execute(result);
    verify(myMockNotification).showBalloon("Build APK", message, NotificationType.INFORMATION);
  }

  public void testExecuteWithFailedBuild() {
    String message = "Errors while building APK. You can find the errors in the 'Messages' view.";
    GradleInvocationResult result =
      new GradleInvocationResult(Collections.EMPTY_LIST, Collections.EMPTY_LIST, new Throwable("Unknown error with gradle build"));
    myTask.execute(result);
    verify(myMockNotification).showBalloon("Build APK", message, NotificationType.ERROR);
  }

  public void testExecuteWithSuccessfulBuild() {
    String message = "APK(s) generated successfully.";
    GradleInvocationResult result = new GradleInvocationResult(Collections.EMPTY_LIST, Collections.EMPTY_LIST, null);
    myTask.execute(result);
    if (ShowFilePathAction.isSupported()) {
      verify(myMockNotification)
        .showBalloon(eq("Build APK"), eq(message), eq(NotificationType.INFORMATION), any(GoToApkLocationTask.GoToPathHyperlink.class));
    }
    else {
      File moduleFilePath = new File(toSystemDependentName(getModule().getModuleFilePath()));
      File apkPath = moduleFilePath.getParentFile();
      message = String.format("APK(s) location is\n%1$s.", apkPath.getPath());
      verify(myMockNotification).showBalloon("Build APK", message, NotificationType.INFORMATION);
    }
  }
}
