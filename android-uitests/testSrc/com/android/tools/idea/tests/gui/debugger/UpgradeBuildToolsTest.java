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
package com.android.tools.idea.tests.gui.debugger;

import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.BuildToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeSettingsDialogFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.dualView.TreeTableView;
import org.fest.reflect.exception.ReflectionError;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.Collections;
import java.util.Enumeration;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickLabelWhenEnabled;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.fest.reflect.core.Reflection.field;
import static org.fest.reflect.core.Reflection.method;
import static org.fest.swing.core.matcher.DialogMatcher.withTitle;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.swing.finder.WindowFinder.findDialog;

@RunWith(GuiTestRunner.class)
public class UpgradeBuildToolsTest extends DebuggerTestBase {

  @Rule public final NativeDebuggerGuiTestRule guiTest = new NativeDebuggerGuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final String PROJECT_DIR_NAME = "NdkHelloJni";
  private static final String OLD_BUILD_TOOLS_VERSION = "22.0.1";
  private static final String INSTALL_SDK_TOOLS_TAB = "SDK Tools";
  private static final String LOOK_FOR_UPDATES = "Looking for updates...";
  private static final String BUILD_TOOLS = "Android SDK Build-Tools";
  private static final String HYPER_LINK_TEXT = "Install Build Tools";
  private static final String GET_DISPLAY_NAME = "getDisplayName";
  private static final String CYCLE_STATE = "cycleState";
  private static final String GET_STATUS_STRING = "getStatusString";
  private static final String PARENT_TREE_NODE = "ParentTreeNode";
  private static final String MY_TITLE = "myTitle";
  private static final String NOT_INSTALLED = "Not installed";
  private static final String INSTALLED = "Installed";
  private static final String ERROR_MSG = "Failed to find Build Tools revision";
  private static final String SDK_QUICKFIX_TITLE = "SDK Quickfix Installation";
  private static final String FINISH = "Finish";

  /**
   * Verifies that NDK project successfully builds even if a build tools upgrade is required.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 300d5189-3d55-437b-94ab-b098785f9ad1
   * <p>
   *   <pre>
   *   Setup:
   *   1. Install NDK through SDK Manager.
   *   2. Install Build tools 22.0.1 (remove any other versions) and restart Android Studio.
   *   Test Steps:
   *   1. Import NdkHelloJni project.
   *   2. Create an emulator.
   *   3. Make sure the latest version of Build Tools is not installed:
   *      remove all installed versions, then install an old one, e.g. 22.0.1
   *   4. Check that build fails because new Build Tools revision is required.
   *   5. Install latest Build Tools revision.
   *   6. Build the project and check build is successful.
   *   </pre>
   */
  @Ignore("It is broken, may affect following tests.")
  @RunIn(TestGroup.QA)
  @Test
  public void upgradeBuildTools() throws Exception {
    IdeFrameFixture ideFrameFixture =
      guiTest.importProjectAndWaitForProjectSyncToFinish(PROJECT_DIR_NAME);

    uninstallAllBuildtools(ideFrameFixture);

    installBuildToolsWithVersion(ideFrameFixture, OLD_BUILD_TOOLS_VERSION);

    fixBuildToolsError(ideFrameFixture);
  }

  @After
  public void installLatestBuildToolsIfNot() {
    IdeSettingsDialogFixture ideSettingsDialogFixture = openSDKManagerSDKToolsTab(guiTest.ideFrame());
    ideSettingsDialogFixture.selectShowPackageDetails();

    TreeTableView treeTableView = GuiTests.waitUntilFound(guiTest.robot(),
                                                          ideSettingsDialogFixture.target(),
                                                          Matchers.byType(TreeTableView.class).andIsShowing());

    DefaultMutableTreeNode root = (DefaultMutableTreeNode)treeTableView.getTableModel().getRoot();

    for (Object object : Collections.list(root.children())) {
      if (object.getClass().getName().endsWith(PARENT_TREE_NODE)) {
        TreeNode parentNode = (TreeNode)object;

        String parentTitle = field(MY_TITLE)
          .ofType(String.class)
          .in(object)
          .get();

        if (parentTitle.contains(BUILD_TOOLS)) {
          Enumeration<TreeNode> children = parentNode.children();

          // Find the last child, which is the latest version of Build Tools.
          TreeNode lastChild = null;
          while (children.hasMoreElements()) {
            lastChild = children.nextElement();
          }

          String installStatus = method(GET_STATUS_STRING).
            withReturnType(String.class)
            .in(lastChild)
            .invoke();

          if (NOT_INSTALLED.equals(installStatus)) {
            method(CYCLE_STATE).in(lastChild).invoke();
            ideSettingsDialogFixture.clickOK();
            finishInstallUninstallProcess(guiTest.robot());
            break;
          } else {
            ideSettingsDialogFixture.clickOK();
          }
        }
      }
    }
  }

  private void uninstallAllBuildtools(@NotNull IdeFrameFixture ideFrameFixture) throws Exception{
    IdeSettingsDialogFixture ideSettingsDialogFixture = openSDKManagerSDKToolsTab(ideFrameFixture);

    TreeTableView treeTableView = GuiTests.waitUntilFound(guiTest.robot(),
                                                          ideSettingsDialogFixture.target(),
                                                          Matchers.byType(TreeTableView.class).andIsShowing());

    DefaultMutableTreeNode root = (DefaultMutableTreeNode)treeTableView.getTableModel().getRoot();
    for (Object object : Collections.list(root.children())) {
      try {
        String title = method(GET_DISPLAY_NAME)
          .withReturnType(String.class)
          .in(object)
          .invoke();

        if (title.contains(BUILD_TOOLS)) {
          String status =  method(GET_STATUS_STRING)
            .withReturnType(String.class)
            .in(object)
            .invoke();
          if (status.equals(INSTALLED)) {
            // Select to remove
            method(CYCLE_STATE).in(object).invoke();
          } else if (!status.equals(NOT_INSTALLED)) {
            // Select to upgrade.
            method(CYCLE_STATE).in(object).invoke();
            // Select again to uninstall all.
            method(CYCLE_STATE).in(object).invoke();
          }
          break;
        }
      } catch (ReflectionError e) {
      }
    }

    ideSettingsDialogFixture.clickOK();

    try {
      finishInstallUninstallProcess(guiTest.robot());
    } catch (Exception e) {
      // The latest Build tools is not installed, don't need to uninstall it. Just do nothing here.
    }
  }

  private void installBuildToolsWithVersion(@NotNull IdeFrameFixture ideFrameFixture,
                                            @NotNull String versionNumber) {
    IdeSettingsDialogFixture ideSettingsDialogFixture = openSDKManagerSDKToolsTab(ideFrameFixture);
    ideSettingsDialogFixture.selectShowPackageDetails();

    TreeTableView treeTableView = GuiTests.waitUntilFound(guiTest.robot(),
                                                          ideSettingsDialogFixture.target(),
                                                          Matchers.byType(TreeTableView.class).andIsShowing());

    DefaultMutableTreeNode root = (DefaultMutableTreeNode)treeTableView.getTableModel().getRoot();
    boolean found = false;
    for (Object object : Collections.list(root.children())) {
      if (object.getClass().getName().endsWith(PARENT_TREE_NODE)) {
        TreeNode parentNode = (TreeNode)object;

        String parentTitle = field(MY_TITLE)
          .ofType(String.class)
          .in(object)
          .get();

        if (parentTitle.contains(BUILD_TOOLS)) {
          Enumeration<TreeNode> children = parentNode.children();

          while (children.hasMoreElements()) {
            TreeNode child = children.nextElement();
            String title = method(GET_DISPLAY_NAME)
              .withReturnType(String.class)
              .in(child)
              .invoke();
            if (title.startsWith(versionNumber)) {
              method(CYCLE_STATE).in(child).invoke();
              found = true;
              break;
            }
          }

          if (found) {
            break;
          }
        }
      }
    }

    if (!found) {
      throw new ComponentLookupException("The given revision of Build Tools is not found", null);
    }

    ideSettingsDialogFixture.clickOK();
    finishInstallUninstallProcess(guiTest.robot());
  }

  private void fixBuildToolsError(@NotNull IdeFrameFixture ideFrameFixture) throws Exception {
    ideFrameFixture.requestProjectSync().waitForGradleProjectSyncToFail();

    BuildToolWindowFixture buildToolWindow = ideFrameFixture.getBuildToolWindow();
    ConsoleViewImpl consoleView = buildToolWindow.getGradleSyncConsoleView();
    buildToolWindow.findHyperlinkByTextAndClick(consoleView, HYPER_LINK_TEXT);

    DialogFixture downloadDialog = findDialog(withTitle(SDK_QUICKFIX_TITLE))
      .withTimeout(SECONDS.toMillis(30)).using(guiTest.robot());
    JButtonFixture finish = downloadDialog.button(withText(FINISH));
    Wait.seconds(120).expecting("Android source to be installed").until(finish::isEnabled);
    finish.click();

    ideFrameFixture.requestProjectSync().waitForGradleProjectSyncToFinish(Wait.seconds(30));
  }

  @NotNull
  private IdeSettingsDialogFixture openSDKManagerSDKToolsTab(@NotNull IdeFrameFixture ideFrameFixture) {
    IdeSettingsDialogFixture ideSettingsDialogFixture = ideFrameFixture.invokeSdkManager();
    findAndClickLabelWhenEnabled(ideSettingsDialogFixture, INSTALL_SDK_TOOLS_TAB);

    GuiTests.waitUntilFound(guiTest.robot(), ideSettingsDialogFixture.target(),
                            Matchers.byText(JBLabel.class, LOOK_FOR_UPDATES).andIsShowing(),
                            20);
    GuiTests.waitUntilGone(guiTest.robot(), ideSettingsDialogFixture.target(),
                           Matchers.byText(JBLabel.class, LOOK_FOR_UPDATES).andIsShowing(),
                           20);
    return ideSettingsDialogFixture;
  }
}
