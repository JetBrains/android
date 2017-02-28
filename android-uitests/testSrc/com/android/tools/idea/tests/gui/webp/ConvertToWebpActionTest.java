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

import com.android.tools.idea.rendering.webp.WebpNativeLibHelper;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.android.tools.idea.tests.gui.framework.fixture.webp.WebpConversionDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.webp.WebpPreviewDialogFixture;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.swing.core.MouseButton;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class ConvertToWebpActionTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testConvertLossless() throws IOException {
    if (!WebpNativeLibHelper.loadNativeLibraryIfNeeded()) {
      System.out.println("Skipping " + ConvertToWebpActionTest.class.getSimpleName() + " because the webp decoder is not available");
      return;
    }

    Project project = guiTest.importProjectAndWaitForProjectSyncToFinish("ImportWebpProject")
      .getProject();

    VirtualFile webpIcon = project.getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test.webp");
    assertThat(webpIcon).isNull();

    VirtualFile pngIcon = project.getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test.png");
    assertThat(pngIcon.exists()).isTrue();

    guiTest.ideFrame()
      .getProjectView()
      .selectAndroidPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "mipmap", "ic_test.png");
    guiTest.ideFrame().invokeMenuPath("Convert to WebP...");

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
    if (!WebpNativeLibHelper.loadNativeLibraryIfNeeded()) {
      System.out.println("Skipping " + ConvertToWebpActionTest.class.getSimpleName() + " because the webp decoder is not available");
      return;
    }

    Project project = guiTest.importProjectAndWaitForProjectSyncToFinish("ImportWebpProject")
      .getProject();

    guiTest.ideFrame()
      .getProjectView()
      .selectAndroidPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "mipmap", "ic_test.png");
    guiTest.ideFrame().invokeMenuPath("Convert to WebP...");

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

  /**
   * Verifies the conversion of images from PNG to WebP.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14603481
   * <p>
   *   <pre>
   *   Test Steps:
   *   1.Create Project with Minimum SDK 18 or above.
   *   2.Place attached webp(or any webp)(attachment in TestRail) image to drawable folder
   *   3.Right click on sample.webp image and select "Convert to PNG"
   *   4.Select 'Yes' option button in alert dialog box (Verify 1)
   *   Verify:
   *   1.Webp image should convert into png and previous WebP image is deleted.
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void testConvertFromPngToWebp() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("ImportWebpProject")
      .getProjectView()
      .selectAndroidPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "mipmap", "ic_test.png");
    guiTest.ideFrame().invokeMenuPath("Convert to WebP...");

    WebpConversionDialogFixture.findDialog(guiTest.robot())
      .clickOk();

    WebpPreviewDialogFixture.findDialog(guiTest.robot())
      .clickFinish();

    VirtualFile webpIcon = guiTest.ideFrame().getProject().getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test.webp");
    assertThat(webpIcon).isNotNull();
    VirtualFile pngIcon = guiTest.ideFrame().getProject().getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test.png");
    assertThat(pngIcon).isNull();
  }

  /**
   * Verifies the conversion of images from WebP to PNG.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14603482
   * <p>
   *   <pre>
   *   Test Steps:
   *   1.Create Project with Minimum SDK 18 or above.
   *   2.Place attached png(or any PNG) to drawable folder
   *   3.Right click on sample.png and select "Convert to WebP..." (Verify 1)
   *   4.Select 'OK' (Verify 2)
   *   5.Go to drawable folder (Verify 3)
   *   Verify:
   *   1.'Converting Images to WebP' alert dialog is open.(You can edit some options).
   *   Note:
   *   1.1 If your Application minSdkVersion 18 or above you have check box option "Skip images with transparency/alpha channel".
   *   1.2 If your Application minSdkVersion < 18, you have no check box option 'Skip images with transparency/alpha channel'.
   *   2.'Preview and Adjust Converted Images' Window is open and you can adjust Quality of image and select Finish button.
   *   3.PNG image should convert into webp image
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void testConvertFromWebPToPng() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("ImportWebpProject")
      .getProjectView()
      .selectAndroidPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "mipmap", "ic_test2.webp");
    guiTest.ideFrame().invokeMenuPath("Convert to PNG...");

    MessagesFixture.findByTitle(guiTest.robot(), "Convert from WebP to PNG")
      .clickYes();

    VirtualFile webpIcon = guiTest.ideFrame().getProject().getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test2.webp");
    assertThat(webpIcon).isNull();
    VirtualFile pngIcon = guiTest.ideFrame().getProject().getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test2.png");
    assertThat(pngIcon).isNotNull();
  }
}
