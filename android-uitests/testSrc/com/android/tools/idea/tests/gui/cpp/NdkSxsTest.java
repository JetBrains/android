/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.cpp;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeSettingsDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.WelcomeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import com.intellij.ui.dualView.TreeTableView;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import javax.swing.tree.DefaultMutableTreeNode;
import org.fest.reflect.exception.ReflectionError;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.fixture.JListFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickLabel;
import static com.google.common.truth.Truth.assertThat;
import static org.fest.reflect.core.Reflection.field;
import static org.fest.reflect.core.Reflection.method;

@RunWith(GuiTestRemoteRunner.class)
public class NdkSxsTest {
  @Rule public GuiTestRule guiTest = new GuiTestRule().withTimeout(2, TimeUnit.MINUTES);

  /**
   * Verifies that there should be multiple NDK version under NDK (Side by side) section in SDK Manager.
   * <p>
   *   This is run to qualify releases. Please involve the test team in substantial changes.
   * </p>
   *
   * TT ID: TODO: Wait this test to be added to Test Tracker, then will have an ID.
   * <p>
   *   <pre>
   *     This feature is for Android Studio 3.5 or above.
   *     Test Steps:
   *     1. From the welcome screen of Android Studio, open SDK Manager
   *        Configure -> SDK Manager
   *     2. Click on SDK Tools tab, and check Show Package Details check box
   *     3. Verify that there should be multiple NDK versions available
   *   </pre>
   * </p>
   */
  @Test
  @RunIn(TestGroup.FAST_BAZEL)
  public void checkMultiNdkVersions() {
    WelcomeFrameFixture welcomeFrame = guiTest.welcomeFrame();
    JListFixture listFixture = welcomeFrame.clickConfigure();
    welcomeFrame.openSdkManager(listFixture);

    IdeSettingsDialogFixture ideSettingsDialogFixture = IdeSettingsDialogFixture.find(guiTest.robot());
    findAndClickLabel(ideSettingsDialogFixture, "SDK Tools");
    ideSettingsDialogFixture.selectShowPackageDetails();
    checkNdkSideBySide(ideSettingsDialogFixture);

    ideSettingsDialogFixture.close();
  }

  private void checkNdkSideBySide(@NotNull IdeSettingsDialogFixture ideSettingsDialogFixture) {
    GuiTests.waitUntilFound(guiTest.robot(),
                            ideSettingsDialogFixture.target(),
                            new GenericTypeMatcher<TreeTableView>(TreeTableView.class) {
        @Override
        protected boolean isMatching(TreeTableView treeTableView) {
          DefaultMutableTreeNode root = (DefaultMutableTreeNode)treeTableView.getTableModel().getRoot();
          for (Object object : Collections.list(root.children())) {  // refer to: MultiVersionTreeNode.java
            try {
              String title = method("getDisplayName")
                .withReturnType(String.class)
                .in(object).invoke();
              if(title.contains("NDK (Side by side)")) {
                assertThat(field("myVersionNodes").ofType(Collection.class).in(object).get().size()).isGreaterThan(0);
                return true;
              }
            } catch (ReflectionError e) {
            }
          }
          return false;
        }
      });
    }
}
