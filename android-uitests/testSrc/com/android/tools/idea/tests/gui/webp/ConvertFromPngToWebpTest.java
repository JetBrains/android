/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.webp.WebpConversionDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.webp.WebpPreviewDialogFixture;
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
public class ConvertFromPngToWebpTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  /**
   * Verifies the conversion of images from PNG to WebP.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 716115fc-fb6e-43ec-abd4-4cc46b46a2f8
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
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void testConvertFromPngToWebp() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("ImportLocalWebpProject")
           .getProjectView()
           .selectAndroidPane()
           .clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "mipmap", "ic_test.png")
           .invokeMenuPath("Convert to WebP...");

    WebpConversionDialogFixture.findDialog(guiTest.robot())
                               .clickOk();

    WebpPreviewDialogFixture.findDialog(guiTest.robot())
                            .clickFinish();

    VirtualFile webpIcon = guiTest.ideFrame().getProject().getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test.webp");
    assertThat(webpIcon).isNotNull();
    VirtualFile pngIcon = guiTest.ideFrame().getProject().getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test.png");
    assertThat(pngIcon).isNull();
  }
}
