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

import static com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.assetstudio.FileChooserDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.webp.WebpConversionDialogFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import com.intellij.ui.ComponentWithMnemonics;
import java.io.File;
import org.fest.reflect.exception.ReflectionError;
import org.fest.reflect.reference.TypeRef;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.MouseButton;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.exception.LocationUnavailableException;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.*;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static org.fest.reflect.core.Reflection.field;

@RunWith(GuiTestRemoteRunner.class)
public class ConvertFrom9PatchTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);
  private IdeFrameFixture ideFrame;

  @Before
  public void setUp() throws Exception {

    File projectDir = guiTest.setUpProject("ConvertFrom9Patch", null, ANDROID_GRADLE_PLUGIN_VERSION, null, null);
    guiTest.openProjectAndWaitForProjectSyncToFinish(projectDir, Wait.seconds(540));
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    ideFrame = guiTest.ideFrame();
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
   *   2. Verify that "Skip nine-patch (.9.png) images" option is checked and disabled.
   *   3. Since minSdkVersion is less than 18, the flag to "skip images with transparency/alpha channel"
   *      is selected by default.
   *   4. Verify that no images are converted .WebP and observe a notification at the bottom right
   *      corner with the message similar to the one below. 0 files were converted 1 9-patch files
   *      were skipped 10 transparent images were skipped
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void testCannotConvertFrom9PatchAndTransparentImagesToWebp() throws Exception {

    ProjectViewFixture.PaneFixture androidPane = ideFrame.getProjectView().selectAndroidPane();

    androidPane.clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "mipmap", "ic_launcher.png")
      .invokeContextualMenuPath("Create 9-Patch file…");

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
    // Reload from Disk before clicking on new generated file.
    androidPane.clickPath(MouseButton.RIGHT_BUTTON, "app")
      .invokeMenuPath("Reload from Disk");
    androidPane.clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "mipmap", "ic_launcher.9.png");

    // Try to convert to webp and verify.
    androidPane.clickPath(MouseButton.RIGHT_BUTTON, "app", "res")
      .invokeContextualMenuPath("Convert to WebP...");
    WebpConversionDialogFixture webpConversionDialog = WebpConversionDialogFixture.findDialog(guiTest.robot());
    JCheckBox skip9PatchCheckBox = webpConversionDialog.getCheckBox("Skip nine-patch (.9.png) images");
    assertThat(skip9PatchCheckBox.isEnabled()).isFalse();
    JCheckBox skipImagesWithtransparencyCheckboc = webpConversionDialog.getCheckBox("Skip images with transparency/alpha channel");
    assertThat(skipImagesWithtransparencyCheckboc.isSelected()).isTrue();
    webpConversionDialog.clickOk();

    // Since the message like "0 files were converted" in the notification balloon cannot be retrieved,
    // (NotificationsManagerImpl$5:model:Data:array), here we can only check there is at least one
    // BalloonImpl:MyComponent instance after converting.
    int countOfBalloonAfterConvert = getNotificationBalloonCount(ideFrame);
    assertThat(countOfBalloonAfterConvert).isGreaterThan(1);

    // Double check there is no .webp file generated.
    try {
      androidPane.clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "mipmap", "ic_launcher.webp");
      Assert.fail(".webp file shouldn't exist");
    } catch (LocationUnavailableException e) {
      // LocationUnavailableException is expected, because there is no .webp file converted.
    }
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
