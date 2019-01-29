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

import com.android.testutils.filesystemdiff.FileSystemEntry;
import com.android.testutils.filesystemdiff.TreeBuilder;
import com.android.testutils.filesystemdiff.TreeDifferenceEngine;
import com.android.tools.idea.tests.gui.assetstudio.NewImageAssetTest;
import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.assetstudio.AssetStudioWizardFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.core.MouseButton;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

/**
 * UI tests for the layout editor
 */
@RunWith(GuiTestRemoteRunner.class)
public class LayoutEditorTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  /**
   * Verifies that Image Asset feature works and appropriate resources are generated
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: b121524b-73b3-49a6-9463-d92078283bbc
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleLocalApplication
   *   2. Right click the app module
   *   3. Click “New” -> click “Image Asset”
   *   4. Make a selection for “Asset Type” and “Foreground”
   *   5. Click Next
   *   6. Click Finish
   *   7. Verify 1 and 2
   *   Verify:
   *   1. Resources are generated based on selection
   *   2. The corresponding png shows up in the mipmap-*dpi (for launcher icon) or drawable-*dpi directory
   *   </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void imageAssetRegressionTest() throws Exception {
    guiTest.importSimpleLocalApplication();

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
