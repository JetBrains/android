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

import com.android.tools.idea.rendering.webp.ConvertToWebpAction;
import com.android.tools.idea.rendering.webp.WebpNativeLibHelper;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.google.common.collect.Maps;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.*;
import java.io.IOException;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.timing.Pause.pause;

@RunWith(GuiTestRunner.class)
public class ConvertToWebpActionTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testConvertLossless() throws IOException {
    if (!WebpNativeLibHelper.loadNativeLibraryIfNeeded()) {
      System.out.println("Skipping " + ConvertToWebpActionTest.class.getSimpleName() + " because the webp decoder is not available");
      return;
    }

    guiTest.importProjectAndWaitForProjectSyncToFinish("ImportWebpProject");
    IdeFrameFixture projectFrame = guiTest.ideFrame();

    Project project = projectFrame.getProject();
    VirtualFile res = project.getBaseDir().findFileByRelativePath("app/src/main/res");
    VirtualFile webpIcon = project.getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test.webp");
    assertThat(webpIcon).isNull();

    VirtualFile pngIcon = project.getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test.png");
    assertThat(pngIcon.exists()).isTrue();
    invokeConvertToWebpAction(project, res);

    WebpConversionDialogFixture dialog = WebpConversionDialogFixture.findDialog(guiTest.robot());
    dialog.selectLossless();
    dialog.clickOk();

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

    guiTest.importProjectAndWaitForProjectSyncToFinish("ImportWebpProject");
    IdeFrameFixture projectFrame = guiTest.ideFrame();

    Project project = projectFrame.getProject();
    VirtualFile res = project.getBaseDir().findFileByRelativePath("app/src/main/res");

    VirtualFile pngIcon = project.getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test.png");
    assertThat(pngIcon.exists()).isTrue();
    invokeConvertToWebpAction(project, res);

    // Settings dialog
    WebpConversionDialogFixture settingsDialog = WebpConversionDialogFixture.findDialog(guiTest.robot());
    settingsDialog.selectLossy();
    settingsDialog.clickOk();

    WebpPreviewDialogFixture previewDialog = WebpPreviewDialogFixture.findDialog(guiTest.robot());
    previewDialog.clickFinish();

    VirtualFile webpIcon = project.getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test.webp");
    assertThat(webpIcon).isNotNull();
    pngIcon = project.getBaseDir().findFileByRelativePath("app/src/main/res/mipmap-xhdpi/ic_test.png");
    assertThat(pngIcon).isNull();
  }

  private static void invokeConvertToWebpAction(@NotNull Project project, @NotNull VirtualFile res) {
    // Ideally I'd invoke the context menu on the res node, "Convert to WebP Format",
    // but the pane fixture doesn't support invoking context menu actions yet.
    // Shortcut:
    //ProjectViewFixture.PaneFixture pane = projectFrame.getProjectView().selectAndroidPane();
    //pane.selectByPath("app", "res");

    Map<String,Object> data = Maps.newHashMap();
    data.put(CommonDataKeys.VIRTUAL_FILE_ARRAY.getName(), new VirtualFile[] {res});
    data.put(CommonDataKeys.PROJECT.getName(), project);
    DataContext context = SimpleDataContext.getSimpleContext(data, null);
    AnActionEvent actionEvent = new AnActionEvent(null, context, "test", new Presentation(), ActionManager.getInstance(), 0);
    ConvertToWebpAction action = new ConvertToWebpAction();
    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> action.actionPerformed(actionEvent));
      }
    });
  }
}
