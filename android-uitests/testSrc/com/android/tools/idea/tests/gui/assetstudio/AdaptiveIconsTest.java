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
package com.android.tools.idea.tests.gui.assetstudio;

import static com.android.tools.idea.tests.gui.assetstudio.FileUtilsKt.getNewFiles;
import static com.google.common.truth.Truth.assertThat;

import com.android.testutils.filesystemdiff.FileSystemEntry;
import com.android.testutils.filesystemdiff.TreeBuilder;
import com.android.testutils.filesystemdiff.TreeDifferenceEngine;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.assetstudio.AssetStudioWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.assetstudio.NewImageAssetStepFixture;
import com.android.tools.idea.tests.gui.uibuilder.RenderTaskLeakCheckRule;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AdaptiveIconsTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);
  @Rule public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

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
   *   4. Select "Adaptive and Legacy" icon type
   *   5. Verify 1
   *   6. Click Next
   *   7. Click Finish
   *   8. Verify 2 and 3
   *   Verify:
   *   1. Preview panel shows the proper set of icons
   *   2. Resources are generated based on selection
   *   3. The corresponding webp shows up in the mipmap-*dpi (for launcher icon) or drawable-*dpi directory
   *   </pre>
   */
  @Test
  public void testPreviewAndGeneration() throws Exception {
    AssetStudioWizardFixture wizard = guiTest.importSimpleApplication()
      .getProjectView()
      .selectAndroidPane()
      .clickPath("app")
      .openFromMenu(AssetStudioWizardFixture::find, "File", "New", "Image Asset");

    Path projectDir = guiTest.getProjectPath().toPath();
    FileSystemEntry original = TreeBuilder.buildFromFileSystem(projectDir);

    NewImageAssetStepFixture<AssetStudioWizardFixture> step = wizard.getImageAssetStep();
    guiTest.robot().waitForIdle();
    step.selectIconType("Launcher Icons (Adaptive and Legacy)");
    assertThat(step.getPreviewPanelCount()).isEqualTo(1);
    assertThat(step.getPreviewPanelIconNames(0)).containsExactly(
      "Circle", "Squircle", "Rounded Square", "Square", "Full Bleed Layers", "Legacy Icon", "Round Icon", "Google Play Store")
      .inOrder();
    wizard.clickNext();
    wizard.selectResFolder("main");
    wizard.clickFinish();

    FileSystemEntry changed = TreeBuilder.buildFromFileSystem(projectDir);

    Path filterPath = projectDir.resolve("app/src");
    List<String> newFiles =
      getNewFiles(projectDir, TreeDifferenceEngine.computeEditScript(original, changed), path -> path.startsWith(filterPath));
    assertThat(newFiles).containsExactly(
      "app/src/main/res/mipmap-hdpi/ic_launcher.webp",
      "app/src/main/res/mipmap-mdpi/ic_launcher.webp",
      "app/src/main/res/mipmap-xhdpi/ic_launcher.webp",
      "app/src/main/res/mipmap-xxhdpi/ic_launcher.webp",
      "app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp",
      "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
      "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
      "app/src/main/res/mipmap-hdpi/ic_launcher_round.webp",
      "app/src/main/res/mipmap-mdpi/ic_launcher_round.webp",
      "app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp",
      "app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp",
      "app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp",
      "app/src/main/ic_launcher-playstore.png",
      "app/src/main/res/mipmap-hdpi/ic_launcher_background.webp",
      "app/src/main/res/mipmap-hdpi/ic_launcher_foreground.webp",
      "app/src/main/res/mipmap-mdpi/ic_launcher_background.webp",
      "app/src/main/res/mipmap-mdpi/ic_launcher_foreground.webp",
      "app/src/main/res/mipmap-xhdpi/ic_launcher_background.webp",
      "app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.webp",
      "app/src/main/res/mipmap-xxhdpi/ic_launcher_background.webp",
      "app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.webp",
      "app/src/main/res/mipmap-xxxhdpi/ic_launcher_background.webp",
      "app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp"
    );
  }
}
