/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.android.tools.idea.tests.gui.framework.fixture.webp.WebpConversionDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.webp.WebpPreviewDialogFixture;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.fest.swing.core.MouseButton;
import org.fest.swing.timing.Wait;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class ConvertFromAndToWebpTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);

  @Before
  public void setUp() throws Exception {
    guiTest.withTimeout(7, TimeUnit.MINUTES);
    guiTest.importProjectAndWaitForProjectSyncToFinish("ImportLocalWebpProject");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }

  /**
   * Verifies the conversion of images from WebP to PNG.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 716115fc-fb6e-43ec-abd4-4cc46b46a2f8
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import 'ImportLocalWebpProject' project
   *   2. Go to app/src/main/res/mipmap-xhdpi folder
   *   3. Right click on ic_test2.webp and select "Convert to png..."
   *   4. Select 'OK' (Verify 1)
   *   5. Go to drawable folder (Verify 2)
   *   Verify:
   *   1. Webp file is not present
   *   2. Png file is present
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void testConvertFromWebPToPng() throws IOException {
    guiTest.ideFrame().getProjectView()
      .selectAndroidPane()
      .expand(30)
      .clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "mipmap", "ic_test2.webp")
      .invokeContextualMenuPath("Convert to PNG\u2026");

    MessagesFixture.findByTitle(guiTest.robot(), "Convert from WebP to PNG")
      .clickYes();

    assertThat(guiTest.ideFrame().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test2.webp")).isNull();
    assertThat(guiTest.ideFrame().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test2.png")).isNotNull();

  }

  /**
   * Verifies the conversion of images from WebP to PNG.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: f1cbf728-3640-407b-a686-57c43e413e95
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import 'ImportLocalWebpProject' project
   *   2. Go to app/src/main/res/mipmap-xhdpi folder (Verify 1, Verify 2)
   *   3. Right click on ic_test.png and select "Convert to WebP..."
   *   4. Select Lossless encoding
   *   5. Select 'OK' (Verify 2)
   *   6. Go to drawable folder (Verify 3)
   *   Verify:
   *   1. Webp file is not present
   *   2. Png file is present
   *   3. Webp file is present and not null
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void testConvertToWebpUsingLosslessEncoding() throws IOException {
    assertThat(guiTest.ideFrame().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test.webp")).isNull();
    assertThat(guiTest.ideFrame().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test.png").exists()).isTrue();

    guiTest.ideFrame()
      .getProjectView()
      .selectAndroidPane()
      .expand(30)
      .clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "mipmap", "ic_test.png")
      .invokeContextualMenuPath("Convert to WebP\u2026");

    WebpConversionDialogFixture.findDialog(guiTest.robot())
      .selectLossless()
      .clickOk();

    guiTest.waitForBackgroundTasks();
    guiTest.robot().waitForIdle();
    // Check that the webp icon now exists
    guiTest.ideFrame().getProjectView().assertFilesExist(
      "/app/src/main/res/mipmap-xhdpi/ic_test.webp"
    );

    // ..and that the .png icon doesn't
    assertThat(guiTest.ideFrame().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test.png")).isNull();
  }

  /**
   * Verifies the conversion of images from WebP to PNG.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: f1cbf728-3640-407b-a686-57c43e413e95
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import 'ImportLocalWebpProject' project
   *   2. Go to app/src/main/res/mipmap-xhdpi folder (Verify 1, Verify 2)
   *   3. Right click on ic_test.png and select "Convert to WebP..."
   *   4. Select Lossy encoding
   *   5. Select 'OK' (Verify 2)
   *   6. Go to drawable folder (Verify 3)
   *   Verify:
   *   1. Webp file is not present
   *   2. Png file is present
   *   3. Webp file is present and not null
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void testConvertToWebPUsingLossyEncodingAndPreviews() throws IOException {
    guiTest.ideFrame()
      .getProjectView()
      .selectAndroidPane()
      .expand(30)
      .clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "mipmap", "ic_test.png")
      .invokeContextualMenuPath("Convert to WebP\u2026");

    // Settings dialog
    WebpConversionDialogFixture.findDialog(guiTest.robot())
      .selectLossy()
      .clickOk();

    WebpPreviewDialogFixture.findDialog(guiTest.robot())
      .clickFinish();

    guiTest.waitForBackgroundTasks();
    guiTest.robot().waitForIdle();

    assertThat(guiTest.ideFrame().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test.png")).isNull();
    assertThat(guiTest.ideFrame().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test.webp")).isNotNull();
  }
}
