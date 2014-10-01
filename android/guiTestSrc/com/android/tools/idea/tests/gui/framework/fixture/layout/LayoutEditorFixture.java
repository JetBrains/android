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
import com.google.common.collect.Lists;
import com.intellij.android.designer.AndroidDesignerEditor;
import com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.application.ApplicationManager;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ComponentFixture;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.android.tools.idea.tests.gui.framework.GuiTests.SHORT_TIMEOUT;
import static org.junit.Assert.*;

/**
 * Fixture wrapping the the layout editor for a particular file
 */
public class LayoutEditorFixture extends ComponentFixture<AndroidDesignerEditorPanel> implements LayoutFixture {
  private final AndroidDesignerEditorPanel myPanel;

  public LayoutEditorFixture(@NotNull Robot robot, @NotNull AndroidDesignerEditor editor) {
    super(robot, (AndroidDesignerEditorPanel)editor.getDesignerPanel());
    myPanel = (AndroidDesignerEditorPanel)editor.getDesignerPanel();
  }

  @Override
  @NotNull
  public RenderErrorPanelFixture getRenderErrors() {
    return new RenderErrorPanelFixture(robot, this, myPanel);
  }

  @Override
  @NotNull
  public ConfigurationToolbarFixture getToolbar() {
    AndroidDesignerEditorPanel panel = myPanel;
    ConfigurationToolBar toolbar = robot.finder().findByType(panel, ConfigurationToolBar.class, true);
    assertNotNull(toolbar);
    return new ConfigurationToolbarFixture(robot, this, panel, toolbar);
  }

  /** Returns the palette associated with this layout editor */
  @NotNull
  public LayoutPaletteFixture getPaletteFixture() {
    return new LayoutPaletteFixture(robot, this, myPanel);
  }

  /** Returns the property sheet associated with this layout editor */
  @NotNull
  public PropertySheetFixture getPropertySheetFixture() {
    return new PropertySheetFixture(robot, this, myPanel);
  }

  @NotNull
  @Override
  public Object waitForRenderToFinish() {
    return waitForNextRenderToFinish(null);
  }

  /** Rendering token used by {@link #waitForRenderToFinish()} */
  private Object myPreviousRender;

  @Override
  public void waitForNextRenderToFinish() {
    myPreviousRender = waitForNextRenderToFinish(myPreviousRender);
  }

  @NotNull
  @Override
  public Object waitForNextRenderToFinish(@Nullable final Object previous) {
    robot.waitForIdle();

    Pause.pause(new Condition("Render is pending") {
      @Override
      public boolean test() {
        return !myPanel.isRenderPending() && myPanel.getLastResult() != null && myPanel.getLastResult() != previous;
      }
    }, SHORT_TIMEOUT);

    robot.waitForIdle();

    Object token = myPanel.getLastResult();
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

  /**
   * Searches for the nth occurrence of a given view in the layout. The ordering of widgets of the same
   * type is by visual order, first vertically, then horizontally (and finally by XML source offset, if they exactly overlap
   * as for example would happen in a {@code <merge>}
   */
  @NotNull
  public LayoutEditorComponentFixture findView(final String tag, int occurrence) {
    waitForRenderToFinish();
    AndroidDesignerEditorPanel panel = myPanel;
    final List<RadViewComponent> components = Lists.newArrayList();
    final RadComponent rootComponent = panel.getRootComponent();
    assertNotNull(rootComponent);
    assertTrue(rootComponent.getClass().getName(), rootComponent instanceof RadViewComponent);
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        addComponents(tag, (RadViewComponent)rootComponent, components);
        // Sort by visual order
        Collections.sort(components, new Comparator<RadViewComponent>() {
          @Override
          public int compare(RadViewComponent component1, RadViewComponent component2) {
            Rectangle bounds1 = component1.getBounds();
            Rectangle bounds2 = component2.getBounds();
            int delta = bounds1.y - bounds2.y;
            if (delta != -1) {
              return delta;
            }
            delta = bounds1.x - bounds2.x;
            if (delta != -1) {
              return delta;
            }
            // Unlikely
            return component1.getTag().getTextOffset() - component2.getTag().getTextOffset();
          }
        });
      }
    });

    assertTrue("Only " + components.size() + " found, not enough for occurrence #" + occurrence, components.size() > occurrence);

    RadViewComponent component = components.get(occurrence);
    return createComponentFixture(component);
  }

  private LayoutEditorComponentFixture createComponentFixture(RadViewComponent component) {
    return new LayoutEditorComponentFixture(robot, component, this, myPanel);
  }

  /** Requires the selection to have the given number of selected widgets */
  public LayoutEditorFixture requireSelectionCount(int count) {
    assertEquals(count, getSelection().size());
    return this;
  }

  /** Requires the selection to have the given number of selected widgets */
  public LayoutEditorFixture requireSelection(List<LayoutEditorComponentFixture> components) {
    assertEquals(components, getSelection());
    return this;
  }

  /** Returns a list of the selected views */
  @NotNull
  public List<LayoutEditorComponentFixture> getSelection() {
    List<LayoutEditorComponentFixture> selection = Lists.newArrayList();
    for (RadComponent component : myPanel.getSurfaceArea().getSelection()) {
      if (component instanceof RadViewComponent) {
        selection.add(createComponentFixture((RadViewComponent)component));
      }
    }
    return selection;
  }

  private static void addComponents(@NotNull String tag, @NotNull RadViewComponent component, @NotNull List<RadViewComponent> components) {
    if (tag.equals(component.getTag().getName())) {
      components.add(component);
    }

    for (RadComponent child : component.getChildren()) {
      if (child instanceof RadViewComponent) {
        addComponents(tag, (RadViewComponent)child, components);
      }
    }
  }
}
