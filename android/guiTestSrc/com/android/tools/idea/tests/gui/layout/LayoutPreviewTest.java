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
import com.android.tools.idea.tests.gui.framework.fixture.layout.ConfigurationToolbarFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.LayoutPreviewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.RenderErrorPanelFixture;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

/**
 * Unit test for the layout preview window
 * <p>
 * Additional things we should test:
 * <ul>
 *   <li>Comprehensive device matching</li>
 *   <li>Multi configuration editing</li>
 *   <li>All the render quick fixes</li>
 *   <li>Editor caret syncing</li>
 *   <li>Included rendering</li>
 *   <li>Other file types than layouts (e.g. drawables etc)</li>
 *   <li>Render thumbnails</li>
 * </ul>
 */
public class LayoutPreviewTest extends GuiTestCase {
  @SuppressWarnings("ConstantConditions")
  @Test
  @IdeGuiTest
  public void testConfigurationTweaks() throws Exception {
    // Open an editor, wait for the layout preview window to open, toggle
    // orientation to landscape, create a landscape variation file, ensure
    // it's in the right folder, toggle orientation back, ensure file switched
    // back to the portrait/original file
    IdeFrameFixture projectFrame = new ProjectDescriptor("LayoutPreviewTest1").activity("MyActivity").create();

    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR);
    assertEquals("layout", editor.getCurrentFile().getParent().getName());
    LayoutPreviewFixture preview = editor.getLayoutPreview(true);
    assertNotNull(preview);
    Object firstRender = preview.waitForRenderToFinish();
    preview.requireRenderSuccessful();
    ConfigurationToolbarFixture toolbar = preview.getToolbar();
    toolbar.requireTheme("@style/AppTheme");
    toolbar.requireDevice("Nexus 4");
    toolbar.requireOrientation("Portrait");

    toolbar.chooseDevice("Nexus 5");
    toolbar.toggleOrientation();
    Object secondRender = preview.waitForNextRenderToFinish(firstRender);
    toolbar.requireOrientation("Landscape");
    toolbar.requireDevice("Nexus 5");

    toolbar.createLandscapeVariation();
    Object thirdRender = preview.waitForNextRenderToFinish(secondRender);
    preview.requireRenderSuccessful();
    assertEquals("layout-land", editor.getCurrentFile().getParent().getName());
    toolbar.requireOrientation("Landscape");

    toolbar.toggleOrientation();
    preview.waitForNextRenderToFinish(thirdRender);
    toolbar.requireOrientation("Portrait");
    // We should have switched back to the first file again since -land doesn't match portrait
    assertEquals("layout", editor.getCurrentFile().getParent().getName());
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  @IdeGuiTest(closeProjectBeforeExecution = true)
  public void testEdits() throws Exception {
    IdeFrameFixture projectFrame = new ProjectDescriptor("LayoutPreviewTest2").activity("MyActivity").create();

    // Load layout, wait for render to be shown in the preview window
    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);
    assertEquals("layout", editor.getCurrentFile().getParent().getName());
    LayoutPreviewFixture preview = editor.getLayoutPreview(true);
    assertNotNull(preview);
    preview.waitForRenderToFinish();

    // Move caret to right after the end of the <TextView> element declaration
    editor.moveTo(editor.findOffset("android:layout_height=\"wrap_content\" />", null, true));
    assertEquals("    <TextView\n" +
                 "        android:text=\"@string/hello_world\"\n" +
                 "        android:layout_width=\"wrap_content\"\n" +
                 "        android:layout_height=\"wrap_content\" />^\n" +
                 "\n" +
                 "</RelativeLayout>\n", editor.getCurrentLineContents(false, true, 3));

    // Then enter some text, which should trigger render warnings:
    editor.enterText("\n    <Button android:text=\"New Button\"/>\n");
    Object firstRender = preview.waitForRenderToFinish();
    RenderErrorPanelFixture renderErrors = preview.getRenderErrors();
    renderErrors.requireHaveRenderError("One or more layouts are missing the layout_width or layout_height attributes");

    // Invoke one of the suggested fixes and make sure the XML is updated properly
    renderErrors.performSuggestion(">Automatically add all missing attributes");

    assertEquals("        android:layout_height=\"wrap_content\" />\n" +
                 "        <Button android:text=\"New Button\"\n" +
                 "            android:layout_width=\"wrap_content\"\n" +
                 "            android:layout_height=\"wrap_content\" />\n" +
                 "    ^\n" +
                 "\n" +
                 "</RelativeLayout>\n", editor.getCurrentLineContents(false, true, 4));

    // And now it should re-render and be successful again
    preview.waitForNextRenderToFinish(firstRender);
    preview.requireRenderSuccessful();
  }
}
