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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.view.View;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.testing.FakeAdbRule;
import com.android.fakeadbserver.CommandHandler;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.execcommandhandlers.SimpleExecHandler;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.MorphDialogFixture;
import com.android.tools.idea.tests.util.ddmlib.AndroidDebugBridgeUtils;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.fest.swing.core.MouseButton;
import org.fest.swing.fixture.JPopupMenuFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class NlEditorTest {
  private static final String RUN_CONFIG_NAME = "app";

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

  @Rule
  public final FakeAdbRule adbRule = new FakeAdbRule().initAbdBridgeDuringSetup(false);

  @Test
  public void testSelectComponent() throws Exception {
    guiTest.importSimpleApplication();

    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor();
    layout.waitForRenderToFinish();

    // Find and click the first text view
    NlComponentFixture textView = layout.findView("TextView", 0);
    textView.getSceneComponent().click();

    // It should be selected now
    assertThat(layout.getSelection()).containsExactly(textView.getComponent());
  }

  @Test
  public void testDeployToDevice() throws Exception {
    // Enable the fake ADB server and attach a fake device to which the preview will be deployed.
    AndroidDebugBridgeUtils.enableFakeAdbServerMode(adbRule.getFakeAdbServerPort());
    AndroidDebugBridge.init(true);
    AndroidDebugBridge debugBridge = AndroidDebugBridge.createBridge(10, TimeUnit.SECONDS);
    assertNotNull(debugBridge);
    Wait.seconds(10).expecting("Bridge is initialized").until(() -> debugBridge.isConnected() && debugBridge.hasInitialDeviceList());
    adbRule.attachDevice("emulator-test", "Google", "Pix3l", "versionX", "29");
    CountDownLatch installedLatch = new CountDownLatch(1);
    adbRule.addDeviceCommandHandler(new SimpleExecHandler("/data/local/tmp/.studio/bin/installer") {
      @Override
      public void execute(@NotNull FakeAdbServer fakeAdbServer,
                          @NotNull Socket responseSocket,
                          @NotNull DeviceState device,
                          @Nullable String args) {
        installedLatch.countDown();
        try {
          OutputStream output = responseSocket.getOutputStream();
          CommandHandler.writeOkay(output);
        }
        catch (IOException ignore) {
        }
      }
    });

    guiTest.importSimpleApplication();

    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor();
    layout.waitForRenderToFinish();

    guiTest.ideFrame().runApp(RUN_CONFIG_NAME, "Google Pix3l");

    // Check the application is deployed
    installedLatch.await(10, TimeUnit.SECONDS);
  }

  @Test
  public void testCopyAndPaste() throws Exception {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor()
      .open("app/src/main/res/layout/empty_absolute.xml", EditorFixture.Tab.DESIGN);
    NlEditorFixture layout = editor.getLayoutEditor();
    DesignSurface<?> surface = layout.getSurface().target();
    Dimension screenViewSize = surface.getFocusedSceneView().getScaledContentSize();
    // Drag components to areas that are safe to click (not out of bounds) and that not overlap to prevent them from overlapping each
    // other and interfering with clicking.
    // We use the middle point and put the components in midpoint +/- 25% of the screen view size.
    int widthOffset = screenViewSize.width / 4;
    int heightOffset = screenViewSize.height / 4;
    layout
      .dragComponentToSurface("Buttons", "CheckBox",
                              screenViewSize.width / 2 - widthOffset, screenViewSize.height / 2 - heightOffset)
      .dragComponentToSurface("Buttons", "Button",
                              screenViewSize.width / 2 + widthOffset, screenViewSize.height / 2 + heightOffset)
      .waitForRenderToFinish();

    // Find and click the checkBox
    NlComponentFixture checkBox = layout.findView("CheckBox", 0);
    checkBox.getSceneComponent().click();
    assertEquals("CheckBox", layout.getSelection().get(0).getTagName());

    // It should be selected now
    assertEquals(3, layout.getAllComponents().size()); // 3 = root layout + the 2 widgets added

    ideFrame.invokeMenuPath("Edit", "Cut");
    assertThat(layout.getSelection()).isEmpty();
    assertEquals(2, layout.getAllComponents().size());

    layout.findView("Button", 0).getSceneComponent().click();
    ideFrame.invokeMenuPath("Edit", "Paste");
    layout.findView("CheckBox", 0).getSceneComponent().click();
    ideFrame.invokeMenuPath("Edit", "Copy");
    ideFrame.invokeMenuPath("Edit", "Paste");
    assertEquals(4, layout.getAllComponents().size());
  }

  @Test
  public void testZoomAndPanWithMouseShortcut() throws Exception {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture nele = editor.getLayoutEditor();
    nele.waitForRenderToFinish();

    // Test zoom in with mouse wheel
    double oldScale = nele.getScale();
    nele.mouseWheelZoomIn(-10);
    assertThat(nele.getScale()).isGreaterThan(oldScale);

    // Test Pan with middle mouse button
    Point oldScrollPosition = nele.getScrollPosition();
    nele.dragMouseFromCenterWithModifier(-10, -10, MouseButton.MIDDLE_BUTTON, 0);
    Point expectedScrollPosition = new Point(oldScrollPosition);
    expectedScrollPosition.translate(10, 10);
    assertThat(nele.getScrollPosition()).isEqualTo(expectedScrollPosition);

    // Test Pan with Left mouse button and SPACE
    nele.dragMouseFromCenterWithKeyCode(-10, -10, MouseButton.LEFT_BUTTON, KeyEvent.VK_SPACE);
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
      .getLayoutEditor()
      .dragComponentToSurface("Text", "TextInputLayout");
    guiTest.ideFrame()
      .actAndWaitForGradleProjectSyncToFinish(
        it -> MessagesFixture.findByTitle(guiTest.robot(), "Add Project Dependency").clickOk())
      .getEditor()
      .open("app/build.gradle")
      .moveBetween("implementation 'com.android.support:design", "")
      .invokeAction(EditorFixture.EditorAction.DELETE_LINE)
      .invokeAction(EditorFixture.EditorAction.SAVE)
      .awaitNotification(
        "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.");

    guiTest.ideFrame().requestProjectSyncAndWaitForSyncToFinish();
  }

  @Test
  public void morphComponent() throws IOException {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);
    NlEditorFixture layout = editor.getLayoutEditor()
      .waitForRenderToFinish();

    // Test click on a suggestion
    NlComponentFixture textView = layout.findView("TextView", 0);
    textView.getSceneComponent().rightClick();
    textView.getSceneComponent().invokeContextMenuAction("Convert view...");
    MorphDialogFixture fixture = layout.findMorphDialog();
    fixture.getTextField().click();
    assertThat(fixture.getTextField().target().isFocusOwner()).isTrue();

    fixture.getSuggestionList().clickItem("Button");
    assertThat(fixture.getTextField().target().getText()).isEqualTo("Button");
    fixture.getOkButton().click();
    layout.waitForRenderToFinish();
    assertThat(textView.getComponent().getTagDeprecated().getName()).isEqualTo("Button");

    // Test enter text manually
    NlComponentFixture button = layout.findView("Button", 0);
    button.getSceneComponent().rightClick();
    layout.invokeContextMenuAction("Convert view...");
    fixture = layout.findMorphDialog();
    fixture.getTextField().click();
    assertThat(fixture.getTextField().target().isFocusOwner()).isTrue();

    fixture.getTextField().replaceText("TextView");
    assertThat(fixture.getTextField().target().getText()).isEqualTo("TextView");
    fixture.getOkButton().click();
    layout.waitForRenderToFinish();
    assertThat(button.getComponent().getTagDeprecated().getName()).isEqualTo("TextView");
  }

  @Test
  public void morphViewGroup() throws IOException {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor()
      .open("app/src/main/res/layout/absolute.xml", EditorFixture.Tab.DESIGN);
    NlEditorFixture layout = editor.getLayoutEditor().waitForRenderToFinish();

    // Right click on AbsoluteLayout in the component tree
    NlComponentFixture root = layout.findView("AbsoluteLayout", 0);
    JPopupMenuFixture popupMenu = layout.getTreePopupMenuItemForComponent(root.getComponent());

    // Open the convert view dialog
    popupMenu.menuItemWithPath("Convert view...").click();
    MorphDialogFixture fixture = layout.findMorphDialog();

    // Click the LinearLayout button
    fixture.getSuggestionList().clickItem("LinearLayout");

    // Apply change
    ideFrame.robot().pressAndReleaseKey(KeyEvent.VK_ENTER, 0);

    // Check if change is correctly applied:
    //    - Root name change from AbsoluteLayout to LinearLayout
    //    - Attributes specific to AbsoluteLayout are removed
    assertThat(root.getComponent().getTagDeprecated().getName()).isEqualTo("LinearLayout");
    String expected = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                      "    android:layout_width=\"match_parent\"\n" +
                      "    android:layout_height=\"match_parent\"\n" +
                      "    android:paddingLeft=\"16dp\"\n" +
                      "    android:paddingTop=\"16dp\"\n" +
                      "    android:paddingRight=\"16dp\"\n" +
                      "    android:paddingBottom=\"16dp\">\n" +
                      "\n" +
                      "    <Button\n" +
                      "        android:id=\"@+id/button\"\n" +
                      "        android:layout_width=\"wrap_content\"\n" +
                      "        android:layout_height=\"wrap_content\"\n" +
                      "        android:text=\"Button\" />\n" +
                      "\n" +
                      "    <Button\n" +
                      "        android:id=\"@+id/button2\"\n" +
                      "        android:layout_width=\"wrap_content\"\n" +
                      "        android:layout_height=\"wrap_content\"\n" +
                      "        android:text=\"Button\" />\n" +
                      "\n" +
                      "    <EditText\n" +
                      "        android:id=\"@+id/editText\"\n" +
                      "        android:layout_width=\"wrap_content\"\n" +
                      "        android:layout_height=\"wrap_content\"\n" +
                      "        android:ems=\"10\"\n" +
                      "        android:inputType=\"textPersonName\"\n" +
                      "        android:text=\"Name\" />\n" +
                      "\n" +
                      "    <LinearLayout\n" +
                      "        android:layout_width=\"359dp\"\n" +
                      "        android:layout_height=\"89dp\">\n" +
                      "\n" +
                      "        <Button\n" +
                      "            android:id=\"@+id/button3\"\n" +
                      "            android:layout_width=\"wrap_content\"\n" +
                      "            android:layout_height=\"wrap_content\"\n" +
                      "            android:layout_weight=\"1\"\n" +
                      "            android:text=\"Button\" />\n" +
                      "\n" +
                      "        <Button\n" +
                      "            android:id=\"@+id/button5\"\n" +
                      "            android:layout_width=\"wrap_content\"\n" +
                      "            android:layout_height=\"wrap_content\"\n" +
                      "            android:layout_weight=\"1\"\n" +
                      "            android:text=\"Button\" />\n" +
                      "\n" +
                      "        <Button\n" +
                      "            android:id=\"@+id/button6\"\n" +
                      "            android:layout_width=\"wrap_content\"\n" +
                      "            android:layout_height=\"wrap_content\"\n" +
                      "            android:layout_weight=\"1\"\n" +
                      "            android:text=\"Button\" />\n" +
                      "    </LinearLayout>\n" +
                      "</LinearLayout>";
    String text = ApplicationManager.getApplication()
      .runReadAction((Computable<String>)() -> root.getComponent().getTagDeprecated().getText());
    assertThat(text).isEqualTo(expected);
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
      editor.getLayoutEditor().waitForRenderToFinish();
      editor.switchToTab("Text");
      ideFrame.getEditor().open("app/src/main/res/layout/absolute.xml", EditorFixture.Tab.DESIGN);
      editor.getLayoutEditor().waitForRenderToFinish();
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
  public void gotoAction() throws IOException {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture nlEditorFixture = editor.getLayoutEditor();
    nlEditorFixture.rightClick();
    nlEditorFixture.invokeContextMenuAction("Go to XML");
    assertThat(editor.getSelectedTab()).isEqualTo("Text");
  }

  @RunIn(TestGroup.UNRELIABLE)  // b/124109589
  @Test
  public void scrollWhileZoomed() throws Exception {
    NlEditorFixture layoutEditor = guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest")
      .getEditor()
      .open("app/src/main/res/layout/scroll.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor();
    Point surfacePosition = layoutEditor
      .waitForRenderToFinish()
      .showOnlyDesignView()
      .waitForRenderToFinish()
      .mouseWheelZoomIn(-10)
      .getScrollPosition();
    View view = (View)layoutEditor.findView("android.support.v4.widget.NestedScrollView", 0).getViewObject();
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
