/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.webp;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.webp.WebpConversionDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.webp.WebpPreviewDialogFixture;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.core.MouseButton;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class ConvertToWebpActionTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  @Test
  public void testConvertLossless() throws IOException {
    Project project = guiTest.importProjectAndWaitForProjectSyncToFinish("ImportLocalWebpProject")
      .getProject();

    VirtualFile webpIcon = project.getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test.webp");
    assertThat(webpIcon).isNull();

    VirtualFile pngIcon = project.getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test.png");
    assertThat(pngIcon.exists()).isTrue();

    guiTest.ideFrame()
      .getProjectView()
      .selectAndroidPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "mipmap", "ic_test.png")
      .invokeMenuPath("Convert to WebP...");

    WebpConversionDialogFixture.findDialog(guiTest.robot())
      .selectLossless()
      .clickOk();

    // Check that the webp icon now exists
    webpIcon = project.getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test.webp");
    assertThat(webpIcon).isNotNull();
    assertThat(pngIcon.exists()).isFalse();
    // ..and that the .png icon doesn't
    pngIcon = project.getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test.png");
    assertThat(pngIcon).isNull();
  }

  @Test
  public void testConvertLossyWithPreviews() throws IOException {
    Project project = guiTest.importProjectAndWaitForProjectSyncToFinish("ImportLocalWebpProject")
      .getProject();

    guiTest.ideFrame()
      .getProjectView()
      .selectAndroidPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "mipmap", "ic_test.png")
      .invokeMenuPath("Convert to WebP...");

    // Settings dialog
    WebpConversionDialogFixture.findDialog(guiTest.robot())
      .selectLossy()
      .clickOk();

    WebpPreviewDialogFixture.findDialog(guiTest.robot())
      .clickFinish();

    VirtualFile webpIcon = project.getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test.webp");
    assertThat(webpIcon).isNotNull();
    VirtualFile pngIcon = project.getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test.png");
    assertThat(pngIcon).isNull();
  }
}
