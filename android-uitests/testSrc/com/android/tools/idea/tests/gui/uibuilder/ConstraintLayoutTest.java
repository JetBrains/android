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

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.timing.Wait;
import org.intellij.lang.annotations.Language;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

/**
 * UI tests for the constraint layout
 */
@RunWith(GuiTestRemoteRunner.class)
public class ConstraintLayoutTest {
  private static final Path ACTIVITY_MAIN_XML_RELATIVE_PATH =
    FileSystems.getDefault().getPath("app", "src", "main", "res", "layout", "activity_main.xml");

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  @Before
  public void setUp() {
    // Temporary: until this test can run with new properties panel
    StudioFlags.NELE_NEW_PROPERTY_PANEL.override(false);
  }

  @After
  public void tearDown() {
    StudioFlags.NELE_NEW_PROPERTY_PANEL.clearOverride();
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
  @RunIn(TestGroup.QA_UNRELIABLE) // b/117556696
  @Test
  public void addAllLayoutItemsFromToolbar() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();

    NlEditorFixture design = ideFrameFixture.getEditor()
      .open("app/src/main/res/layout/activity_my.xml", Tab.DESIGN)
      .getLayoutEditor(false)
      .waitForRenderToFinish();

    Multimap<String, String> widgets = ArrayListMultimap.create();
    widgets.put("Buttons", "Button");
    widgets.put("Buttons", "ToggleButton");
    widgets.put("Buttons", "CheckBox");
    widgets.put("Buttons", "RadioButton");
    widgets.put("Buttons", "Switch");
    widgets.put("Widgets", "ProgressBar");
    widgets.put("Widgets", "SeekBar");
    widgets.put("Widgets", "RatingBar");
    widgets.put("Layouts", "Space");
    widgets.put("Containers", "Spinner");
    widgets.put("Text", "CheckedTextView");

    for (Map.Entry<String, String> entry : widgets.entries()) {
      design.dragComponentToSurface(entry.getKey(), entry.getValue());
      assertThat(design.getIssuePanel().hasRenderError()).isFalse();
    }

    // Testing these separately because the generated tag does not correspond to the
    // displayed name to the code below would fail
    design.dragComponentToSurface("Widgets", "Vertical Divider");
    assertThat(design.getIssuePanel().hasRenderError()).isFalse();
    design.dragComponentToSurface("Widgets", "Horizontal Divider");
    assertThat(design.getIssuePanel().hasRenderError()).isFalse();

    String layoutXml = ideFrameFixture
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", Tab.EDITOR)
      .getCurrentFileContents();
    for (String widget : widgets.values()) {
      assertThat(layoutXml).containsMatch("<" + widget);
    }
  }

  @Test
  public void fileIsFormattedAfterSelectingMarginStart() {
    WizardUtils.createNewProject(guiTest, "Empty Activity");

    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open(ACTIVITY_MAIN_XML_RELATIVE_PATH);

    NlEditorFixture layoutEditor = editor.getLayoutEditor(true);

    layoutEditor.waitForRenderToFinish();
    layoutEditor.findView("TextView", 0).click();
    layoutEditor.getPropertiesPanel().getConstraintLayoutViewInspector().selectMarginStart(8);

    editor.selectEditorTab(Tab.EDITOR);

    @Language("XML")
    String expected =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<androidx.constraintlayout.widget.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
      "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
      "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
      "    android:layout_width=\"match_parent\"\n" +
      "    android:layout_height=\"match_parent\"\n" +
      "    tools:context=\".MainActivity\">\n" +
      "\n" +
      "    <TextView\n" +
      "        android:layout_width=\"wrap_content\"\n" +
      "        android:layout_height=\"wrap_content\"\n" +
      "        android:layout_marginTop=\"8dp\"\n" +
      "        android:text=\"Hello World!\"\n" +
      "        app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
      "        app:layout_constraintLeft_toLeftOf=\"parent\"\n" +
      "        app:layout_constraintRight_toRightOf=\"parent\"\n" +
      "        app:layout_constraintTop_toTopOf=\"parent\" />\n" +
      "\n" +
      "</androidx.constraintlayout.widget.ConstraintLayout>";

    Wait.seconds(10).expecting("the editor to update and reformat the XML file")
      .until(() -> expected.equals(editor.getCurrentFileContents()));
  }

  @Test
  public void cleanUpAttributes() {
    WizardUtils.createNewProject(guiTest);

    @Language("XML")
    String contents = "<androidx.constraintlayout.widget.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                      "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                      "    android:layout_width=\"match_parent\"\n" +
                      "    android:layout_height=\"match_parent\">\n" +
                      "\n" +
                      "    <TextView\n" +
                      "        android:layout_width=\"wrap_content\"\n" +
                      "        android:layout_height=\"wrap_content\"\n" +
                      "        android:layout_marginStart=\"0dp\"\n" +
                      "        android:text=\"Hello World!\"\n" +
                      "        app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                      "        app:layout_constraintLeft_toLeftOf=\"parent\"\n" +
                      "        app:layout_constraintRight_toRightOf=\"parent\"\n" +
                      "        app:layout_constraintTop_toTopOf=\"parent\" />\n" +
                      "</androidx.constraintlayout.widget.ConstraintLayout>";

    EditorFixture editor = guiTest.ideFrame().getEditor()
      .open(ACTIVITY_MAIN_XML_RELATIVE_PATH)
      .replaceText(contents);

    NlEditorFixture layoutEditor = editor.getLayoutEditor(true);
    layoutEditor.waitForRenderToFinish();
    layoutEditor.showOnlyDesignView();
    layoutEditor.findView("TextView", 0).click();
    layoutEditor.getPropertiesPanel().getConstraintLayoutViewInspector().getDeleteRightConstraintButton().click();

    editor.selectEditorTab(Tab.EDITOR);

    @Language("XML")
    String expected = "<androidx.constraintlayout.widget.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                      "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                      "    android:layout_width=\"match_parent\"\n" +
                      "    android:layout_height=\"match_parent\">\n" +
                      "\n" +
                      "    <TextView\n" +
                      "        android:layout_width=\"wrap_content\"\n" +
                      "        android:layout_height=\"wrap_content\"\n" +
                      "        android:text=\"Hello World!\"\n" +
                      "        app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                      "        app:layout_constraintLeft_toLeftOf=\"parent\"\n" +
                      "        app:layout_constraintTop_toTopOf=\"parent\" />\n" +
                      "</androidx.constraintlayout.widget.ConstraintLayout>";

    Wait.seconds(10).expecting("the editor to update and reformat the XML file")
      .until(() -> expected.equals(editor.getCurrentFileContents()));
  }
}