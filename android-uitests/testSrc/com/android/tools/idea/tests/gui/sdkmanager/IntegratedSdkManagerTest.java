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
package com.android.tools.idea.tests.gui.sdkmanager;

import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeSettingsDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.intellij.ui.dualView.TreeTableView;
import org.fest.reflect.exception.ReflectionError;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Collections;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickLabelWhenEnabled;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.fest.reflect.core.Reflection.method;
import static org.fest.swing.core.matcher.DialogMatcher.withTitle;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.swing.finder.WindowFinder.findDialog;

@RunWith(GuiTestRunner.class)
public class IntegratedSdkManagerTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String INSTALL_PACKAGE_TAB = "SDK Tools";
  private static final String INSTALL_PACKAGE = "GPU Debugging tools";

  /**
   * To verify that the new SDK Manager integrates into the Android Studio user interface,
   * and the user can update/download new SDK components without having to rely on the standalone SDK Manager.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14581573
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open Android Studio
   *   2. Open File > Settings > System Settings > Android SDK
   *   3. Select "SDK Tools" tab
   *   4. Select a package that is not pre-installed (we choose "GPU Debugging tools" here)
   *   5. Click OK
   *   6. Click yes to confirm
   *   7. Wait until the package is installed and click finish.
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void installPackage() throws Exception {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    IdeSettingsDialogFixture ideSettingsDialogFixture = ideFrameFixture.openIdeSettings().selectSdkPage();
    findAndClickLabelWhenEnabled(ideSettingsDialogFixture, INSTALL_PACKAGE_TAB);

    GuiTests.waitUntilFound(guiTest.robot(), ideSettingsDialogFixture.target(), new GenericTypeMatcher<TreeTableView>(TreeTableView.class) {
      @Override
      protected boolean isMatching(TreeTableView ttv) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode)ttv.getTableModel().getRoot();
        for (Object object : Collections.list(root.children())) {
          try {
            if (INSTALL_PACKAGE.equals(method("getDisplayName").withReturnType(String.class).in(object).invoke())) {
              assertThat(method("getStatusString").withReturnType(String.class).in(object).invoke()).isEqualTo("Not Installed");
              method("cycleState").in(object).invoke();
              return true;
            }
          } catch (ReflectionError e) {
          }
        }
        return false;
      }
    });

    ideSettingsDialogFixture.clickOK();
    MessagesFixture.findByTitle(guiTest.robot(), "Confirm Change").clickOk();
    DialogFixture downloadDialog =
      findDialog(withTitle("SDK Quickfix Installation")).withTimeout(SECONDS.toMillis(5)).using(guiTest.robot());
    JButtonFixture finish = downloadDialog.button(withText("Finish"));
    Wait.seconds(120).expecting("Android source to be installed").until(finish::isEnabled);
    finish.click();
  }
}
