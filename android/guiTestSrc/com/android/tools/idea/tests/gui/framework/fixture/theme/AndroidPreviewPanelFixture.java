/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.theme;

import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.swing.layoutlib.AndroidPreviewPanel;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import java.awt.Dimension;
import java.awt.Point;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

/**
 * Fixture wrapping the {@link AndroidPreviewPanel}
 */
public class AndroidPreviewPanelFixture extends ComponentFixture<AndroidPreviewPanelFixture, AndroidPreviewPanel> {
  public AndroidPreviewPanelFixture(@NotNull Robot robot,
                                    @NotNull AndroidPreviewPanel target) {
    super(AndroidPreviewPanelFixture.class, robot, target);
  }

  /**
   * Requires that the panel has actually rendered something successfully once
   */
  @NotNull
  public AndroidPreviewPanelFixture requireRendered() {
    Dimension renderSize = target().getPreferredScrollableViewportSize();
    assertTrue(0 != renderSize.getHeight());
    assertTrue(0 != renderSize.getWidth());
    assertNotNull(target().findViewAtPoint(new Point(10, 10)));
    return this;
  }
}
