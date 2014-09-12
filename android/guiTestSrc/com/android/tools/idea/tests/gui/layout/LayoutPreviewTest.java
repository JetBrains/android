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

import com.android.SdkConstants;
import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.annotation.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.ConfigurationToolbarFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.ImageFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.LayoutPreviewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.RenderErrorPanelFixture;
import com.android.tools.lint.detector.api.LintUtils;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static com.android.SdkConstants.DOT_PNG;
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
  // Default folder in the GUI test data directory where we're storing rendering thumbnails
  public static final String THUMBNAIL_FOLDER = "thumbnails";

  @Test
  @IdeGuiTest
  public void testConfigurationTweaks() throws Exception {
    // Open an editor, wait for the layout preview window to open, toggle
    // orientation to landscape, create a landscape variation file, ensure
    // it's in the right folder, toggle orientation back, ensure file switched
    // back to the portrait/original file
    IdeFrameFixture projectFrame = newProject("LayoutPreviewTest1").withActivity("MyActivity").create();

    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR);
    editor.requireFolderName("layout");
    LayoutPreviewFixture preview = editor.getLayoutPreview(true);
    assertNotNull(preview);
    preview.waitForNextRenderToFinish();
    preview.requireRenderSuccessful();
    ConfigurationToolbarFixture toolbar = preview.getToolbar();
    toolbar.requireTheme("@style/AppTheme");
    toolbar.requireDevice("Nexus 4");
    toolbar.requireOrientation("Portrait");

    toolbar.chooseDevice("Nexus 5");
    toolbar.toggleOrientation();
    preview.waitForNextRenderToFinish();
    toolbar.requireOrientation("Landscape");
    toolbar.requireDevice("Nexus 5");

    toolbar.createLandscapeVariation();
    preview.waitForNextRenderToFinish();
    preview.requireRenderSuccessful();
    editor.requireFolderName("layout-land");
    toolbar.requireOrientation("Landscape");

    toolbar.toggleOrientation();

    preview.waitForNextRenderToFinish();
    toolbar.requireOrientation("Portrait");
    // We should have switched back to the first file again since -land doesn't match portrait
    editor.requireFolderName("layout");

    toolbar.createOtherVariation("layout-v17");
    preview.waitForNextRenderToFinish();
    preview.requireRenderSuccessful();
    editor.requireFolderName("layout-v17");
    toolbar.requireDevice("Nexus 5"); // The device shouldn't have changed.

    toolbar.toggleOrientation();
    preview.waitForNextRenderToFinish();
    preview.requireRenderSuccessful();
    toolbar.requireDevice("Nexus 5");  // We should still be using the same device.
    toolbar.requireOrientation("Landscape");
    // The file should have switched to layout-land.
    editor.requireFolderName("layout-land");

    toolbar.toggleOrientation();
    preview.waitForNextRenderToFinish();
    preview.requireRenderSuccessful();
    toolbar.requireDevice("Nexus 5");
    toolbar.requireOrientation("Portrait");
    editor.requireFolderName("layout");

    toolbar.chooseDevice("Nexus 4");
    preview.waitForNextRenderToFinish();
    preview.requireRenderSuccessful();
    // We should still be in the same file.
    editor.requireFolderName("layout");
  }

  @Test
  @IdeGuiTest(closeProjectBeforeExecution = true)
  public void testEdits() throws Exception {
    IdeFrameFixture projectFrame = newProject("LayoutPreviewTest2").withActivity("MyActivity").create();

    // Load layout, wait for render to be shown in the preview window
    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);
    LayoutPreviewFixture preview = editor.getLayoutPreview(true);
    assertNotNull(preview);
    editor.requireName("activity_my.xml");
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
    preview.waitForNextRenderToFinish();
    RenderErrorPanelFixture renderErrors = preview.getRenderErrors();
    renderErrors.requireHaveRenderError("One or more layouts are missing the layout_width or layout_height attributes");

    // Invoke one of the suggested fixes and make sure the XML is updated properly
    renderErrors.performSuggestion("Automatically add all missing attributes");

    assertEquals("        android:layout_height=\"wrap_content\" />\n" +
                 "        <Button android:text=\"New Button\"\n" +
                 "            android:layout_width=\"wrap_content\"\n" +
                 "            android:layout_height=\"wrap_content\" />\n" +
                 "    ^\n" +
                 "\n" +
                 "</RelativeLayout>\n", editor.getCurrentLineContents(false, true, 4));

    // And now it should re-render and be successful again
    preview.waitForNextRenderToFinish();
    preview.requireRenderSuccessful();
  }

  @Test
  @IdeGuiTest(closeProjectBeforeExecution = true)
  public void testConfigurationMatching() throws Exception {
    // Opens the LayoutTest project, opens a layout with a custom view, checks
    // that it can't render yet (because the project hasn't been built),
    // builds the project, checks that the render works, edits the custom view
    // source code, ensures that the render lists the custom view as out of date,
    // applies the suggested fix to build the project, and finally asserts that the
    // build is now successful.

    IdeFrameFixture projectFrame = openProject("LayoutTest");
    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/main/res/layout/layout2.xml", EditorFixture.Tab.EDITOR);
    LayoutPreviewFixture preview = editor.getLayoutPreview(true);
    assertNotNull(preview);
    ConfigurationToolbarFixture toolbar = preview.getToolbar();
    toolbar.chooseDevice("Nexus 5");
    preview.waitForNextRenderToFinish();
    toolbar.requireDevice("Nexus 5");
    editor.requireFolderName("layout");
    toolbar.requireOrientation("Portrait");

    toolbar.chooseDevice("Nexus 7");
    preview.waitForNextRenderToFinish();
    toolbar.requireDevice("Nexus 7 2013");
    editor.requireFolderName("layout-sw600dp");

    toolbar.chooseDevice("Nexus 10");
    preview.waitForNextRenderToFinish();
    toolbar.requireDevice("Nexus 10");
    editor.requireFolderName("layout-sw600dp");
    toolbar.requireOrientation("Landscape"); // Default orientation for Nexus 10

    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR);
    preview.waitForNextRenderToFinish();
    toolbar.requireDevice("Nexus 10"); // Since we switched to it most recently
    toolbar.requireOrientation("Portrait");

    toolbar.chooseDevice("Nexus 7");
    preview.waitForNextRenderToFinish();
    toolbar.chooseDevice("Nexus 4");
    preview.waitForNextRenderToFinish();
    editor.open("app/src/main/res/layout-sw600dp/layout2.xml", EditorFixture.Tab.EDITOR);
    preview.waitForNextRenderToFinish();
    editor.requireFolderName("layout-sw600dp");
    toolbar.requireDevice("Nexus 7 2013"); // because it's the most recently configured sw600-dp compatible device
    editor.open("app/src/main/res/layout/layout2.xml", EditorFixture.Tab.EDITOR);
    preview.waitForNextRenderToFinish();
    toolbar.requireDevice("Nexus 4"); // because it's the most recently configured small screen compatible device
  }

  @NotNull
  private String suggestName(EditorFixture editor) {
    String currentFileName = editor.getCurrentFileName();
    assertNotNull(currentFileName);
    String prefix = this.getClass().getSimpleName() + "-" + getTestName();
    String pngFileName = LintUtils.getBaseName(currentFileName) + DOT_PNG;
    return THUMBNAIL_FOLDER + "/" + prefix + "-" + pngFileName;
  }

  @Test
  @IdeGuiTest(closeProjectBeforeExecution = true)
  public void testRendering() throws Exception {
    // Opens a number of layouts in the layout test project and checks that the rendering looks roughly
    // correct.

    IdeFrameFixture projectFrame = openProject("LayoutTest");
    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/main/res/layout/widgets.xml", EditorFixture.Tab.EDITOR);
    LayoutPreviewFixture preview = editor.getLayoutPreview(true);
    assertNotNull(preview);
    ConfigurationToolbarFixture toolbar = preview.getToolbar();
    preview.waitForNextRenderToFinish();
    if (!toolbar.isDevice("Nexus 5")) {
      toolbar.chooseDevice("Nexus 5");
      preview.waitForNextRenderToFinish();
    }

    // Basic layout, including action bar, device frame, Holo-based app theme
    preview.requireThumbnailMatch(suggestName(editor));

    //// Drawable shape
    editor.open("app/src/main/res/drawable/progress.xml", EditorFixture.Tab.EDITOR);
    preview.waitForNextRenderToFinish();
    preview.requireThumbnailMatch(suggestName(editor));

    // Using an included layout, and various gradients and text styles
    editor.open("app/src/main/res/layout/textstyles.xml", EditorFixture.Tab.EDITOR);
    preview.waitForNextRenderToFinish();
    preview.requireThumbnailMatch(suggestName(editor));

    // Included render: inner layout rendered inside an outer layout
    editor.open("app/src/main/res/layout/included.xml", EditorFixture.Tab.EDITOR);
    preview.waitForNextRenderToFinish();
    preview.requireThumbnailMatch(suggestName(editor));

    // Render menu
    editor.open("app/src/main/res/menu/my.xml", EditorFixture.Tab.EDITOR);
    preview.waitForNextRenderToFinish();
    preview.requireThumbnailMatch(suggestName(editor));

    // TODO:
    // Disabling device frames
    // Multi configuration editing
    // Material design rendering
    // Theme switching
    // Menu rendering
    // Android Wear, Android TV form factor rendering
    // Switching rendering targets
    // Editing resource files and making sure style updates
    // RTL rendering
    // ScrollViews (no device clipping)
  }
}
