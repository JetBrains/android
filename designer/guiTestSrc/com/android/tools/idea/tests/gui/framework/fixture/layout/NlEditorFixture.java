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

import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.uibuilder.editor.NlEditor;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

/**
 * Fixture wrapping the the layout editor for a particular file
 */
public class NlEditorFixture extends ComponentFixture<NlEditorFixture, Component> {
  private final DesignSurfaceFixture myDesignSurfaceFixture;

  public NlEditorFixture(@NotNull Robot robot, @NotNull NlEditor editor) {
    super(NlEditorFixture.class, robot, editor.getComponent());
    myDesignSurfaceFixture = new DesignSurfaceFixture(robot, editor.getComponent().getSurface());
  }

  public void waitForRenderToFinish() {
    myDesignSurfaceFixture.waitForRenderToFinish();
  }

  @NotNull
  public NlComponentFixture findView(@NotNull String tag, int occurrence) {
    return myDesignSurfaceFixture.findView(tag, occurrence);
  }

  public void requireSelection(@NotNull List<NlComponentFixture> components) {
    myDesignSurfaceFixture.requireSelection(components);
  }

  public boolean hasRenderErrors() {
    return myDesignSurfaceFixture.hasRenderErrors();
  }

  public boolean errorPanelContains(@NotNull String errorText) {
    return myDesignSurfaceFixture.errorPanelContains(errorText);
  }
}
