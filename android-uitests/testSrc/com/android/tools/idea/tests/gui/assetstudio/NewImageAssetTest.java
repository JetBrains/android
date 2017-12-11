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

import com.android.testutils.filesystemdiff.Action;
import com.android.testutils.filesystemdiff.CreateDirectoryAction;
import com.android.testutils.filesystemdiff.CreateFileAction;
import com.android.testutils.filesystemdiff.FileSystemEntry;
import com.android.testutils.filesystemdiff.Script;
import com.android.testutils.filesystemdiff.TreeBuilder;
import com.android.testutils.filesystemdiff.TreeDifferenceEngine;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.assetstudio.AssetStudioWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.assetstudio.NewImageAssetStepFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class NewImageAssetTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testAdaptiveIconsPreviewPanelContents() throws Exception {
    AssetStudioWizardFixture wizard = guiTest.importSimpleApplication()
      .getProjectView()
      .selectAndroidPane()
      .clickPath("app")
      .openFromMenu(AssetStudioWizardFixture::find, "File", "New", "Image Asset");

    NewImageAssetStepFixture step = wizard.getImageAssetStep();
    step.selectIconType("Launcher Icons (Adaptive and Legacy)");
    assertThat(step.getPreviewPanelCount()).isEqualTo(1);
    assertThat(step.getPreviewPanelIconNames(0)).containsExactly(
      "Circle", "Squircle", "Rounded Square", "Square", "Full Bleed Layers", "Legacy Icon", "Round Icon", "Google Play Store")
      .inOrder();
    wizard.clickCancel();
  }

  @Test
  public void testNotificationImageCount() throws Exception {
    AssetStudioWizardFixture wizard = guiTest.importSimpleApplication()
      .getProjectView()
      .selectAndroidPane()
      .clickPath("app")
      .openFromMenu(AssetStudioWizardFixture::find, "File", "New", "Image Asset");

    NewImageAssetStepFixture step = wizard.getImageAssetStep();
    Path projectDir = guiTest.getProjectPath().toPath();
    FileSystemEntry original = TreeBuilder.buildFromFileSystem(projectDir);

    step.selectIconType("Notification Icons");
    assertThat(step.getPreviewPanelCount()).isEqualTo(1);
    assertThat(step.getPreviewPanelIconNames(0)).containsExactly("xxhdpi", "xhdpi", "hdpi", "mdpi").inOrder();
    wizard.clickNext();
    wizard.clickFinish();

    FileSystemEntry changed = TreeBuilder.buildFromFileSystem(projectDir);

    List<String> newFiles = getNewFiles(projectDir, TreeDifferenceEngine.computeEditScript(original, changed));
    assertThat(newFiles).containsExactly("app/src/main/res/drawable-mdpi/ic_stat_name.png",
                                         "app/src/main/res/drawable-hdpi/ic_stat_name.png",
                                         "app/src/main/res/drawable-xhdpi/ic_stat_name.png",
                                         "app/src/main/res/drawable-xxhdpi/ic_stat_name.png");
  }

  @Test
  public void testNotificationImageCountForOldApi() throws Exception {
    AssetStudioWizardFixture wizard = guiTest.importSimpleApplication()
      .getProjectView()
      .selectAndroidPane()
      .clickPath("app")
      .getEditor()
      .open("app/build.gradle")
      .select("minSdkVersion (19)")
      .enterText("4")
      .awaitNotification("Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
      .performAction("Sync Now")
      .waitForGradleProjectSyncToFinish()
      .getEditor()
      .moveBetween("minSdkVersion ", "4")
      .getIdeFrame()
      .openFromMenu(AssetStudioWizardFixture::find, "File", "New", "Image Asset");

    Path projectDir = guiTest.getProjectPath().toPath();
    FileSystemEntry original = TreeBuilder.buildFromFileSystem(projectDir);

    wizard.getImageAssetStep()
      .selectIconType("Notification Icons");
    wizard.clickNext()
      .clickFinish();

    FileSystemEntry changed = TreeBuilder.buildFromFileSystem(projectDir);

    List<String> newFiles = getNewFiles(projectDir, TreeDifferenceEngine.computeEditScript(original, changed));
    assertThat(newFiles).containsExactly("app/src/main/res/drawable-mdpi/ic_stat_name.png",
                                         "app/src/main/res/drawable-hdpi/ic_stat_name.png",
                                         "app/src/main/res/drawable-xhdpi/ic_stat_name.png",
                                         "app/src/main/res/drawable-xxhdpi/ic_stat_name.png",

                                         "app/src/main/res/drawable-mdpi-v9/ic_stat_name.png",
                                         "app/src/main/res/drawable-hdpi-v9/ic_stat_name.png",
                                         "app/src/main/res/drawable-xhdpi-v9/ic_stat_name.png",
                                         "app/src/main/res/drawable-xxhdpi-v9/ic_stat_name.png",

                                         "app/src/main/res/drawable-mdpi-v11/ic_stat_name.png",
                                         "app/src/main/res/drawable-hdpi-v11/ic_stat_name.png",
                                         "app/src/main/res/drawable-xhdpi-v11/ic_stat_name.png",
                                         "app/src/main/res/drawable-xxhdpi-v11/ic_stat_name.png"
      );
  }

  @NotNull
  public static List<String> getNewFiles(@NotNull Path root, @NotNull Script script) {
    List<String> newFiles = new ArrayList<>();
    List<Action> actions = script.getActions();
    for (Action action : actions) {
      if (action instanceof CreateFileAction) {
        newFiles.add(toString(root, action.getSourceEntry().getPath()));
      }
      if (action instanceof CreateDirectoryAction) {
        try {
          Files.walk(action.getSourceEntry().getPath())
            .filter(Files::isRegularFile)
            .forEach(path -> newFiles.add(toString(root, path)));
        }
        catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      }
    }
    return newFiles;
  }

  @NotNull
  private static String toString(@NotNull Path root, @NotNull Path path) {
    return root.relativize(path).toString().replace('\\', '/');
  }
}
