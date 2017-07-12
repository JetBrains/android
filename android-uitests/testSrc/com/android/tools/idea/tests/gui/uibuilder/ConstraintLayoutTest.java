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
package com.android.tools.idea.tests.gui.uibuilder;

import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.NlPreviewFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;


import static com.google.common.truth.Truth.assertThat;

/**
 * UI tests for the constraint layout
 */
@RunWith(GuiTestRunner.class)
public class ConstraintLayoutTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  /**
   * Verifies the UI for adding side constraints for a ConstraintLayout in the layout editor.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 7f7d5b54-a3df-41cc-a472-fa6f50296b0b
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open the layout file which uses constraint layout and switch to design view
   *   2. From the palette window, drag and drop couple of widgets to constraint layout. Say, a button and a text view
   *   3. Now click on a widget in the design view, select any of the little circles on it and create constraints for all left, right,
   *      top and bottom. Create them such that your refer both other widgets and the parent layout (Verify 1)
   *   4. Repeat steps 3 and 4 by aligning widgets in blue print mode (Verify 1)
   *   Verify:
   *   1. Should be able to position the widgets in both Deign view and blueprint mode. Verify the constraints (app:layout_constraint***)
   *      in xml view of layout editor.
   *   </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE)
  @Test
  public void testSideConstraintHandling() throws Exception {
    EditorFixture editor = guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest")
      .getEditor()
      .open("app/src/main/res/layout/constraint.xml", EditorFixture.Tab.DESIGN);
    NlEditorFixture layoutEditor = editor.getLayoutEditor(true);

    layoutEditor
      .showOnlyDesignView()
      .dragComponentToSurface("Widgets", "Button")
      .findView("Button", 0)
      .createConstraintFromBottomToTopOf(layoutEditor.findView("TextView", 0))
      .createConstraintFromTopToTopOfLayout()
      .createConstraintFromLeftToLeftOfLayout()
      .createConstraintFromRightToRightOfLayout();

    String layoutContents = editor.selectEditorTab(EditorFixture.Tab.EDITOR).getCurrentFileContents();
    assertThat(layoutContents).contains("app:layout_constraintBottom_toTopOf=\"@+id/textView\"");
    assertThat(layoutContents).contains("app:layout_constraintTop_toTopOf=\"parent\"");
    assertThat(layoutContents).contains("app:layout_constraintLeft_toLeftOf=\"parent\"");
    assertThat(layoutContents).contains("app:layout_constraintRight_toRightOf=\"parent\"");

    layoutEditor = editor.select("(<Button[\\s\\S]*/>\\n)")
      .invokeAction(EditorFixture.EditorAction.BACK_SPACE)
      .getLayoutEditor(true);

    layoutEditor
      .showOnlyBlueprintView()
      .dragComponentToSurface("Widgets", "Button")
      .findView("Button", 0)
      .createConstraintFromBottomToTopOf(layoutEditor.findView("TextView", 0))
      .createConstraintFromTopToTopOfLayout()
      .createConstraintFromLeftToLeftOfLayout()
      .createConstraintFromRightToRightOfLayout();

    layoutContents = editor.selectEditorTab(EditorFixture.Tab.EDITOR).getCurrentFileContents();
    assertThat(layoutContents).contains("app:layout_constraintBottom_toTopOf=\"@+id/textView\"");
    assertThat(layoutContents).contains("app:layout_constraintTop_toTopOf=\"parent\"");
    assertThat(layoutContents).contains("app:layout_constraintLeft_toLeftOf=\"parent\"");
    assertThat(layoutContents).contains("app:layout_constraintRight_toRightOf=\"parent\"");
  }

  /**
   * Verifies the UI for adding baseline constraints for a ConstraintLayout in the layout editor.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 604b3f71-f831-4236-a30e-725fa0c54193
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open the layout file which uses constraint layout and switch to design view
   *   2. From the palette window, drag and drop couple of widgets to constraint layout. Say, a button and a text view
   *   3. Now click on a widget in the design view, you should see a wide and thin rectangle.
   *   4. Click on the thin rectangle of one widget and create a constraint with similar rectangle in another widget
   *   5. Repeat steps 3 and 4 by aligning widgets in blue print mode (Verify 1)
   *   Verify:
   *   1. Should be able to baseline the widgets in both design view and blueprint mode. Verfiy the same in xml view by checking
   *      for "layout_constraintBaseline_toBaselineOf"
   *   </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE)
  @Test
  public void testBaselineConstraintHandling() throws Exception {
    EditorFixture editor = guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest")
      .getEditor()
      .open("app/src/main/res/layout/constraint.xml", EditorFixture.Tab.DESIGN);
    NlEditorFixture layoutEditor = editor.getLayoutEditor(true);

    layoutEditor
      .waitForRenderToFinish()
      .showOnlyDesignView()
      .dragComponentToSurface("Widgets", "Button")
      .findView("Button", 0)
      .createBaselineConstraintWith(layoutEditor.findView("TextView", 0));
    String layoutContents = editor.selectEditorTab(EditorFixture.Tab.EDITOR).getCurrentFileContents();
    assertThat(layoutContents).contains("app:layout_constraintBaseline_toBaselineOf=\"@+id/textView\"");

    layoutEditor = editor.select("(<Button[\\s\\S]*/>\\n)")
      .invokeAction(EditorFixture.EditorAction.BACK_SPACE)
      .getLayoutEditor(true);

    layoutEditor
      .showOnlyBlueprintView()
      .dragComponentToSurface("Widgets", "Button")
      .findView("Button", 0)
      .createBaselineConstraintWith(layoutEditor.findView("TextView", 0));
    layoutContents = editor.selectEditorTab(EditorFixture.Tab.EDITOR).getCurrentFileContents();
    assertThat(layoutContents).contains("app:layout_constraintBaseline_toBaselineOf=\"@+id/textView\"");
  }

  /**
   * To verify that items from the tool kit can be added to a layout.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 7fd4834b-ef5a-4414-b601-3c8bd8ab54d0
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open layout editor for the default activity.
   *   2. Add each item from the toolbar.
   *   3. Switch to xml view.
   *   Verify:
   *   1. Verify the item displays in the xml view.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void addAllLayoutItemsFromToolbar() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();

    String group = "Widgets";

    NlEditorFixture design = ideFrameFixture.getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(false);

    List<String> widgets =
      Arrays.asList("Button", "ToggleButton", "CheckBox", "RadioButton", "CheckedTextView",
                    "Spinner", "ProgressBar", "SeekBar", "RatingBar", "Switch", "Space");
    for (String widget : widgets) {
      design.dragComponentToSurface(group, widget);
    }
    String layoutXml = ideFrameFixture
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR)
      .getCurrentFileContents();
    for (String widget: widgets) {
      assertThat(layoutXml).containsMatch("<" + widget);
    }
  }

  /**
   * To verify that the layout preview renders appropriately with different themes and API selections
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: d45e0fa5-82d5-4d9a-9046-0437210741f0
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open layout.xml design view for the MainActivity
   *   2. Select the rendering device and switch to another device, say N5 and repeat with N6 (Verify 1)
   *   3. Click on the Orientation and repeat (Verify 2)
   *   4. Select the Android version option and choose older API levels one by one (Verify 3)
   *   5. Select the Theme option and choose a different theme to render the preview (Verify 4)
   *   6. Select the activity option and choose a different activity (Verify 5)
   *   Verify:
   *   1. Preview render device changes to the newly selected device.
   *   2. The preview layout orientation switches to landscape and then back to Portrait.
   *   3. Preview layout renders fine on compatible API levels.
   *   4. The selected theme is applied on the preview layout.
   *   5. Preview layout is rendered for the selected activity.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void layoutPreviewRendering() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");

    EditorFixture editorFixture = ideFrameFixture.getEditor()
      .open("app/src/main/res/layout/layout2.xml", EditorFixture.Tab.DESIGN);

    NlPreviewFixture preview = editorFixture
      .getLayoutPreview(true)
      .waitForRenderToFinish();

    preview.getConfigToolbar()
      .chooseDevice("Nexus 5")
      .requireDevice("Nexus 5")
      .chooseDevice("Nexus 6")
      .requireDevice("Nexus 6");

    preview.getConfigToolbar()
      .switchOrientation()
      .requireOrientation("Landscape")
      .switchOrientation()
      .requireOrientation("Portrait");

    preview.getConfigToolbar()
      .chooseApiLevel("API 23")
      .requireApiLevel("N")
      .chooseApiLevel("API 24")
      .requireApiLevel("24")
      .chooseApiLevel("API 25")
      .requireApiLevel("O");

    preview.getConfigToolbar()
      .openThemeSelectionDialog()
      .selectsTheme("Material Light", "android:Theme.Material.Light")
      .clickOk();
    preview.getConfigToolbar()
      .requireTheme("Light");
    preview.getConfigToolbar()
      .openThemeSelectionDialog()
      .selectsTheme("Material Dark", "android:Theme.Material")
      .clickOk();
    preview.getConfigToolbar()
      .requireTheme("Material");

    editorFixture = ideFrameFixture.getEditor()
      .open("app/src/main/res/layout/layout1.xml", EditorFixture.Tab.DESIGN);

    preview = editorFixture
      .getLayoutPreview(true)
      .waitForRenderToFinish();

    preview.getConfigToolbar()
      .requireDevice("Nexus 6")
      .requireOrientation("Portrait")
      .requireApiLevel("O")
      .requireTheme("AppTheme");
  }
}
