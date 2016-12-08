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

import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.errors.ui.RenderErrorPanel;
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.Lists;
import org.fest.swing.core.Robot;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class DesignSurfaceFixture extends ComponentFixture<DesignSurfaceFixture, DesignSurface> {
  private final JPanel myProgressPanel;
  private final RenderErrorPanel myRenderErrorPanel;
  private final IdeFrameFixture myIdeFrame;

  public DesignSurfaceFixture(@NotNull Robot robot, @NotNull IdeFrameFixture frame, @NotNull DesignSurface designSurface) {
    super(DesignSurfaceFixture.class, robot, designSurface);
    myIdeFrame = frame;
    myProgressPanel = robot.finder().findByName(target(), "Layout Editor Progress Panel", JPanel.class, false);
    myRenderErrorPanel = robot.finder().findByName(target(), "Layout Editor Error Panel", RenderErrorPanel.class, false);
  }

  public void waitForRenderToFinish() {
    Wait.seconds(5).expecting("render to finish").until(() -> !myProgressPanel.isVisible());
    Wait.seconds(5).expecting("render to finish").until(() -> {
      ScreenView screenView = target().getCurrentScreenView();
      if (screenView == null) {
        return false;
      }
      RenderResult result = screenView.getResult();
      if (result == null) {
        return false;
      }
      if (result.getLogger().hasErrors()) {
        return target().isShowing() && myRenderErrorPanel.isShowing();
      }
      return target().isShowing() && !myRenderErrorPanel.isShowing();
    });
  }

  public boolean hasRenderErrors() {
    return myRenderErrorPanel.isShowing();
  }

  public void waitForErrorPanelToContain(@NotNull String errorText) {
    Wait.seconds(1).expecting("the error panel to contain: " + errorText).until(() -> {
      Document doc = myRenderErrorPanel.getHtmlDetailPane().getDocument();
      try {
        return doc.getText(0, doc.getLength()).contains(errorText);
      }
      catch (BadLocationException e) {
        return false;
      }
    });
  }

  @Nullable
  public String getErrorText() {
    Document doc = myRenderErrorPanel.getHtmlDetailPane().getDocument();
    try {
      return doc.getText(0, doc.getLength());
    }
    catch (BadLocationException e) {
      return null;
    }
  }

  /**
   * Searches for the nth occurrence of a given view in the layout. The ordering of widgets of the same
   * type is by visual order, first vertically, then horizontally (and finally by XML source offset, if they exactly overlap
   * as for example would happen in a {@code <merge>}
   *
   * @param tag the view tag to search for, e.g. "Button" or "TextView"
   * @param occurrence the index of the occurrence of the tag, e.g. 0 for the first TextView in the layout
   */
  @NotNull
  public NlComponentFixture findView(@NotNull final String tag, int occurrence) {
    waitForRenderToFinish();
    ScreenView screenView = target().getCurrentScreenView();
    assertNotNull(screenView);
    final NlModel model = screenView.getModel();
    final java.util.List<NlComponent> components = Lists.newArrayList();

    model.getComponents().forEach(component -> addComponents(tag, component, components));
    // Sort by visual order
    components.sort((component1, component2) -> {
      int delta = component1.y - component2.y;
      if (delta != -1) {
        return delta;
      }
      delta = component1.x - component2.x;
      if (delta != -1) {
        return delta;
      }
      // Unlikely
      return component1.getTag().getTextOffset() - component2.getTag().getTextOffset();
    });

    assertTrue("Only " + components.size() + " found, not enough for occurrence #" + occurrence, components.size() > occurrence);

    NlComponent component = components.get(occurrence);
    return createComponentFixture(component);
  }

  @NotNull
  private NlComponentFixture createComponentFixture(@NotNull NlComponent component) {
    return new NlComponentFixture(robot(), myIdeFrame, component, target());
  }

  private static void addComponents(@NotNull String tag, @NotNull NlComponent component, @NotNull List<NlComponent> components) {
    if (tag.equals(component.getTagName())) {
      components.add(component);
    }

    for (NlComponent child : component.getChildren()) {
      addComponents(tag, child, components);
    }
  }

  /** Returns the views and all the children */
  @NotNull
  public List<NlComponentFixture> getAllComponents() {
    ScreenView screenView = target().getCurrentScreenView();
    if (screenView == null) {
      return Collections.emptyList();
    }

    return screenView.getModel().flattenComponents()
      .map(this::createComponentFixture)
      .collect(Collectors.toList());
  }

  /** Requires the selection to have the given number of selected widgets */
  @NotNull
  public DesignSurfaceFixture requireSelection(@NotNull List<NlComponentFixture> components) {
    assertEquals(components, getSelection());
    return this;
  }

  /** Returns a list of the selected views */
  @NotNull
  public List<NlComponentFixture> getSelection() {
    ScreenView screenView = target().getCurrentScreenView();
    if (screenView == null) {
      return Collections.emptyList();
    }

    return screenView.getModel().getSelectionModel().getSelection().stream()
      .map(this::createComponentFixture)
      .collect(Collectors.toList());
  }

  public boolean isInScreenMode(@NotNull DesignSurface.ScreenMode mode) {
    return target().getScreenMode() == mode;
  }
}
