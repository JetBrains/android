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

import com.android.SdkConstants;
import com.android.testutils.filesystemdiff.FileSystemEntry;
import com.android.testutils.filesystemdiff.TreeBuilder;
import com.android.testutils.filesystemdiff.TreeDifferenceEngine;
import com.android.tools.idea.tests.gui.assetstudio.NewImageAssetTest;
import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.assetstudio.AssetStudioWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.assetstudio.NewImageAssetStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import org.fest.swing.core.MouseButton;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

/**
 * UI tests for the layout editor
 */
@RunWith(GuiTestRunner.class)
public class LayoutEditorTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  /**
   * Verifies that Asset Studio features work
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: e5a42dd1-c55c-460e-b8e5-62f715e70f03
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open Android Studio
   *   2. Import SimpleApplication
   *   3. Right click the default app module (or any manually created module) and select New > Vector Asset (Verify 1)
   *   4. Click on "Choose Icon"
   *   5. Select an Icon and click OK
   *   6. Check the check box of “Override default size …” and modify the size to be something like 48 x 24 (Verify 2)
   *   7. Modify the Opacity slider to a small value, like 50. (Verify 3)
   *   8. Check the checkbox of “Enable auto mirroring… " and click Next (Verify 4)
   *   9. Hit “Finish” button
   *   10. In the folder, find the XML file just created, double click it. (Verify 5)
   *   Verify:
   *   1. Vector Asset Studio is displayed. A vector drawable preview is displayed (initially blank) with option
   *   to choose an icon and Resource name
   *   2. The image become scale within the bound accordingly
   *   3. The image become translucent.
   *   4. An XML file is created under /res/drawable. The preview should match the previous dialog.
   *   5. The file content should have some details And if preview pane is enabled, then a similar preview image should show up.
   *   there are android:alpha attributes and android:autoMirrored created.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void androidVectorDrawableTool() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    AssetStudioWizardFixture assetStudioWizardFixture = guiTest.ideFrame()
      .getProjectView()
      .selectAndroidPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "app")
      .openFromMenu(AssetStudioWizardFixture::find, "File", "New", "Vector Asset");
    assetStudioWizardFixture.chooseIcon(ideFrameFixture)
      .clickOk();
    assetStudioWizardFixture
      .enableOverrideDefaultSize()
      .setSize(48, 24)
      .setOpacity(50)
      .enableAutoMirror()
      .clickNext()
      .clickFinish();

    String contents = ideFrameFixture.getEditor()
      .open("app/src/main/res/drawable/ic_android_black_24dp.xml")
      .getCurrentFileContents();
    assertThat(contents).contains("android:width=\"48dp\"");
    assertThat(contents).contains("android:height=\"24dp\"");
    assertThat(contents).contains("xmlns:android=\"http://schemas.android.com/apk/res/android\"");
  }

  /**
   * Verifies that SVG images can be loaded in Asset Studio
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 5b7ac387-09b9-41a3-85cf-1f89659c65aa
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open Android Studio
   *   2. Import SimpleApplication
   *   3. Right click the default app module (or any manually created module) and select New > Vector Asset (Verify)
   *   4. Select Local SVG file option.
   *   5. Choose an image file from the local system.
   *   Verify:
   *   For supported files, conversion to svg should succeed.
   *   For unsupported files, “Errors” dialog should display at the bottom of Configure vector asset window with appropriate message.
   *   </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // b/68001739
  @Test
  public void imageAssetErrorCheck() throws Exception {
    guiTest.importSimpleApplication();
    AssetStudioWizardFixture assetStudioWizardFixture = guiTest.ideFrame()
      .getProjectView()
      .selectAndroidPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "app")
      .openFromMenu(AssetStudioWizardFixture::find, "File", "New", "Vector Asset");

    String invalidTip = "The specified asset could not be parsed. Please choose another asset.";

    assetStudioWizardFixture.useLocalFile(
      findFileByIoFile(new File(GuiTests.getTestDataDir() + "/TestImages/call.svg"), true));
    // The invalid tip will show there before parsing an image finishes.
    // So, we should wait until the invalid tip is not shown.
    GuiTests.waitUntilGone(guiTest.robot(),
                           assetStudioWizardFixture.target(),
                           Matchers.byText(JLabel.class, invalidTip).andIsShowing());

    assetStudioWizardFixture.useLocalFile(
      findFileByIoFile(new File(GuiTests.getTestDataDir() + "/TestImages/android_wrong.svg"), true));
    GuiTests.waitUntilShowing(guiTest.robot(),
                              assetStudioWizardFixture.target(),
                              Matchers.byText(JLabel.class, invalidTip).andIsShowing());

    assetStudioWizardFixture.clickCancel();
  }

  /**
   * Verifies that vector drawable project can be deployed successfully
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 28407c0e-1415-4470-90f3-75cf9d330d03
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication
   *   2. Create a vector drawable under directory /res/drawable and name it “local_library.xml”
   *   3. Modify the existing main content view, then add one button, set its background as the Vector Drawable
   *   <button android:background="@drawable/local_library" android:layout_width="wrap_content" android:layout_height="wrap_content"/>
   *   Verify:
   *   Make sure the “preview” pane displays the layout with the drawable correctly.
   *   </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // b/69420548
  @Test
  public void imageAssetGradleTest() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    ideFrameFixture
      .getProjectView()
      .selectAndroidPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "app")
      .openFromMenu(AssetStudioWizardFixture::find, "File", "New", "Vector Asset")
      .setName("local_library")
      .clickNext()
      .clickFinish();

    ideFrameFixture.getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(true)
      .dragComponentToSurface("Buttons", "Button");

    NlComponentFixture button = ideFrameFixture.getEditor()
      .selectEditorTab(EditorFixture.Tab.EDITOR)
      .moveBetween("<Button", "")
      .enterText("\nandroid:background=\"@drawable/local_library\"")
      .getLayoutEditor(true)
      .waitForRenderToFinish()
      .findView("Button", 0);
    assertThat(button.getComponent().getAttribute(SdkConstants.ANDROID_URI, "background"))
      .isEqualTo("@drawable/local_library");
  }

  /**
   * Verifies that Image Asset feature works and appropriate resources are generated
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: b121524b-73b3-49a6-9463-d92078283bbc
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication
   *   2. Right click the app module
   *   3. Click “New” -> click “Image Asset”
   *   4. Make a selection for “Asset Type” and “Foreground”
   *   5. Click Next
   *   6. Click Finish
   *   7. Verify 1 and 2
   *   Verify:
   *   1. Resources are generated based on selection
   *   2. The corresponding png shows up in the mipmap-*dpi (for launcher icon) or drawable-*dpi directory.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void imageAssetRegressionTest() throws Exception {
    guiTest.importSimpleApplication();

    Path projectDir = guiTest.getProjectPath().toPath();
    FileSystemEntry original = TreeBuilder.buildFromFileSystem(projectDir);

    guiTest.ideFrame()
      .getProjectView()
      .selectAndroidPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "app")
      .openFromMenu(AssetStudioWizardFixture::find, "File", "New", "Image Asset")
      .getImageAssetStep()
      .selectIconType("Launcher Icons (Legacy only)")
      .selectClipArt()
      .setForeground(new Color(200, 0, 0, 200))
      .wizard()
      .clickNext()
      .clickFinish();

    FileSystemEntry changed = TreeBuilder.buildFromFileSystem(projectDir);
    List<String> newFiles = NewImageAssetTest.getNewFiles(projectDir, TreeDifferenceEngine.computeEditScript(original, changed));
    assertThat(newFiles).containsExactly("app/src/main/ic_launcher-web.png",
                                         "app/src/main/res/mipmap-hdpi/ic_launcher.png",
                                         "app/src/main/res/mipmap-mdpi/ic_launcher.png",
                                         "app/src/main/res/mipmap-xhdpi/ic_launcher.png",
                                         "app/src/main/res/mipmap-xxhdpi/ic_launcher.png",
                                         "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png");
  }
}
