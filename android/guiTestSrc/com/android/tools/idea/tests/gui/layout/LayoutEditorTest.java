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
package com.android.tools.idea.tests.gui.layout;

import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.annotation.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.*;
import org.junit.Test;

import java.util.Collections;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Unit test for the layout editor window
 * <p>
 * Additional things we should test:
 * <ul>
 *   <li>Drag from palette</li>
 *   <li>Drag to move</li>
 *   <li>Layout actions toolbar</li>
 *   <li>Resizing widgets</li>
 *   <li>Relative Layout handler</li>
 *   <li>Linear Layout handler</li>
 *   <li>Grid Layout handler</li>
 *   <li>Component Tree</li>
 * </ul>
 */
public class LayoutEditorTest extends GuiTestCase {
  @Test
  @IdeGuiTest
  public void testSetProperty() throws Exception {
    IdeFrameFixture projectFrame = openSimpleApplication();

    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR);
    editor.selectEditorTab(EditorFixture.Tab.DESIGN);
    LayoutEditorFixture layout = editor.getLayoutEditor(false);
    assertNotNull(layout);
    layout.waitForNextRenderToFinish();

    // Find and click the first text view
    LayoutEditorComponentFixture textView = layout.findView("TextView", 0);
    textView.click();

    // It should be selected now
    layout.requireSelection(Collections.singletonList(textView));

    // Let's check the property sheet
    PropertySheetFixture propertySheet = layout.getPropertySheetFixture();
    PropertyFixture property = propertySheet.findProperty("text");
    assertNotNull(property);
    property.requireDisplayName("text");
    property.requireValue("@string/hello_world");
    property.enterValue("New Label");
    layout.waitForNextRenderToFinish();
    property.requireValue("New Label");

    LayoutPaletteFixture palette = layout.getPaletteFixture();
    LayoutPaletteComponentFixture imageView = palette.findByName("ImageView");
    assertNotNull(imageView);
    imageView.requireTitle("ImageView");
    imageView.requireTag("ImageView");
    imageView.requireCategory("Widgets");

    // Check id editing (has custom editor which strips out id prefixes
    PropertyFixture idProperty = propertySheet.findProperty("id");
    assertThat(idProperty).as("ID property").isNotNull();
    assert idProperty != null; // for null analysis
    idProperty.enterValue("tv1");
    idProperty.requireXmlValue("@+id/tv1");

    // This will work when PropertyFixture#getValue properly reads the actual displayed text
    // rather than the property model value
    //idProperty.requireValue("tv1");
    idProperty.requireValue("@+id/tv1");

    idProperty.enterInvalidValue("new", "Java keyword");

    // Check XML escaping: regression test for
    //   https://code.google.com/p/android/issues/detail?id=76283
    property.enterValue("a < b > c & d ' e \" f");
    layout.waitForNextRenderToFinish();
    property.requireValue("a < b > c & d ' e \" f");
    // make sure XML source was escaped properly
    editor.selectEditorTab(EditorFixture.Tab.EDITOR);
    editor.moveTo(editor.findOffset("android:text=\"", null, true));
    assertEquals("android:text=\"a &lt; b > c &amp; d &apos; e &quot; f\"", editor.getCurrentLineContents(true, false, 0));
  }

  @Test
  @IdeGuiTest
  public void testDeletion() throws Exception {
    IdeFrameFixture projectFrame = openSimpleApplication();

    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);
    LayoutEditorFixture layout = editor.getLayoutEditor(false);
    assertNotNull(layout);
    layout.waitForNextRenderToFinish();

    // Find and click the first text view
    LayoutEditorComponentFixture textView = layout.findView("TextView", 0);
    textView.click();

    assertEquals("Device Screen\n" +
                 "    RelativeLayout\n" +
                 "        *TextView - @string/hello_world\n", layout.describeComponentTree(true));

    projectFrame.invokeMenuPath("Edit", "Delete");

    layout.waitForNextRenderToFinish();
    layout.requireComponentTree("Device Screen\n" +
                                "    *RelativeLayout\n", true);

    projectFrame.invokeMenuPathRegex("Edit", "Undo.*");

    layout.waitForNextRenderToFinish();
    layout.requireComponentTree("Device Screen\n" +
                                "    RelativeLayout\n" +
                                "        TextView - @string/hello_world\n", false);


    // Check that we can't delete the root component
    LayoutEditorComponentFixture relativeLayout = layout.findView("RelativeLayout", 0);
    relativeLayout.click();

    layout.requireComponentTree("Device Screen\n" +
                                "    *RelativeLayout\n" +
                                "        TextView - @string/hello_world\n", true);

    projectFrame.invokeMenuPath("Edit", "Delete");

    layout.requireComponentTree("Device Screen\n" +
                                "    RelativeLayout\n" + // still there!
                                "        TextView - @string/hello_world\n", false);
  }
}
