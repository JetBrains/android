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

import com.android.tools.adtui.webp.WebpNativeLibHelper;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture.PaneFixture;
import com.android.tools.idea.tests.gui.framework.fixture.assetstudio.FileChooserDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.webp.WebpConversionDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.webp.WebpPreviewDialogFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ComponentWithMnemonics;
import org.fest.reflect.exception.ReflectionError;
import org.fest.reflect.reference.TypeRef;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.MouseButton;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.*;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Collection;

import static com.google.common.truth.Truth.assertThat;
import static org.fest.reflect.core.Reflection.field;
import static org.junit.Assert.fail;

@RunWith(GuiTestRunner.class)
public class ConvertToWebpActionTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testConvertLossless() throws IOException {
    if (!WebpNativeLibHelper.loadNativeLibraryIfNeeded()) {
      System.out.println("Skipping " + ConvertToWebpActionTest.class.getSimpleName() + " because the webp decoder is not available");
      return;
    }

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
    if (!WebpNativeLibHelper.loadNativeLibraryIfNeeded()) {
      System.out.println("Skipping " + ConvertToWebpActionTest.class.getSimpleName() + " because the webp decoder is not available");
      return;
    }

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
  @RunIn(TestGroup.SANITY)
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
  @RunIn(TestGroup.SANITY)
  @Test
  public void testConvertFromWebPToPng() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("ImportLocalWebpProject")
      .getProjectView()
      .selectAndroidPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "mipmap", "ic_test2.webp")
      .invokeMenuPath("Convert to PNG...");

    MessagesFixture.findByTitle(guiTest.robot(), "Convert from WebP to PNG")
      .clickYes();

    VirtualFile webpIcon = guiTest.ideFrame().getProject().getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test2.webp");
    assertThat(webpIcon).isNull();
    VirtualFile pngIcon = guiTest.ideFrame().getProject().getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test2.png");
    assertThat(pngIcon).isNotNull();
  }

  /**
   * Verify that .9.png and transparent images are not converted to .WebP.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: d48504cb-f03a-4e05-9c68-7c39db65bc8e
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import ConvertFrom9Patch project with minsdk 15.
   *   2. Right click on any .png file and select Create 9-patch file... option. (verify 1)
   *   3. Right click on res folder and select Convert to WebP.. (verify 2) (verify 3)
   *   4. Click OK on the popped up window. (verify 4)
   *   Verify:
   *   1. Verify that corresponding nine-patch file is created.
   *   2. Verify that Ã¢â‚¬Å“Skip nine-patch (.9.png) imagesÃ¢â‚¬ï¿¾ option is checked and disabled.
   *   3. Since minSdkVersion is less than 18, the flag to "skip images with transparency"
   *      is selected by default.
   *   4. Verify that no images are converted .WebP and observe a notification at the bottom right
   *      corner with the message similar to the one below. 0 files were converted 1 9-patch files
   *      were skipped 10 transparent images were skipped
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void testCannotConvertFrom9PatchAndTransparentImagesToWebp() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("ConvertFrom9Patch");

    PaneFixture androidPane = ideFrame.getProjectView().selectAndroidPane();

    androidPane.clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "mipmap", "ic_launcher.png")
      .invokeMenuPath("Create 9-Patch file...");

    FileChooserDialogFixture.find(ideFrame.robot())
      .clickOk();

    GenericTypeMatcher<JDialog> matcher = new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        if (Matchers.byType(JDialog.class).matches(dialog)) {
          DialogWrapper wrapper = getDialogWrapperFrom(dialog, FileChooserDialogImpl.class);
          if (wrapper != null) {
            return true;
          }
        }
        return false;
      }
    };

    Wait.seconds(20).expecting("Wait for Save As .9.png Dialog disappear. ").until(() -> {
      try {
        guiTest.robot().finder().find(ideFrame.target(), matcher);
        return false;
      } catch (ComponentLookupException e) {
        return true;
      }
    });

    // Check nine-patch file is created by clicking on it.
    // Synchronize files before clicking on new generated file.
    androidPane.clickPath(MouseButton.RIGHT_BUTTON, "app")
      .invokeMenuPath("Synchronize 'app'");
    androidPane.clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "mipmap", "ic_launcher.9.png");

    // Try to convert to webp and verify.
    int countOfBalloonBeforeConvert = getNotificationBalloonCount(ideFrame);

    androidPane.clickPath(MouseButton.RIGHT_BUTTON, "app", "res")
      .invokeMenuPath("Convert to WebP...");
    WebpConversionDialogFixture webpConversionDialog = WebpConversionDialogFixture.findDialog(guiTest.robot());
    JCheckBox skip9PatchCheckBox = webpConversionDialog.getCheckBox("Skip nine-patch (.9.png) images");
    assertThat(skip9PatchCheckBox.isEnabled()).isFalse();
    JCheckBox skipImagesWithtransparencyCheckboc = webpConversionDialog.getCheckBox("Skip images with transparency/alpha channel");
    assertThat(skipImagesWithtransparencyCheckboc.isSelected()).isTrue();
    webpConversionDialog.clickOk();

    // Since the message like "0 files were converted" in the notification balloon cannot be retrieved,
    // (NotificationsManagerImpl$5:model:Data:array), here we can only check there is only one
    // more BalloomImpl:MyComponent instance after converting.
    int countOfBalloonAfterConvert = getNotificationBalloonCount(ideFrame);
    assertThat(countOfBalloonAfterConvert - countOfBalloonBeforeConvert).isEqualTo(1);
  }

  private int getNotificationBalloonCount(@NotNull IdeFrameFixture ideFrame) {
    Collection<JPanel> allFound = ideFrame.robot().finder()
      .findAll(ideFrame.target(), Matchers.byType(JPanel.class));

    int count = 0;
    for (JPanel jPanel : allFound) {
      try {
        if (jPanel instanceof ComponentWithMnemonics) {
          count++;
        }
      }
      catch (ComponentLookupException e) {
        continue;
      }
    }

    return count;
  }

  // Copied from IdeaDialogFixture.getDialogWrapperFrom(), which is protected method.
  @Nullable
  private <T extends DialogWrapper> T getDialogWrapperFrom(@NotNull JDialog dialog,
                                                           Class<T> dialogWrapperType) {
    try {
      DialogWrapper wrapper = field("myDialogWrapper")
        .ofType(new TypeRef<WeakReference<DialogWrapper>>() {}).in(dialog).get().get();
      if (dialogWrapperType.isInstance(wrapper)) {
        return dialogWrapperType.cast(wrapper);
      }
    } catch (ReflectionError ignored) {
    }
    return null;
  }
}
