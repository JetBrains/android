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
package com.android.tools.idea.tests.gui.framework;

import com.android.tools.idea.AndroidTestCaseHelper;
import com.android.tools.idea.sdk.DefaultSdks;
import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.fest.swing.core.BasicRobot;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Timeout;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.File;

import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static java.util.concurrent.TimeUnit.MINUTES;
import static junit.framework.Assert.assertNotNull;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.finder.WindowFinder.findFrame;
import static org.fest.swing.timing.Pause.pause;
import static org.fest.swing.timing.Timeout.timeout;

public final class GuiTests {
  public static final Timeout SHORT_TIMEOUT = timeout(2, MINUTES);
  public static final Timeout LONG_TIMEOUT = timeout(5, MINUTES);

  public static final String GUI_TESTS_RUNNING_IN_SUITE_PROPERTY = "gui.tests.running.in.suite";
  public static final String GRADLE_1_12_HOME_PROPERTY = "gradle.1.12.home.path";
  public static final String GRADLE_2_HOME_PROPERTY = "gradle.2.0.home.path";

  // Called by IdeTestApplication via reflection.
  @SuppressWarnings("UnusedDeclaration")
  public static void setUpDefaultGeneralSettings() {
    GeneralSettings.getInstance().setShowTipsOnStartup(false);
    setUpDefaultProjectCreationLocationPath();

    final File androidSdkPath = AndroidTestCaseHelper.getAndroidSdkPath();
    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            DefaultSdks.setDefaultAndroidHome(androidSdkPath);
          }
        });
      }
    });
  }

  public static void setUpDefaultProjectCreationLocationPath() {
    GeneralSettings.getInstance().setLastProjectCreationLocation(getProjectCreationLocationPath().getPath());
  }

  // Called by IdeTestApplication via reflection.
  @SuppressWarnings("UnusedDeclaration")
  public static void waitForIdeToStart() {
    Robot robot = null;
    try {
      robot = BasicRobot.robotWithCurrentAwtHierarchy();
      final MyProjectManagerListener listener = new MyProjectManagerListener();
      findFrame(new GenericTypeMatcher<Frame>(Frame.class) {
        @Override
        protected boolean isMatching(Frame frame) {
          if (frame instanceof IdeFrame) {
            if (frame instanceof IdeFrameImpl) {
              listener.myActive = true;
              ProjectManager.getInstance().addProjectManagerListener(listener);
            }
            return true;
          }
          return false;
        }
      }).withTimeout(LONG_TIMEOUT.duration()).using(robot);

      if (listener.myActive) {
        pause(new Condition("Project to be opened") {
          @Override
          public boolean test() {
            boolean notified = listener.myNotified;
            if (notified) {
              ProgressManager progressManager = ProgressManager.getInstance();
              boolean isIdle = !progressManager.hasModalProgressIndicator() &&
                               !progressManager.hasProgressIndicator() &&
                               !progressManager.hasUnsafeProgressIndicator();
              if (isIdle) {
                ProjectManager.getInstance().removeProjectManagerListener(listener);
              }
              return isIdle;
            }
            return false;
          }
        }, LONG_TIMEOUT);
      }
    }
    finally {
      if (robot != null) {
        robot.cleanUpWithoutDisposingWindows();
      }
    }
  }

  @NotNull
  public static File getProjectCreationLocationPath() {
    return new File(getTestProjectsRootDirPath(), "newProjects");
  }

  @NotNull
  public static File getTestProjectsRootDirPath() {
    String testDataPath = AndroidTestBase.getTestDataPath();
    assertNotNull(testDataPath);
    assertThat(testDataPath).isNotEmpty();
    testDataPath = toCanonicalPath(toSystemDependentName(testDataPath));
    return new File(testDataPath, "guiTests");
  }

  private GuiTests() {
  }

  private static class MyProjectManagerListener extends ProjectManagerAdapter {
    boolean myActive;
    boolean myNotified;

    @Override
    public void projectOpened(Project project) {
      myNotified = true;
    }
  }
}
