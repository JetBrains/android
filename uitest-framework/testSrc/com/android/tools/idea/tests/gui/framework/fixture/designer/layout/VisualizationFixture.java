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
package com.android.tools.idea.tests.gui.framework.fixture.designer.layout;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.ToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.visual.VisualizationForm;
import com.android.tools.idea.uibuilder.visual.VisualizationManager;
import com.intellij.openapi.project.Project;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

/**
 * Fixture for the Visualization tool window
 */
public class VisualizationFixture extends ToolWindowFixture {
  private final NlDesignSurfaceFixture myDesignSurfaceFixture;

  public VisualizationFixture(@NotNull Project project, @NotNull Robot robot) {
    super(VisualizationManager.TOOL_WINDOW_ID, project, robot);
    myDesignSurfaceFixture = new NlDesignSurfaceFixture(
      robot, GuiTests.waitUntilShowing(robot, null, Matchers.byName(NlDesignSurface.class, VisualizationForm.VISUALIZATION_DESIGN_SURFACE), 20));
  }

  public VisualizationFixture waitForRenderToFinish() {
    myDesignSurfaceFixture.waitForRenderToFinish();
    return this;
  }

  @NotNull
  public String getCurrentFileName() {
    return myDesignSurfaceFixture.target().getModel().getVirtualFile().getName();
  }
}
