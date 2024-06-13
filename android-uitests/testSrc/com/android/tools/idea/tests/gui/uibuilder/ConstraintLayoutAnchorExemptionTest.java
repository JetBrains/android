/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.uibuilder;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.SplitEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.SplitEditorFixtureKt;
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.wizard.template.Language;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.event.KeyEvent;
import org.fest.swing.timing.Wait;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;

@RunWith(GuiTestRemoteRunner.class)
public class ConstraintLayoutAnchorExemptionTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);
  @Rule public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

  private IdeFrameFixture ideFrameFixture;

  protected static final String EMPTY_ACTIVITY_TEMPLATE = "Empty Views Activity";
  protected static final String APP_NAME = "App";
  protected static final String PACKAGE_NAME = "android.com.app";
  protected static final int MIN_SDK_API = 30;
  protected static final String ACTIVITY_FILE = "app/src/main/res/layout/activity_main.xml";

  @Before
  public void setUp() throws Exception {

    WizardUtils.createNewProject(guiTest, EMPTY_ACTIVITY_TEMPLATE, APP_NAME, PACKAGE_NAME, MIN_SDK_API, Language.Kotlin);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrameFixture = guiTest.ideFrame();

    //Clearing notifications present on the screen.
    ideFrameFixture.clearNotificationsPresentOnIdeFrame();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }

  /**
   * To verify that anchors on different axis, such as left and top anchor cannot be connected.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: ffa37b5f-d71a-4b29-bcdd-2b73865e1496
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open the layout file which uses constraint layout and switch to design view
   *   2. From the palette window, drag and drop couple of widgets to constraint layout. Say, a button and a text view
   *   3. Now click on a widget in the design view, select left/right anchor and try to connect it to top and bottom anchor
   *   4. Repeat step 3 with top/bottom anchor and try connect to left and right anchors
   *   Verify:
   *   Anchors on different axis, such as left and top anchor shall not get connected and no abnormal behavior is observed
   *   </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void constraintLayoutAnchorExemption() throws Exception {

    EditorFixture editor = ideFrameFixture.getEditor()
      .open(ACTIVITY_FILE);
    SplitEditorFixture splitEditorFixture = SplitEditorFixtureKt.getSplitEditorFixture(editor);
    splitEditorFixture.setSplitMode();
    editor.replaceText("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                       "<androidx.constraintlayout.widget.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                       "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                       "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                       "    android:layout_width=\"match_parent\"\n" +
                       "    android:layout_height=\"match_parent\"\n" +
                       "    tools:context=\".MainActivity\">\n" +
                       "\n" +
                       "</androidx.constraintlayout.widget.ConstraintLayout>");

    guiTest.waitForAllBackgroundTasksToBeCompleted();
    editor.selectEditorTab(EditorFixture.Tab.DESIGN);

    NlEditorFixture design = editor.getLayoutEditor().waitForRenderToFinish(Wait.seconds(30))
      .waitForSurfaceToLoad()
      .dragComponentToSurface("Text", "TextView")
      .waitForRenderToFinish()
      .dragComponentToSurface("Buttons", "Button")
      .waitForRenderToFinish();

    design.findView("Button", 0)
      .moveBy(-20, -20);

    design.waitForRenderToFinish();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    String layoutContents = editor.selectEditorTab(EditorFixture.Tab.EDITOR).getCurrentFileContents();

    editor.open(ACTIVITY_FILE, EditorFixture.Tab.DESIGN)
      .getLayoutEditor()
      .waitForRenderToFinish();
    design.findView("Button", 0)
      .createConstraintFromBottomToLeftOf(design.findView("TextView", 0));
    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_ESCAPE); // Removing any popup for constraints suggestions
    String layoutContentsAfter = editor.selectEditorTab(EditorFixture.Tab.EDITOR).getCurrentFileContents();

    assertThat(layoutContents).isEqualTo(layoutContentsAfter);
  }
}
