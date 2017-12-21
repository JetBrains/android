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

import com.android.testutils.TestUtils;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.tests.gui.framework.matcher.FluentMatcher;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.ui.GuiTestingService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.diagnostic.AbstractMessage;
import com.intellij.diagnostic.MessagePool;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.diagnostic.FrequentEventDetector;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.list.ListPopupModel;
import com.intellij.util.net.HttpConfigurable;
import org.fest.swing.core.BasicRobot;
import org.fest.swing.core.ComponentFinder;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.*;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AssumptionViolatedException;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static com.google.common.base.Joiner.on;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.io.Files.createTempDir;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static org.fest.swing.finder.WindowFinder.findFrame;
import static org.fest.util.Strings.quote;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class GuiTests {

  public static final String GUI_TESTS_RUNNING_IN_SUITE_PROPERTY = "gui.tests.running.in.suite";

  /**
   * Environment variable set by users to point to sources
   */
  private static final String AOSP_SOURCE_PATH = "AOSP_SOURCE_PATH";
  /**
   * Older environment variable pointing to the sdk dir inside AOSP; checked for compatibility
   */
  private static final String ADT_SDK_SOURCE_PATH = "ADT_SDK_SOURCE_PATH";
  /**
   * AOSP-relative path to directory containing GUI test data
   */
  private static final String RELATIVE_DATA_PATH = "tools/adt/idea/android-uitests/testData".replace('/', File.separatorChar);

  private static final EventQueue SYSTEM_EVENT_QUEUE = Toolkit.getDefaultToolkit().getSystemEventQueue();

  private static final File TMP_PROJECT_ROOT = createTempProjectCreationDir();

  @NotNull
  public static List<Error> fatalErrorsFromIde() {
    List<AbstractMessage> errorMessages = MessagePool.getInstance().getFatalErrors(true, true);
    List<Error> errors = new ArrayList<>(errorMessages.size());
    for (AbstractMessage errorMessage : errorMessages) {
      StringBuilder messageBuilder = new StringBuilder(errorMessage.getMessage());
      String additionalInfo = errorMessage.getAdditionalInfo();
      if (isNotEmpty(additionalInfo)) {
        messageBuilder.append(System.getProperty("line.separator")).append("Additional Info: ").append(additionalInfo);
      }
      Error error = new Error(messageBuilder.toString(), errorMessage.getThrowable());
      errors.add(error);
    }
    return Collections.unmodifiableList(errors);
  }

  static void setIdeSettings() {
    GradleExperimentalSettings.getInstance().SKIP_SOURCE_GEN_ON_PROJECT_SYNC = false;

    // Clear HTTP proxy settings, in case a test changed them.
    HttpConfigurable ideSettings = HttpConfigurable.getInstance();
    ideSettings.USE_HTTP_PROXY = false;
    ideSettings.PROXY_HOST = "";
    ideSettings.PROXY_PORT = 80;

    GuiTestingService.GuiTestSuiteState state = GuiTestingService.getInstance().getGuiTestSuiteState();
    state.setSkipSdkMerge(false);

    // Clear saved Wizard settings to its initial defaults
    PropertiesComponent.getInstance().setValue("SAVED_PROJECT_KOTLIN_SUPPORT", false); // New Project "Include Kotlin Support"
    PropertiesComponent.getInstance().setValue("SAVED_RENDER_LANGUAGE", "Java"); // New Activity "Source Language"

    FrequentEventDetector.disableUntil(() -> {/* pigs fly */});

    // TODO: setUpDefaultGeneralSettings();
  }

  // Called by IdeTestApplication via reflection.
  @SuppressWarnings("unused")
  public static void setUpDefaultGeneralSettings() {
    GuiTestingService.getInstance().setGuiTestingMode(true);
    GeneralSettings.getInstance().setShowTipsOnStartup(false);
  }

  public static void setUpSdks() {
    File androidSdkPath = TestUtils.getSdk();

    GuiTask.execute(
      () -> {
        IdeSdks ideSdks = IdeSdks.getInstance();
        File currentAndroidSdkPath = ideSdks.getAndroidSdkPath();
        if (!filesEqual(androidSdkPath, currentAndroidSdkPath)) {
          ApplicationManager.getApplication().runWriteAction(
            () -> {
              System.out.println(String.format("Setting Android SDK: '%1$s'", androidSdkPath.getPath()));
              ideSdks.setAndroidSdkPath(androidSdkPath, null);

              ideSdks.setUseEmbeddedJdk();
              System.out.println(String.format("Setting JDK: '%1$s'", ideSdks.getJdkPath()));

              System.out.println();
            });
        }
      });
  }

  public static void refreshFiles() {
    ApplicationManager.getApplication().invokeAndWait(() -> LocalFileSystem.getInstance().refresh(false));
  }

  /**
   * @return the path of the installation of a supported Gradle version. The path of the installation is specified via the system property
   * 'supported.gradle.home.path'. If the system property is not found, the test invoking this method will be skipped.
   */
  @NotNull
  public static File getGradleHomePathOrSkipTest() {
    return getFilePathPropertyOrSkipTest("supported.gradle.home.path", "the path of a local Gradle 2.2.1 distribution", true);
  }

  /**
   * @return the path of the installation of a Gradle version that is no longer supported by the IDE. The path of the installation is
   * specified via the system property 'unsupported.gradle.home.path'. If the system property is not found, the test invoking this method
   * will be skipped.
   */
  @NotNull
  public static File getUnsupportedGradleHomeOrSkipTest() {
    return getGradleHomeFromSystemPropertyOrSkipTest("unsupported.gradle.home.path", "2.1");
  }

  /**
   * Returns the Gradle installation directory whose path is specified in the given system property. If the expected system property is not
   * found, the test invoking this method will be skipped.
   *
   * @param propertyName  the name of the system property.
   * @param gradleVersion the version of the Gradle installation. This is used in the message displayed when the expected system property is
   *                      not found.
   * @return the Gradle installation directory whose path is specified in the given system property.
   */
  @NotNull
  public static File getGradleHomeFromSystemPropertyOrSkipTest(@NotNull String propertyName, @NotNull String gradleVersion) {
    String description = "the path of a Gradle " + gradleVersion + " distribution";
    return getFilePathPropertyOrSkipTest(propertyName, description, true);
  }

  /**
   * Returns a file whose path is specified in the given system property. If the expected system property is not found, the test invoking \
   * this method will be skipped.
   *
   * @param propertyName the name of the system property.
   * @param description  the description of the path to get. This is used in the message displayed when the expected system property is not
   *                     found.
   * @param isDirectory  indicates whether the file is a directory.
   * @return a file whose path is specified in the given system property.
   */
  @NotNull
  public static File getFilePathPropertyOrSkipTest(@NotNull String propertyName, @NotNull String description, boolean isDirectory) {
    String pathValue = System.getProperty(propertyName);
    File path = null;
    if (!isNullOrEmpty(pathValue)) {
      File tempPath = new File(pathValue);
      if (isDirectory && tempPath.isDirectory() || !isDirectory && tempPath.isFile()) {
        path = tempPath;
      }
    }

    if (path == null) {
      skipTest("Please specify " + description + ", using system property " + quote(propertyName));
    }
    return path;
  }

  /**
   * Skips a test (the test will be marked as "skipped"). This method has the same effect as the {@code Ignore} annotation, with the
   * advantage of allowing tests to be skipped conditionally.
   */
  @Contract("_ -> fail")
  public static void skipTest(@NotNull String message) {
    throw new AssumptionViolatedException(message);
  }

  public static void setUpDefaultProjectCreationLocationPath() {
    FileUtilRt.delete(getProjectCreationDirPath());
    refreshFiles();
    RecentProjectsManager.getInstance().setLastProjectCreationLocation(getProjectCreationDirPath().getPath());
  }

  // Called by IdeTestApplication via reflection.
  @SuppressWarnings("UnusedDeclaration")
  public static void waitForIdeToStart() {
    GuiActionRunner.executeInEDT(false);
    Robot robot = null;
    try {
      robot = BasicRobot.robotWithCurrentAwtHierarchy();
      MyProjectManagerListener listener = new MyProjectManagerListener();
      findFrame(new GenericTypeMatcher<Frame>(Frame.class) {
        @Override
        protected boolean isMatching(@NotNull Frame frame) {
          if (frame instanceof IdeFrame) {
            if (frame instanceof IdeFrameImpl) {
              listener.myActive = true;
              ProjectManager.getInstance().addProjectManagerListener(listener);
            }
            return true;
          }
          return false;
        }
      }).withTimeout(TimeUnit.MINUTES.toMillis(2)).using(robot);

      // We know the IDE event queue was pushed in front of the AWT queue. Some JDKs will leave a dummy event in the AWT queue, which
      // we attempt to clear here. All other events, including those posted by the Robot, will go through the IDE event queue.
      try {
        if (SYSTEM_EVENT_QUEUE.peekEvent() != null) {
          SYSTEM_EVENT_QUEUE.getNextEvent();
        }
      }
      catch (InterruptedException ex) {
        // Ignored.
      }

      if (listener.myActive) {
        Wait.seconds(1).expecting("project to be opened")
          .until(() -> {
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
          });
      }
    }
    finally {
      GuiActionRunner.executeInEDT(true);
      if (robot != null) {
        robot.cleanUpWithoutDisposingWindows();
      }
    }
  }

  static ImmutableList<Window> windowsShowing() {
    ImmutableList.Builder<Window> listBuilder = ImmutableList.builder();
    for (Window window : Window.getWindows()) {
      if (window.isShowing()) {
        listBuilder.add(window);
      }
    }
    return listBuilder.build();
  }

  @NotNull
  public static File getProjectCreationDirPath() {
    return TMP_PROJECT_ROOT;
  }

  @NotNull
  public static File createTempProjectCreationDir() {
    try {
      // The temporary location might contain symlinks, such as /var@ -> /private/var on MacOS.
      // EditorFixture seems to require a canonical path when opening the file.
      return createTempDir().getCanonicalFile();
    }
    catch (IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @NotNull
  public static File getTestProjectsRootDirPath() {
    String testDataPath = PathManager.getHomePath() + "/../adt/idea/android-uitests";
    if (!new File(testDataPath).exists()) {
      testDataPath = PathManagerEx.findFileUnderCommunityHome("plugins/android").getPath();
    }
    testDataPath = toCanonicalPath(toSystemDependentName(testDataPath));
    return new File(testDataPath, "testData");
  }

  private GuiTests() {
  }

  public static void deleteFile(@Nullable VirtualFile file) {
    // File deletion must happen on UI thread under write lock
    if (file != null) {
      GuiTask.execute(() -> ApplicationManager.getApplication().runWriteAction(
        () -> {
          try {
            file.delete(GuiTests.class);
          }
          catch (IOException e) {
            // ignored
          }
        }
      ));
    }
  }

  /**
   * Waits until an IDE popup is shown and returns it.
   */
  public static JBList waitForPopup(@NotNull Robot robot) {
    return waitUntilFound(robot, null, new GenericTypeMatcher<JBList>(JBList.class) {
      @Override
      protected boolean isMatching(@NotNull JBList list) {
        ListModel model = list.getModel();
        return model instanceof ListPopupModel;
      }
    });
  }

  /**
   * Clicks an IntelliJ/Studio popup menu item with the given label prefix
   *
   * @param labelPrefix the target menu item label prefix
   * @param component   a component in the same window that the popup menu is associated with
   * @param robot       the robot to drive it with
   */
  public static void clickPopupMenuItem(@NotNull String labelPrefix, @NotNull Component component, @NotNull Robot robot) {
    clickPopupMenuItemMatching(s -> s.startsWith(labelPrefix), component, robot);
  }

  public static void clickPopupMenuItemMatching(@NotNull Predicate<String> predicate, @NotNull Component component, @NotNull Robot robot) {

    JPopupMenu menu = robot.findActivePopupMenu();
    if (menu != null) {
      new JPopupMenuFixture(robot, menu).menuItem(new GenericTypeMatcher<JMenuItem>(JMenuItem.class) {
        @Override
        protected boolean isMatching(@Nonnull JMenuItem component) {
          return predicate.test(component.getText());
        }
      }).click();
      return;
    }

    // IntelliJ doesn't seem to use a normal JPopupMenu, so this won't work:
    //    JPopupMenu menu = myRobot.findActivePopupMenu();
    // Instead, it uses a JList (technically a JBList), which is placed somewhere
    // under the root pane.

    Container root = GuiQuery.getNonNull(() -> (Container)SwingUtilities.getRoot(component));
    // First find the JBList which holds the popup. There could be other JBLists in the hierarchy,
    // so limit it to one that is actually used as a popup, as identified by its model being a ListPopupModel:
    JBList list = waitUntilShowing(robot, root, new GenericTypeMatcher<JBList>(JBList.class) {
      @Override
      protected boolean isMatching(@NotNull JBList list) {
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
      String s;
      if (elementAt instanceof PopupFactoryImpl.ActionItem) {
        s = ((PopupFactoryImpl.ActionItem)elementAt).getText();
      }
      else { // For example package private class IntentionActionWithTextCaching used in quickfix popups
        s = elementAt.toString();
      }

      if (predicate.test(s)) {
        new JListFixture(robot, list).clickItem(i);
        robot.waitForIdle();
        return;
      }
      items.add(s);
    }

    if (items.isEmpty()) {
      fail("Could not find any menu items in popup");
    }
    fail("Did not find the correct menu item among " + on(", ").join(items));
  }

  public static void findAndClickOkButton(@NotNull ContainerFixture<? extends Container> container) {
    findAndClickButtonWhenEnabled(container, "OK");
  }

  public static void findAndClickCancelButton(@NotNull ContainerFixture<? extends Container> container) {
    findAndClickButtonWhenEnabled(container, "Cancel");
  }

  public static void findAndClickButton(@NotNull ContainerFixture<? extends Container> container, @NotNull String text) {
    Robot robot = container.robot();
    new JButtonFixture(robot, GuiTests.waitUntilShowing(robot, container.target(), Matchers.byText(JButton.class, text))).click();
  }

  public static void findAndClickButtonWhenEnabled(@NotNull ContainerFixture<? extends Container> container, @NotNull String text) {
    Robot robot = container.robot();
    new JButtonFixture(robot, GuiTests.waitUntilShowingAndEnabled(robot, container.target(), Matchers.byText(JButton.class, text))).click();
  }

  public static void findAndClickLabel(@NotNull ContainerFixture<? extends Container> container, @NotNull String text) {
    Robot robot = container.robot();
    new JLabelFixture(robot, GuiTests.waitUntilShowing(robot, container.target(), Matchers.byText(JLabel.class, text))).click();
  }

  public static void findAndClickLabelWhenEnabled(@NotNull ContainerFixture<? extends Container> container, @NotNull String text) {
    Robot robot = container.robot();
    new JLabelFixture(robot, GuiTests.waitUntilShowingAndEnabled(robot, container.target(), Matchers.byText(JLabel.class, text))).click();
  }

  /**
   * Returns a full path to the GUI data directory in the user's AOSP source tree, if known, or null
   */
  @Nullable
  public static File getTestDataDir() {
    File aosp = getAospSourceDir();
    return aosp != null ? new File(aosp, RELATIVE_DATA_PATH) : null;
  }

  /**
   * @return a full path to the user's AOSP source tree (e.g. the directory expected to contain tools/adt/idea etc including the GUI tests.
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

  /**
   * Waits for a single AWT or Swing {@link Component} showing and matched by {@code matcher}.
   */
  @NotNull
  public static <T extends Component> T waitUntilShowing(@NotNull Robot robot, @NotNull GenericTypeMatcher<T> matcher) {
    return waitUntilShowing(robot, null, matcher);
  }

  /**
   * Waits for a single AWT or Swing {@link Component} showing and matched by {@code matcher} under {@code root}.
   */
  @NotNull
  public static <T extends Component> T waitUntilShowing(@NotNull Robot robot,
                                                         @Nullable Container root,
                                                         @NotNull GenericTypeMatcher<T> matcher) {
    return waitUntilFound(robot, root, FluentMatcher.wrap(matcher).andIsShowing());
  }

  /**
   * Waits for a single AWT or Swing {@link Component} showing and matched by {@code matcher} under {@code root}
   * up to {@code secondsToWait} seconds.
   */
  @NotNull
  public static <T extends Component> T waitUntilShowing(@NotNull Robot robot,
                                                         @Nullable Container root,
                                                         @NotNull GenericTypeMatcher<T> matcher,
                                                         long secondsToWait) {
    return waitUntilFound(robot, root, FluentMatcher.wrap(matcher).andIsShowing(), secondsToWait);
}

  /**
   * Waits for a single AWT or Swing {@link Component} showing, enabled and matched by {@code matcher} under {@code root}.
   */
  @NotNull
  public static <T extends Component> T waitUntilShowingAndEnabled(
    @NotNull Robot robot, @Nullable Container root, @NotNull GenericTypeMatcher<T> matcher) {
    return waitUntilShowing(robot, root, FluentMatcher.wrap(matcher).andIsEnabled());
  }

  /**
   * Waits for a single AWT or Swing {@link Component} showing, enabled and matched by {@code matcher} under {@code root}.
   */
  @NotNull
  public static <T extends Component> T waitUntilShowingAndEnabled(
    @NotNull Robot robot, @Nullable Container root, @NotNull GenericTypeMatcher<T> matcher, long secondsToWait) {
    return waitUntilShowing(robot, root, FluentMatcher.wrap(matcher).andIsEnabled(), secondsToWait);
  }

  /**
   * Waits for a single AWT or Swing {@link Component} matched by {@code matcher}.
   */
  @NotNull
  public static <T extends Component> T waitUntilFound(@NotNull Robot robot, @NotNull GenericTypeMatcher<T> matcher) {
    return waitUntilFound(robot, null, matcher);
  }

  /**
   * Waits for a single AWT or Swing {@link Component} matched by {@code matcher} under {@code root}.
   */
  @NotNull
  public static <T extends Component> T waitUntilFound(@NotNull Robot robot,
                                                       @Nullable Container root,
                                                       @NotNull GenericTypeMatcher<T> matcher) {
    return waitUntilFound(robot, root, matcher, 10);
  }

  /**
   * Waits for a single AWT or Swing {@link Component} matched by {@code matcher} under {@code root}.
   */
  @NotNull
  public static <T extends Component> T waitUntilFound(@NotNull Robot robot,
                                                       @Nullable Container root,
                                                       @NotNull GenericTypeMatcher<T> matcher,
                                                       long secondsToWait) {
    AtomicReference<T> reference = new AtomicReference<>();
    String typeName = matcher.supportedType().getSimpleName();
    Wait.seconds(secondsToWait).expecting("matching " + typeName)
      .until(() -> {
        ComponentFinder finder = robot.finder();
        Collection<T> allFound = root != null ? finder.findAll(root, matcher) : finder.findAll(matcher);
        boolean found = allFound.size() == 1;
        if (found) {
          reference.set(getFirstItem(allFound));
        }
        else if (allFound.size() > 1) {
          // Only allow a single component to be found, otherwise you can get some really confusing
          // test failures; the matcher should pick a specific enough instance
          fail("Found more than one " + matcher.supportedType().getSimpleName() + " which matches the criteria: " + allFound);
        }
        return found;
      });

    return reference.get();
  }

  /**
   * Waits until no components match the given criteria under the given root
   * Usage of this method is discouraged. Please use the version with default timeout, unless you really need greater values.
   */
  public static <T extends Component> void waitUntilGone(@NotNull Robot robot,
                                                         @NotNull Container root,
                                                         @NotNull GenericTypeMatcher<T> matcher,
                                                         int seconds) {
    String typeName = matcher.supportedType().getSimpleName();
    Wait.seconds(seconds).expecting("absence of matching " + typeName).until(() -> robot.finder().findAll(root, matcher).isEmpty());
  }

  /**
   * Waits until no components match the given criteria under the given root
   */
  public static <T extends Component> void waitUntilGone(@NotNull Robot robot,
                                                         @NotNull Container root,
                                                         @NotNull GenericTypeMatcher<T> matcher) {
    waitUntilGone(robot, root, matcher, 1);
  }

  public static void waitForBackgroundTasks(Robot robot) {
    waitForBackgroundTasks(robot, null);
  }

  public static void waitForBackgroundTasks(Robot robot, @Nullable Wait wait) {
    if (wait == null) {
      // A 90-second default limit is high, but the first test in the suite may need it.
      wait = Wait.seconds(90);
    }

    wait
      .expecting("background tasks to finish")
      .until(() -> {
        robot.waitForIdle();

        ProgressManager progressManager = ProgressManager.getInstance();
        return !progressManager.hasModalProgressIndicator() &&
               !progressManager.hasProgressIndicator() &&
               !progressManager.hasUnsafeProgressIndicator();
      });
  }

  /**
   * Pretty-prints the given table fixture
   */
  @NotNull
  public static String tableToString(@NotNull JTableFixture table) {
    return tableToString(table, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 40);
  }

  /**
   * Pretty-prints the given table fixture
   */
  @NotNull
  public static String tableToString(@NotNull JTableFixture table, int startRow, int endRow, int startColumn, int endColumn,
                                     int cellWidth) {
    String[][] contents = table.contents();

    StringBuilder sb = new StringBuilder();
    String formatString = "%-" + Integer.toString(cellWidth) + "s";
    for (int row = Math.max(0, startRow); row < Math.min(endRow, contents.length); row++) {
      for (int column = Math.max(0, startColumn); column < Math.min(contents[0].length, endColumn); column++) {
        String cell = contents[row][column];
        if (cell.length() > cellWidth) {
          cell = cell.substring(0, cellWidth - 3) + "...";
        }
        sb.append(String.format(formatString, cell));
      }
      sb.append('\n');
    }

    return sb.toString();
  }

  /**
   * Pretty-prints the given list fixture
   */
  @NotNull
  public static String listToString(@NotNull JListFixture list) {
    return listToString(list, 0, Integer.MAX_VALUE, 40);
  }

  /**
   * Pretty-prints the given list fixture
   */
  @NotNull
  public static String listToString(@NotNull JListFixture list, int startRow, int endRow, int cellWidth) {
    String[] contents = list.contents();

    StringBuilder sb = new StringBuilder();
    String formatString = "%-" + Integer.toString(cellWidth) + "s";
    for (int row = Math.max(0, startRow); row < Math.min(endRow, contents.length); row++) {
      String cell = contents[row];
      if (cell.length() > cellWidth) {
        cell = cell.substring(0, cellWidth - 3) + "...";
      }
      sb.append(String.format(formatString, cell));
      sb.append('\n');
    }

    return sb.toString();
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
