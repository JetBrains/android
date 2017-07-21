/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.view.View;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.MorphDialogFixture;
import com.intellij.ide.ui.UISettings;
import org.fest.swing.core.MouseButton;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.timing.Pause.pause;
import static org.junit.Assert.assertEquals;

@RunWith(GuiTestRunner.class)
public class NlEditorTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testSelectComponent() throws Exception {
    guiTest.importSimpleApplication();

    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor(false);
    layout.waitForRenderToFinish();

    // Find and click the first text view
    NlComponentFixture textView = layout.findView("TextView", 0);
    textView.click();

    // It should be selected now
    assertThat(layout.getSelection()).containsExactly(textView.getComponent());
  }

  /**
   * Verifies addition of components to designer screen
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: 78baed4b-32be-4d72-b558-ba9f6c727334
   * <pre>
   *   1. Create a new project
   *   2. Open the layout xml file
   *   3. Switch to design view
   *   4. Drag and drop components TextView, Button
   *   5. Switch back to Text view
   *   Verification:
   *   1. The added component shows up in the xml
   * </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void basicLayoutEdit() throws Exception {
    guiTest.importSimpleApplication()
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(false)
      .dragComponentToSurface("Text", "TextView")
      .dragComponentToSurface("Widgets", "Button");
    String layoutFileContents = guiTest.ideFrame()
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR)
      .getCurrentFileContents();
    assertThat(layoutFileContents).contains("<TextView");
    assertThat(layoutFileContents).contains("<Button");
  }

  @Test
  public void testCopyAndPaste() throws Exception {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);
    NlEditorFixture layout = editor.getLayoutEditor(true)
      .dragComponentToSurface("Widgets", "Button")
      .dragComponentToSurface("Widgets", "CheckBox")
      .waitForRenderToFinish();

    // Find and click the first text view
    NlComponentFixture textView = layout.findView("CheckBox", 0);
    textView.click();

    // It should be selected now
    assertThat(layout.getSelection()).containsExactly(textView.getComponent());
    assertEquals(4, layout.getAllComponents().size()); // 4 = root layout + 3 widgets

    ideFrame.invokeMenuPath("Edit", "Cut");
    assertThat(layout.getSelection()).isEmpty();
    assertEquals(3, layout.getAllComponents().size());

    layout.findView("Button", 0).click();
    ideFrame.invokeMenuPath("Edit", "Paste");
    layout.findView("CheckBox", 0).click();
    ideFrame.invokeMenuPath("Edit", "Copy");
    ideFrame.invokeMenuPath("Edit", "Paste");
    assertEquals(5, layout.getAllComponents().size());
  }

  @Test
  public void testZoomAndPanWithMouseShortcut() throws Exception {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture nele = editor.getLayoutEditor(true);
    nele.waitForRenderToFinish();

    // Test zoom in with mouse wheel
    double oldScale = nele.getScale();
    nele.mouseWheelZoomIn(-10);
    assertThat(nele.getScale()).isGreaterThan(oldScale);

    // Test Pan with middle mouse button
    Point oldScrollPosition = nele.getScrollPosition();
    nele.dragMouseFromCenter(-10, -10, MouseButton.MIDDLE_BUTTON, 0);
    Point expectedScrollPosition = new Point(oldScrollPosition);
    expectedScrollPosition.translate(10, 10);
    assertThat(nele.getScrollPosition()).isEqualTo(expectedScrollPosition);

    // Test Pan with Left mouse button and CTRL
    nele.dragMouseFromCenter(-10, -10, MouseButton.LEFT_BUTTON, InputEvent.CTRL_MASK);
    expectedScrollPosition.translate(10, 10);
    assertThat(nele.getScrollPosition()).isEqualTo(expectedScrollPosition);

    // Test zoom out with mouse wheel
    oldScale = nele.getScale();
    nele.mouseWheelZoomIn(3);
    assertThat(nele.getScale()).isLessThan(oldScale);
  }

  @Test
  public void testAddDesignLibrary() throws Exception {
    guiTest.importSimpleApplication()
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(true)
      .dragComponentToSurface("Design", "android.support.design.widget.TextInputLayout");
    MessagesFixture.findByTitle(guiTest.robot(), "Add Project Dependency").clickOk();
    guiTest.ideFrame()
      .waitForGradleProjectSyncToFinish()
      .getEditor()
      .open("app/build.gradle")
      .moveBetween("implementation 'com.android.support:design", "")
      .invokeAction(EditorFixture.EditorAction.DELETE_LINE)
      .invokeAction(EditorFixture.EditorAction.SAVE)
      .awaitNotification(
        "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
      .performAction("Sync Now")
      .waitForGradleProjectSyncToFinish();
  }

  @Test
  public void morphComponent() throws IOException {
    boolean morphViewActionEnabled = StudioFlags.NELE_CONVERT_VIEW.get();

    try {
      StudioFlags.NELE_CONVERT_VIEW.override(true);

      guiTest.importSimpleApplication();
      IdeFrameFixture ideFrame = guiTest.ideFrame();
      EditorFixture editor = ideFrame.getEditor()
        .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);
      NlEditorFixture layout = editor.getLayoutEditor(true)
        .dragComponentToSurface("Widgets", "Button")
        .waitForRenderToFinish();

      NlComponentFixture button = layout.findView("Button", 0);
      MorphDialogFixture fixture = layout.openMorphDialogForComponent(button);
      assertThat(fixture.getTextField().target().isFocusOwner()).isTrue();

      ideFrame.robot().enterText("TextView");
      ideFrame.robot().pressAndReleaseKey(KeyEvent.VK_ENTER, 0);
      assertThat(fixture.getTextField().target().getText()).isEqualTo("TextView");
      fixture.getOkButton().click();
      assertThat(button.getComponent().getTag().getName()).isEqualTo("TextView");
    }
    finally {
      StudioFlags.NELE_CONVERT_VIEW.override(morphViewActionEnabled);
    }
  }

  @Test
  public void testNavigateEditorsWithoutTabs() throws Exception {
    // Regression test for b/37138939
    // When the tabs are turned off, a switch using ctrl-E (or cmd-E on Mac) to a layout/menu would
    // cause the editor to switch to design mode.

    UISettings settings = UISettings.getInstance();
    int placement = settings.getEditorTabPlacement();

    try {
      // First turn off tabs.
      settings.setEditorTabPlacement(UISettings.TABS_NONE);

      // Open up 2 layout files in design and switch them both to text editor mode.
      guiTest.importSimpleApplication();
      IdeFrameFixture ideFrame = guiTest.ideFrame();
      EditorFixture editor = ideFrame.getEditor().open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);
      editor.getLayoutEditor(true).waitForRenderToFinish();
      editor.switchToTab("Text");
      ideFrame.getEditor().open("app/src/main/res/layout/absolute.xml", EditorFixture.Tab.DESIGN);
      editor.getLayoutEditor(true).waitForRenderToFinish();
      editor.switchToTab("Text");

      // Switch to the previous layout and verify we are still editing the text.
      ideFrame.selectPreviousEditor();
      assertThat(editor.getCurrentFileName()).isEqualTo("activity_my.xml");
      assertThat(editor.getSelectedTab()).isEqualTo("Text");  // not "Design"

      // Again switch to the previous layout and verify we are still editing the text.
      ideFrame.selectPreviousEditor();
      assertThat(editor.getCurrentFileName()).isEqualTo("absolute.xml");
      assertThat(editor.getSelectedTab()).isEqualTo("Text");  // not "Design"
    }
    finally {
      settings.setEditorTabPlacement(placement);
    }
  }

  @Test
  public void scrollWhileZoomed() throws Exception {
    NlEditorFixture layoutEditor = guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest")
      .getEditor()
      .open("app/src/main/res/layout/scroll.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(true);
    Point surfacePosition = layoutEditor
      .showOnlyDesignView()
      .waitForRenderToFinish()
      .mouseWheelZoomIn(-10)
      .getScrollPosition();
    View  view = (View)layoutEditor.findView("android.support.v4.widget.NestedScrollView", 0).getViewObject();
    assertThat(view.getScrollY()).isEqualTo(0);

    // Scroll a little bit: the ScrollView moves, the Surface stays.
    layoutEditor.mouseWheelScroll(10)
      .waitForRenderToFinish();
    int viewScroll = view.getScrollY();
    assertThat(viewScroll).isGreaterThan(0);
    assertThat(layoutEditor.getScrollPosition()).isEqualTo(surfacePosition);

    // Scroll a lot more so that the ScrollView reaches the bottom: the ScrollView moves, and the Surface moves as well.
    layoutEditor.mouseWheelScroll(1000)
      .waitForRenderToFinish();
    assertThat(view.getScrollY()).isGreaterThan(viewScroll);
    assertThat(layoutEditor.getScrollPosition().getY()).isGreaterThan(surfacePosition.getY());
  }
}
