/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.idea.configurations.ConfigurationToolBar;
import com.android.tools.idea.tests.gui.framework.fixture.ToolWindowFixture;
import com.intellij.openapi.project.Project;
import org.fest.swing.core.Robot;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowForm;
import org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.tests.gui.framework.GuiTests.SHORT_TIMEOUT;
import static org.junit.Assert.assertNotNull;

/**
 * Fixture wrapping the the layout preview window
 */
public class LayoutPreviewFixture extends ToolWindowFixture implements LayoutFixture {
  private final Project myProject;

  public LayoutPreviewFixture(@NotNull Robot robot, @NotNull Project project) {
    super("Preview", project, robot);
    myProject = project;
  }

  @Override
  @NotNull
  public RenderErrorPanelFixture getRenderErrors() {
    return new RenderErrorPanelFixture(myRobot, this, getContent());
  }

  @Override
  @NotNull
  public ConfigurationToolbarFixture getToolbar() {
    AndroidLayoutPreviewToolWindowForm form = getContent();
    ConfigurationToolBar toolbar = myRobot.finder().findByType(form.getContentPanel(), ConfigurationToolBar.class, true);
    assertNotNull(toolbar);
    return new ConfigurationToolbarFixture(myRobot, this, form, toolbar);
  }

  @NotNull
  private AndroidLayoutPreviewToolWindowForm getContent() {
    activate();
    AndroidLayoutPreviewToolWindowForm form = getManager().getToolWindowForm();
    assertNotNull(form); // because we opened the window with activate() above
    return form;
  }

  @NotNull
  private AndroidLayoutPreviewToolWindowManager getManager() {
    return AndroidLayoutPreviewToolWindowManager.getInstance(myProject);
  }

  @NotNull
  @Override
  public Object waitForRenderToFinish() {
    return waitForNextRenderToFinish(null);
  }

  @NotNull
  @Override
  public Object waitForNextRenderToFinish(@Nullable final Object previous) {
    myRobot.waitForIdle();

    Pause.pause(new Condition("Render is pending") {
      @Override
      public boolean test() {
        AndroidLayoutPreviewToolWindowManager manager = getManager();
        return !manager.isRenderPending() && manager.getToolWindowForm() != null && manager.getToolWindowForm().getLastResult() != null
          && manager.getToolWindowForm().getLastResult() != previous;
      }
    }, SHORT_TIMEOUT);

    myRobot.waitForIdle();

    Object token = getManager().getToolWindowForm().getLastResult();
    assert token != null;
    return token;
  }

  @Override
  public void requireRenderSuccessful() {
    waitForRenderToFinish();
    requireRenderSuccessful(false, false);
  }

  @Override
  public void requireRenderSuccessful(boolean allowErrors, boolean allowWarnings) {
    getRenderErrors().requireRenderSuccessful(allowErrors, allowWarnings);
  }
}
