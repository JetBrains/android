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
package com.android.tools.idea.tests.gui.framework.fixture.layout;

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.Wait;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ToolWindowFixture;
import com.android.tools.idea.uibuilder.editor.NlPreviewForm;
import com.android.tools.idea.uibuilder.editor.NlPreviewManager;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.assertNotNull;

/**
 * Fixture for the layout editor preview window
 */
public class NlPreviewFixture extends ToolWindowFixture {
  private final Project myProject;
  private final JPanel myProgressPanel;

  protected NlPreviewFixture(@NotNull Project project, @NotNull Robot robot) {
    super("Preview", project, robot);
    myProject = project;
    myProgressPanel = robot.finder().findByName(getContent().getContentPanel(), "Layout Editor Progress Panel", JPanel.class, false);
  }

  @Nullable
  public static NlPreviewFixture getNlPreview(@NotNull final EditorFixture editor, @NotNull final IdeFrameFixture frame,
                                              boolean switchToTabIfNecessary) {
    VirtualFile currentFile = editor.getCurrentFile();
    if (ResourceHelper.getFolderType(currentFile) != ResourceFolderType.LAYOUT) {
      return null;
    }

    if (switchToTabIfNecessary) {
      editor.selectEditorTab(EditorFixture.Tab.EDITOR);
    }

    Boolean visible = execute(new GuiQuery<Boolean>() {
      @Override
      protected Boolean executeInEDT() throws Throwable {
        NlPreviewManager manager = NlPreviewManager.getInstance(frame.getProject());
        NlPreviewForm toolWindowForm = manager.getPreviewForm();
        return toolWindowForm != null && toolWindowForm.getSurface().isShowing();
      }
    });
    if (visible == null || !visible) {
      frame.invokeMenuPath("View", "Tool Windows", "Preview");
    }

    Wait.minutes(2).expecting("Preview window to be visible")
      .until(() -> {
        NlPreviewManager manager = NlPreviewManager.getInstance(frame.getProject());
        NlPreviewForm toolWindowForm = manager.getPreviewForm();
        return toolWindowForm != null && toolWindowForm.getSurface().isShowing();
      });

    return new NlPreviewFixture(frame.getProject(), frame.robot());
  }

  @NotNull
  public NlConfigurationToolbarFixture getToolbar() {
    ActionToolbar toolbar = waitUntilShowing(myRobot, getContent().getContentPanel(), GuiTests.matcherForType(ActionToolbarImpl.class));
    return new NlConfigurationToolbarFixture(myRobot, getContent().getSurface(), toolbar);
  }

  @NotNull
  private NlPreviewForm getContent() {
    activate();
    NlPreviewForm form = getManager().getPreviewForm();
    assertNotNull(form); // because we opened the window with activate() above
    return form;
  }

  @NotNull
  private NlPreviewManager getManager() {
    return NlPreviewManager.getInstance(myProject);
  }

  public void waitForRenderToFinish() {
    Wait.minutes(2).expecting("render to finish").until(() -> !myProgressPanel.isVisible());
    DesignSurface surface = getContent().getSurface();
    Wait.minutes(2).expecting("render to finish").until(() -> {
      ScreenView screenView = surface.getCurrentScreenView();
      return screenView != null && screenView.getResult() != null;
    });
  }
}
