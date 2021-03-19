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
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.core.MouseButton;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class ConvertFromWebpToPngTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  /**
   * Verifies the conversion of images from WebP to PNG.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: f1cbf728-3640-407b-a686-57c43e413e95
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
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void testConvertFromWebPToPng() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("ImportLocalWebpProject", Wait.seconds(120))
           .getProjectView()
           .selectAndroidPane()
           .expand(30)
           .clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "mipmap", "ic_test2.webp")
           .invokeMenuPath("Convert to PNG...");

    MessagesFixture.findByTitle(guiTest.robot(), "Convert from WebP to PNG")
                   .clickYes();

    VirtualFile webpIcon = guiTest.ideFrame().getProject().getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test2.webp");
    assertThat(webpIcon).isNull();
    VirtualFile pngIcon = guiTest.ideFrame().getProject().getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test2.png");
    assertThat(pngIcon).isNotNull();
  }
}
