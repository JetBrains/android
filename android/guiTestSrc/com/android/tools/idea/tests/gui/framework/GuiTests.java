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
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.list.ListPopupModel;
import org.fest.swing.core.BasicRobot;
import org.fest.swing.core.ComponentFinder;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JButtonMatcher;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.ComponentFixture;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.fest.swing.timing.Timeout;
import org.jetbrains.android.AndroidPlugin;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static java.util.concurrent.TimeUnit.MINUTES;
import static junit.framework.Assert.assertNotNull;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.finder.WindowFinder.findFrame;
import static org.fest.swing.timing.Pause.pause;
import static org.fest.swing.timing.Timeout.timeout;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class GuiTests {
  public static final Timeout SHORT_TIMEOUT = timeout(2, MINUTES);
  public static final Timeout LONG_TIMEOUT = timeout(5, MINUTES);

  public static final String GUI_TESTS_RUNNING_IN_SUITE_PROPERTY = "gui.tests.running.in.suite";
  public static final String GRADLE_2_2_HOME_PROPERTY = "gradle.2.2.home.path";
  public static final String GRADLE_1_12_HOME_PROPERTY = "gradle.1.12.home.path";

  /** Environment variable set by users to point to sources */
  public static final String AOSP_SOURCE_PATH = "AOSP_SOURCE_PATH";
  /** Older environment variable pointing to the sdk dir inside AOSP; checked for compatibility */
  public static final String ADT_SDK_SOURCE_PATH = "ADT_SDK_SOURCE_PATH";
  /** AOSP-relative path to directory containing GUI test data */
  public static final String RELATIVE_DATA_PATH = "tools/adt/idea/android/testData/guiTests".replace('/', File.separatorChar);

  // Called by IdeTestApplication via reflection.
  @SuppressWarnings("UnusedDeclaration")
  public static void setUpDefaultGeneralSettings() {
    AndroidPlugin.setGuiTestingMode(true);

    GeneralSettings.getInstance().setShowTipsOnStartup(false);
    setUpDefaultProjectCreationLocationPath();

    final File androidSdkPath = AndroidTestCaseHelper.getAndroidSdkPath();
    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            DefaultSdks.setDefaultAndroidHome(androidSdkPath, null);
          }
        });
      }
    });
  }

  public static void setUpDefaultProjectCreationLocationPath() {
    GeneralSettings.getInstance().setLastProjectCreationLocation(getProjectCreationDirPath().getPath());
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
  public static File getProjectCreationDirPath() {
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

  public static void deleteFile(@Nullable final VirtualFile file) {
    // File deletion must happen on UI thread under write lock
    if (file != null) {
      GuiActionRunner.execute(new GuiTask() {
        @Override
        protected void executeInEDT() throws Throwable {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              try {
                file.delete(this);
              }
              catch (IOException e) {
                // ignored
              }
            }
          });
        }
      });
    }
  }

  /** Waits until an IDE popup is shown (and returns it */
  public static JBList waitForPopup(@NotNull Robot robot) {
    return waitUntilFound(robot, null, new GenericTypeMatcher<JBList>(JBList.class) {
      @Override
      protected boolean isMatching(JBList list) {
        ListModel model = list.getModel();
        return model instanceof ListPopupModel;
      }
    });
  }

  /**
   * Clicks an IntelliJ/Studio popup menu item with the given label
   *
   * @param labelPrefix the target menu item label
   * @param component a component in the same window that the popup menu is associated with
   * @param robot the robot to drive it with
   */
  public static void clickPopupMenuItem(@NotNull String labelPrefix, @NotNull Component component, @NotNull Robot robot) {
    // IntelliJ doesn't seem to use a normal JPopupMenu, so this won't work:
    //    JPopupMenu menu = myRobot.findActivePopupMenu();
    // Instead, it uses a JList (technically a JBList), which is placed somewhere
    // under the root pane.

    Container root = getRootContainer(component);

    // First fine the JBList which holds the popup. There could be other JBLists in the hierarchy,
    // so limit it to one that is actually used as a popup, as identified by its model being a ListPopupModel:
    JBList list = robot.finder().find(root, new GenericTypeMatcher<JBList>(JBList.class) {
      @Override
      protected boolean isMatching(JBList list) {
        ListModel model = list.getModel();
        return model instanceof ListPopupModel;
      }
    });

    // We can't use the normal JListFixture method to click by label since the ListModel items are
    // ActionItems whose toString does not reflect the text, so search through the model items instead:
    ListPopupModel model = (ListPopupModel)list.getModel();
    java.util.List<String> items = Lists.newArrayList();
    for (int i = 0; i < model.getSize(); i++) {
      Object elementAt = model.getElementAt(i);
      if (elementAt instanceof PopupFactoryImpl.ActionItem) {
        PopupFactoryImpl.ActionItem item = (PopupFactoryImpl.ActionItem)elementAt;
        String s = item.getText();
        if (s.startsWith(labelPrefix)) {
          new JListFixture(robot, list).clickItem(i);
          return;
        }
        items.add(s);
      } else { // For example package private class IntentionActionWithTextCaching used in quickfix popups
        String s = elementAt.toString();
        if (s.startsWith(labelPrefix)) {
          new JListFixture(robot, list).clickItem(i);
          return;
        }
        items.add(s);
      }
    }

    if (items.isEmpty()) {
      fail("Could not find any menu items in popup");
    }
    fail("Did not find menu item with prefix '" + labelPrefix + "' among " + Joiner.on(", ").join(items));
  }

  /** Returns the root container containing the given component */
  @Nullable
  public static Container getRootContainer(@NotNull final Component component) {
    return GuiActionRunner.execute(new GuiQuery<Container>() {
      @Override
      @Nullable
      protected Container executeInEDT() throws Throwable {
        return (Container)SwingUtilities.getRoot(component);
      }
    });
  }

  public static void findAndClickOkButton(@NotNull ComponentFixture<? extends Container> container) {
    findAndClickButton(container, "OK");
  }

  public static void findAndClickCancelButton(@NotNull ComponentFixture<? extends Container> container) {
    findAndClickButton(container, "Cancel");
  }

  public static void findAndClickButton(@NotNull ComponentFixture<? extends Container> container, @NotNull String text) {
    Robot robot = container.robot;
    JButton button = robot.finder().find(container.target, JButtonMatcher.withText(text).andShowing());
    robot.click(button);
  }

  /** Returns a full path to the GUI data directory in the user's AOSP source tree, if known, or null */
  @Nullable
  public static File getTestDataDir() {
    File aosp = getAospSourceDir();
    return aosp != null ? new File(aosp, RELATIVE_DATA_PATH) : null;
  }

  /**
   * Returns a full path to the user's AOSP source tree (e.g. the directory expected to
   * contain tools/adt/idea etc including the GUI tests
   */
  @Nullable
  public static File getAospSourceDir() {
    // If running tests from the IDE, we can find the AOSP directly without user environment variable help
    File home = new File(PathManager.getHomePath());
    if (home.exists()) {
      File parentFile = home.getParentFile();
      if (parentFile != null && "tools".equals(parentFile.getName())) {
        return parentFile.getParentFile();
      }
    }

    String aosp = System.getenv(AOSP_SOURCE_PATH);
    if (aosp == null) {
      String sdk = System.getenv(ADT_SDK_SOURCE_PATH);
      if (sdk != null) {
        aosp = sdk + File.separator + "..";
      }
    }
    if (aosp != null) {
      File dir = new File(aosp);
      assertTrue(dir.getPath() + " (pointed to by " + AOSP_SOURCE_PATH + " or " + ADT_SDK_SOURCE_PATH + " does not exist", dir.exists());
      return dir;
    }

    return null;
  }

  /** Waits for a first component which passes the given matcher to become visible */
  @NotNull
  public static <T extends Component> T waitUntilFound(@NotNull final Robot robot, @NotNull final GenericTypeMatcher<T> matcher) {
    return waitUntilFound(robot, null, matcher);
  }

  /** Waits for a first component which passes the given matcher under the given root to become visible. */
  @NotNull
  public static <T extends Component> T waitUntilFound(@NotNull final Robot robot,
                                                       @Nullable final Container root,
                                                       @NotNull final GenericTypeMatcher<T> matcher) {
    final AtomicReference<T> reference = new AtomicReference<T>();
    Pause.pause(new Condition("Find component using " + matcher.toString()) {
      @Override
      public boolean test() {
        ComponentFinder finder = robot.finder();
        Collection<T> allFound = root != null ? finder.findAll(root, matcher) : finder.findAll(matcher);
        boolean found = allFound.size() == 1;
        if (found) {
          reference.set(getFirstItem(allFound));
        } else if (allFound.size() > 1) {
          // Only allow a single component to be found, otherwise you can get some really confusing
          // test failures; the matcher should pick a specific enough instance
          fail("Found more than one " + matcher.supportedType().getSimpleName() + " which matches the criteria: " + allFound);
        }
        return found;
      }
    }, SHORT_TIMEOUT);

    return reference.get();
  }

  /** Waits until no components match the given criteria under the given root */
  public static <T extends Component> void waitUntilGone(@NotNull final Robot robot,
                                                         @NotNull final Container root,
                                                         @NotNull final GenericTypeMatcher<T> matcher) {
    Pause.pause(new Condition("Find component using " + matcher.toString()) {
      @Override
      public boolean test() {
        Collection<T> allFound = robot.finder().findAll(root, matcher);
        return allFound.isEmpty();
      }
    }, SHORT_TIMEOUT);
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
