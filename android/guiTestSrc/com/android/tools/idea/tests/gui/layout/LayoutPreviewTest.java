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

import com.android.repository.Revision;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.FileFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.ConfigurationToolbarFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.LayoutPreviewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.LayoutWidgetFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.RenderErrorPanelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.TagMatcher.AttributeMatcher;
import com.android.tools.lint.detector.api.LintUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.tests.gui.framework.GuiTests.getProjectCreationDirPath;
import static com.android.tools.idea.tests.gui.framework.TestGroup.LAYOUT;
import static com.intellij.lang.annotation.HighlightSeverity.ERROR;
import static junit.framework.Assert.*;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

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
@BelongsToTestGroups({LAYOUT})
@RunWith(GuiTestRunner.class)
public class LayoutPreviewTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  // Default folder in the GUI test data directory where we're storing rendering thumbnails
  public static final String THUMBNAIL_FOLDER = "thumbnails";

  @Rule public final TestName myTestName = new TestName();

  @Ignore("failed in http://go/aj/job/studio-ui-test/389 and from IDEA")
  @Test
  public void testConfigurationTweaks() throws Exception {
    // Open an editor, wait for the layout preview window to open, toggle
    // orientation to landscape, create a landscape variation file, ensure
    // it's in the right folder, toggle orientation back, ensure file switched
    // back to the portrait/original file
    guiTest.importSimpleApplication();

    EditorFixture editor = guiTest.ideFrame().getEditor();
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
  @Ignore("http://b.android.com/76009")
  public void testPreviewConfigurationTweaks() throws Exception {
    guiTest.importSimpleApplication();

    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR);
    editor.requireFolderName("layout");
    LayoutPreviewFixture preview = editor.getLayoutPreview(true);
    assertNotNull(preview);
    preview.waitForNextRenderToFinish();
    preview.requireRenderSuccessful();
    ConfigurationToolbarFixture toolbar = preview.getToolbar();

    String rtl = "RTL";
    String original = "Original";
    List<String> rtlList = Collections.singletonList(rtl);
    List<String> originalList = Collections.singletonList(original);
    toolbar.chooseLocale("Preview Right-to-Left Layout");
    preview.waitForNextRenderToFinish();
    preview.requirePreviewTitles(rtlList);
    for (int i = 0; i < 2; i++) {
      preview.switchToPreview(rtl);
      preview.waitForNextRenderToFinish();
      preview.requirePreviewTitles(originalList);
      preview.switchToPreview(original);
      preview.waitForNextRenderToFinish();
      preview.requirePreviewTitles(rtlList);
    }
    toolbar.removePreviews();
  }

  @Test
  @Ignore("http://b.android.com/203392")
  public void testEdits() throws Exception {
    guiTest.importSimpleApplication();

    // Load layout, wait for render to be shown in the preview window
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR);
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
  public void testConfigurationMatching() throws Exception {
    // Opens the LayoutTest project, opens a layout with a custom view, checks
    // that it can't render yet (because the project hasn't been built),
    // builds the project, checks that the render works, edits the custom view
    // source code, ensures that the render lists the custom view as out of date,
    // applies the suggested fix to build the project, and finally asserts that the
    // build is now successful.

    guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");
    EditorFixture editor = guiTest.ideFrame().getEditor();
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
    String prefix = this.getClass().getSimpleName() + "-" + myTestName.getMethodName();
    String pngFileName = LintUtils.getBaseName(currentFileName) + DOT_PNG;
    return THUMBNAIL_FOLDER + File.separatorChar + prefix + '-' + pngFileName;
  }

  @Test
  @Ignore("http://b.android.com/203392")
  public void testRendering() throws Exception {
    // Opens a number of layouts in the layout test project and checks that the rendering looks roughly
    // correct.

    guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");
    EditorFixture editor = guiTest.ideFrame().getEditor();
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
    // Currently broken: https://code.google.com/p/android/issues/detail?id=174236
    //editor.open("app/src/main/res/menu/my.xml", EditorFixture.Tab.EDITOR);
    //preview.waitForNextRenderToFinish();
    //preview.requireThumbnailMatch(suggestName(editor));
    System.out.println("Not checking menu rendering: re-enable when issue 174236 is fixed");

    // Make sure the project is built: we need custom views for the porter duff test
    GradleInvocationResult result = guiTest.ideFrame().invokeProjectMake();
    assertTrue(result.isBuildSuccessful());

    // PorterDuff
    editor.open("app/src/main/res/layout/porterduff.xml", EditorFixture.Tab.EDITOR);
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

  @Test
  public void testEditCustomView() throws Exception {
    // Opens the LayoutTest project, opens a layout with a custom view, checks
    // that it can't render yet (because the project hasn't been built),
    // builds the project, checks that the render works, edits the custom view
    // source code, ensures that the render lists the custom view as out of date,
    // applies the suggested fix to build the project, and finally asserts that the
    // build is now successful.

    guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/layout1.xml", EditorFixture.Tab.EDITOR);
    LayoutPreviewFixture preview = editor.getLayoutPreview(true);
    assertNotNull(preview);
    preview.waitForNextRenderToFinish();

    String viewClassFile = "app/build/intermediates/classes/debug/com/android/tools/tests/layout/MyButton.class";
    if (guiTest.ideFrame().findFileByRelativePath(viewClassFile, false) != null) {
      fail("Project should be clean at the start of this test; when that is not the case it's probably " +
           "some caching of loaded projects in '" + getProjectCreationDirPath().getPath() + "' which " +
           "we will soon get rid of.");
    }

    RenderErrorPanelFixture renderErrors = preview.getRenderErrors();
    renderErrors.requireHaveRenderError("The following classes could not be found");
    renderErrors.requireHaveRenderError("com.android.tools.tests.layout.MyButton");
    renderErrors.requireHaveRenderError("Change to android.widget.Button");

    GradleInvocationResult result = guiTest.ideFrame().invokeProjectMake();
    assertTrue(result.isBuildSuccessful());

    // Build completion should trigger re-render
    preview.waitForNextRenderToFinish();
    preview.requireRenderSuccessful();

    // Next let's edit the custom view source file
    editor.open("app/src/main/java/com/android/tools/tests/layout/MyButton.java", EditorFixture.Tab.EDITOR);
    editor.moveTo(editor.findOffset("extends Button {", null, true));
    editor.enterText(" // test");

    // Switch back; should trigger render:
    editor.open("app/src/main/res/layout/layout1.xml", EditorFixture.Tab.EDITOR);
    preview.waitForNextRenderToFinish();
    renderErrors.requireHaveRenderError("The MyButton custom view has been edited more recently than the last build");
    renderErrors.performSuggestion("Build");
    preview.waitForNextRenderToFinish();
    preview.requireRenderSuccessful();

    // Now make some changes to the file which updates the modification timestamp of the source. However,
    // also edit them back and save again (which still leaves a new modification timestamp). Gradle will
    // *not* rebuild if the file contents have not changed (it uses checksums rather than file timestamps).
    // Make sure that we don't get render errors in this scenario! (Regression test for http://b.android.com/76676)
    editor.open("app/src/main/java/com/android/tools/tests/layout/MyButton.java", EditorFixture.Tab.EDITOR);
    editor.moveTo(editor.findOffset("extends Button {", null, true));
    editor.enterText(" ");
    editor.invokeAction(EditorFixture.EditorAction.SAVE);
    editor.invokeAction(EditorFixture.EditorAction.BACK_SPACE);
    editor.invokeAction(EditorFixture.EditorAction.SAVE);
    editor.open("app/src/main/res/layout/layout1.xml", EditorFixture.Tab.EDITOR);
    preview.waitForNextRenderToFinish();
    renderErrors.requireHaveRenderError("The MyButton custom view has been edited more recently than the last build");
    renderErrors.performSuggestion("Build"); // this build won't do anything this time, since Gradle notices checksum has not changed
    preview.waitForNextRenderToFinish();
    preview.requireRenderSuccessful(); // but our build timestamp check this time will mask the out of date warning
  }

  @Test
  public void testRenderingDynamicResources() throws Exception {
    // Opens a layout which contains dynamic resources (defined only in build.gradle)
    // and checks that the values have been resolved correctly (both that there are no
    // unresolved reference errors in the XML file, and that the rendered layout strings
    // matches the expected overlay semantics); also edits these in the Gradle file and
    // checks that the layout rendering is updated after a Gradle sync.

    guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");

    AndroidGradleModel androidModel = guiTest.ideFrame().getAndroidModel("app");
    String modelVersion = androidModel.getAndroidProject().getModelVersion();
    assertNotNull(modelVersion);
    Revision version = Revision.parseRevision(modelVersion);
    assertNotNull("Could not parse version " + modelVersion, version);
    assumeTrue("This test tests behavior that starts working in 0.14.+", version.getMajor() != 0 || version.getMinor() >= 14);

    EditorFixture editor = guiTest.ideFrame().getEditor();
    String layoutFilePath = "app/src/main/res/layout/dynamic_layout.xml";
    editor.open(layoutFilePath, EditorFixture.Tab.EDITOR);
    LayoutPreviewFixture preview = editor.getLayoutPreview(true);
    assertNotNull(preview);
    preview.waitForNextRenderToFinish();

    RenderErrorPanelFixture renderErrors = preview.getRenderErrors();
    renderErrors.requireRenderSuccessful(false, false);

    LayoutWidgetFixture string1 = preview.find(new AttributeMatcher(ATTR_TEXT, ANDROID_URI, "@string/dynamic_string1"));
    string1.requireTag("TextView");
    string1.requireAttribute(ATTR_TEXT, ANDROID_URI, "@string/dynamic_string1");

    string1.requireViewClass("android.widget.TextView");
    string1.requireActualText("String 1 defined only by defaultConfig");

    LayoutWidgetFixture string2 = preview.find(new AttributeMatcher(ATTR_TEXT, ANDROID_URI, "@string/dynamic_string2"));
    string2.requireActualText("String 1 defined only by defaultConfig");

    LayoutWidgetFixture string3 = preview.find(new AttributeMatcher(ATTR_TEXT, ANDROID_URI, "@string/dynamic_string3"));
    string3.requireActualText("String 3 defined by build type debug");

    LayoutWidgetFixture string4 = preview.find(new AttributeMatcher(ATTR_TEXT, ANDROID_URI, "@string/dynamic_string4"));
    string4.requireActualText("String 4 defined by flavor free");

    LayoutWidgetFixture string5 = preview.find(new AttributeMatcher(ATTR_TEXT, ANDROID_URI, "@string/dynamic_string5"));
    string5.requireActualText("String 5 defined by build type debug");

    // Ensure that all the references are properly resolved
    FileFixture file = guiTest.ideFrame().findExistingFileByRelativePath(layoutFilePath);
    file.requireCodeAnalysisHighlightCount(ERROR, 0);

    String buildGradlePath = "app/build.gradle";
    editor.open(buildGradlePath, EditorFixture.Tab.EDITOR);
    editor.moveTo(editor.findOffset("String 1 defined only by |defaultConfig"));
    editor.enterText("edited ");
    guiTest.ideFrame().requireEditorNotification("Gradle files have changed since last project sync").performAction("Sync Now");
    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    editor.open(layoutFilePath, EditorFixture.Tab.EDITOR);
    preview.waitForNextRenderToFinish();

    string1 = preview.find(new AttributeMatcher(ATTR_TEXT, ANDROID_URI, "@string/dynamic_string1"));
    string1.requireActualText("String 1 defined only by edited defaultConfig");

    file.requireCodeAnalysisHighlightCount(ERROR, 0);
  }
}
