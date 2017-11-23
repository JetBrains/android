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
import com.android.tools.idea.tests.gui.framework.fixture.DeployTargetPickerDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeSettingsDialogFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.ui.dualView.TreeTableView;
import org.fest.reflect.exception.ReflectionError;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JButtonFixture;
import com.intellij.ui.components.JBLabel;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JTextArea;
import javax.swing.tree.DefaultMutableTreeNode;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickLabelWhenEnabled;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.fest.reflect.core.Reflection.method;
import static org.fest.swing.core.matcher.DialogMatcher.withTitle;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.swing.finder.WindowFinder.findDialog;

@RunWith(GuiTestRunner.class)
public class PackageUpgradeTest extends DebuggerTestBase {

  @Rule public final NativeDebuggerGuiTestRule guiTest = new NativeDebuggerGuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final String INSTALL_SDK_TOOLS_TAB = "SDK Tools";
  private static final String LOOK_FOR_UPDATES = "Looking for updates...";

  /**
   * Verifies that Android Studio does not let user proceed with native debugging if newer lldb
   * package is available.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 1b9d7d1d-be6e-48f3-96f7-7995492f1ece
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicCmakeAppForUI project.
   *   2. Create an emulator.
   *   3. Make sure the latest version of lldb is not installed.
   *   4. Set a breakpoint in native code.
   *   5. Debug on the emulator.
   *   Verify:
   *   1. User is prompted to install new lldb package and debugging proceeds only after installing
   *      the package.
   *   2. Check that the newer version’s revision number is a.b.* where a.b matches the revision of
   *      Android Studio.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void testWithOlderLldb() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest
        .importProjectAndWaitForProjectSyncToFinish("BasicCmakeAppForUI");

    emulator.createDefaultAVD(ideFrameFixture.invokeAvdManager());

    IdeSettingsDialogFixture ideSettingsDialogFixture = ideFrameFixture.invokeSdkManager();
    findAndClickLabelWhenEnabled(ideSettingsDialogFixture, INSTALL_SDK_TOOLS_TAB);

    GuiTests.waitUntilFound(guiTest.robot(), ideSettingsDialogFixture.target(),
                            Matchers.byText(JBLabel.class, LOOK_FOR_UPDATES).andIsShowing());
    GuiTests.waitUntilGone(guiTest.robot(), ideSettingsDialogFixture.target(),
                           Matchers.byText(JBLabel.class, LOOK_FOR_UPDATES).andIsShowing(),
                           20);

    unselectedLldbIfInstalled(ideSettingsDialogFixture);
    ideSettingsDialogFixture.clickOK();
    try {
      // Complete unstallation process.
      finishInstallUninstallProcess(guiTest.robot());
    } catch (Exception e) {
      // The latest lldb is not installed, don't need to uninstall it. Just do nothing here.
    }

    openAndToggleBreakPoints(ideFrameFixture,
                             "app/src/main/jni/native-lib.c",
                             "return (*env)->NewStringUTF(env, message);");

    ideFrameFixture.selectApp(DEBUG_CONFIG_NAME);
    ideFrameFixture.findDebugApplicationButton().click();

    // User is prompted to install new lldb package and debugging proceeds only after installing
    // the package.
    DialogFixture quickFixDialog = findDialog(withTitle("Quick fix"))
        .withTimeout(SECONDS.toMillis(30)).using(guiTest.robot());
    quickFixDialog.button(withText("Yes")).click();

    DialogFixture downloadDialog = findDialog(withTitle("SDK Quickfix Installation"))
        .withTimeout(SECONDS.toMillis(30)).using(guiTest.robot());
    JButtonFixture finish = downloadDialog.button(withText("Finish"));

    // Check that the newer version’s revision number is a.b.* where a.b matches the revision of
    // Android Studio.
    // Example of text content:
    // To install:
    // - LLDB 3.0 (lldb;3.0)
    // Preparing "Install LLDB 3.0 (revision: 3.0.4213617)".
    // Downloading https://dl.google.com/android/repository/lldb-3.0.4213617-linux-x86_64.zip
    JTextArea jTextArea = ideFrameFixture.robot().finder().findByType(downloadDialog.target(),
                                                                      JTextArea.class);
    String contents = jTextArea.getText();
    Pattern pattern = Pattern.compile("(revision:\\s\\d\\.\\d\\.\\d+)");
    Matcher matcher = pattern.matcher(contents);
    String lldbRevisionInfo = null;
    if (matcher.find()) {
      lldbRevisionInfo = matcher.group();
    } else {
      throw new RuntimeException("Cannot find expected digital pattern for full lldb revision.");
    }

    String lldbRevision = null;
    pattern = Pattern.compile("(\\d\\.\\d)");
    matcher = pattern.matcher(lldbRevisionInfo);
    if (matcher.find()) {
      lldbRevision = matcher.group();
    } else {
      throw new RuntimeException("Cannot find expected digital pattern for lldb revision.");
    }

    // In release build, revisions are mached;
    // In tot build, "b" in revision "a.b" doesn't always matched.
    String androidStudioRevision = ideFrameFixture.getRevision();
    if (!lldbRevision.equals(androidStudioRevision)) {
      if (!lldbRevision.split("\\.")[0]
          .equals(androidStudioRevision.split("\\.")[0])) {
        throw new RuntimeException("LLDB revision doesn't match Android Studio revision.");
      }
    }

    Wait.seconds(120)
        .expecting("Android source to be installed").until(finish::isEnabled);
    finish.click();

    DeployTargetPickerDialogFixture.find(ideFrameFixture.robot())
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();
  }

  private void unselectedLldbIfInstalled(
      @NotNull IdeSettingsDialogFixture ideSettingsDialogFixture) {
    GuiTests.waitUntilFound(guiTest.robot(),
                            ideSettingsDialogFixture.target(),
                            new GenericTypeMatcher<TreeTableView>(TreeTableView.class) {
        @Override
        protected boolean isMatching(TreeTableView treeTableView) {
          if(treeTableView.isShowing()) {
            DefaultMutableTreeNode root = (DefaultMutableTreeNode)treeTableView.getTableModel()
                .getRoot();
            for (Object object : Collections.list(root.children())) {
              try {
                String title = method("getDisplayName")
                    .withReturnType(String.class)
                    .in(object).invoke();
                if(title.equals("LLDB")) {
                  String status =  method("getStatusString")
                      .withReturnType(String.class)
                      .in(object).invoke();
                  if(status.equals("Installed")) {
                    method("cycleState").in(object).invoke();
                  }
                  return true;
                }
              } catch (ReflectionError e) {
              }
            }
            return false;
          }
          return false;
        }
    });
  }
}
