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

import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.*;
import org.junit.Ignore;
import org.junit.Test;

import java.awt.*;
import java.util.Collections;

import static com.android.tools.idea.tests.gui.framework.TestGroup.LAYOUT;
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
@BelongsToTestGroups({LAYOUT})
public class LayoutEditorTest extends GuiTestCase {

  @Ignore("failed in http://go/aj/job/studio-ui-test/326 but passed from IDEA")
  @Test @IdeGuiTest
  public void testSetProperty() throws Exception {
    myProjectFrame = importSimpleApplication();

    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = myProjectFrame.getEditor();
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
    assertNotNull(idProperty); // for null analysis
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

    // Check the resource reference symbols escaping
    // https://code.google.com/p/android/issues/detail?id=73101
    editor.selectEditorTab(EditorFixture.Tab.DESIGN);
    // If it's not a valid resource reference, escape the initial symbol.
    property.enterValue("@");
    property.requireValue("\\@");
    property.requireXmlValue("\\@");
    property.enterValue("@string"); // Incomplete reference.
    property.requireValue("\\@string");
    property.requireXmlValue("\\@string");
    // Try a valid references.
    property.enterValue("@string/valid_ref");
    property.requireValue("@string/valid_ref");
    property.requireXmlValue("@string/valid_ref");
    property.enterValue("?android:attr");
    property.requireValue("?android:attr");
    property.requireXmlValue("?android:attr");
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/326 and from IDEA")
  @Test @IdeGuiTest
  public void testDeletion() throws Exception {
    // Tests deletion: Opens a layout, finds the first TextView, deletes it,
    // checks that the component hierarchy shows it as removed. Then performs
    // an undo; checks that the widget is back. Then it ensures that root components
    // cannot be deleted, by selecting it, attempting to delete it, and verifying
    // that it's still there.

    myProjectFrame = importSimpleApplication();

    EditorFixture editor = myProjectFrame.getEditor();
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

    myProjectFrame.invokeMenuPath("Edit", "Delete");

    layout.waitForNextRenderToFinish();
    layout.requireComponentTree("Device Screen\n" +
                                "    *RelativeLayout\n", true);

    myProjectFrame.invokeMenuPathRegex("Edit", "Undo.*");

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

    myProjectFrame.invokeMenuPath("Edit", "Delete");

    layout.requireComponentTree("Device Screen\n" +
                                "    RelativeLayout\n" + // still there!
                                "        TextView - @string/hello_world\n", false);
  }

  @Test @IdeGuiTest
  public void testIdManipulation() throws Exception {
    if (true) {
      System.out.println("Skipping testIdManipulation until http://b.android.com/173576 is fixed\n");
      return;
    }
    // Checks that when we insert new widgets, we assign appropriate id's (they should
    // be unique in the application), and also check that when we copy/paste a component
    // hierarchy, we reassign id's to keep them unique and also update all references within
    // the pasted component to the newly assigned id's.

    myProjectFrame = importProjectAndWaitForProjectSyncToFinish("LayoutTest");

    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = myProjectFrame.getEditor();
    editor.open("app/src/main/res/layout/ids.xml", EditorFixture.Tab.DESIGN);
    LayoutEditorFixture layout = editor.getLayoutEditor(false);
    assertNotNull(layout);
    layout.waitForNextRenderToFinish();

    // Find and click the first text view
    LayoutEditorComponentFixture relativeLayout = layout.findView("RelativeLayout", 0);
    relativeLayout.click();

    myProjectFrame.invokeMenuPath("Edit", "Cut");
    layout.waitForNextRenderToFinish();
    layout.requireComponents("Device Screen\n" + "    *LinearLayout (vertical)\n", true);

    Rectangle viewBounds = relativeLayout.getViewBounds();


    // First copy
    myProjectFrame.invokeMenuPath("Edit", "Paste");
    // The paste action enters a mode where you have to click where you want to paste/insert: provide that click
    Point p = new Point(viewBounds.x + 5, viewBounds.y + 5);
    layout.moveMouse(p);
    layout.click();

    layout.waitForNextRenderToFinish();
    layout.requireComponents("Device Screen\n" +
                             "    LinearLayout (vertical)\n" +
                             "        *RelativeLayout\n" +
                             "            buttonid1 - \"Button\"\n" +
                             "            buttonid2 - \"Button 2\"\n", true);

    // Second copy: should use unique id's (and update relative references)
    myProjectFrame.invokeMenuPath("Edit", "Paste");
    p = new Point(viewBounds.x + 20, viewBounds.y + viewBounds.height + 150);
    layout.moveMouse(p);
    layout.click();
    layout.waitForNextRenderToFinish();
    layout.requireComponents("Device Screen\n" +
                             "    LinearLayout (vertical)\n" +
                             "        RelativeLayout\n" +
                             "            buttonid1 - \"Button\"\n" +
                             "            buttonid2 - \"Button 2\"\n" +
                             "            *RelativeLayout\n" +
                             // Note: button1 and button2 are already present in the project (not
                             // in this layout) so button3 is the first unique available button name
                             "                button3 - \"Button\"\n" +
                             "                button4 - \"Button 2\"\n", true);

    LayoutEditorComponentFixture button4 = layout.findViewById("button4");
    button4.requireXml("<Button\n" +
                       "android:layout_width=\"wrap_content\"\n" +
                       "android:layout_height=\"wrap_content\"\n" +
                       "android:text=\"Button 2\"\n" +
                       "android:id=\"@+id/button4\"\n" +
                       "android:layout_below=\"@+id/button3\"\n" +
                       "android:layout_toRightOf=\"@+id/button3\"\n" + // notice how this now points to button3, not buttonid1
                       "android:layout_toEndOf=\"@+id/button3\" />", true);
  }
}
