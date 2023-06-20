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

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.tests.gui.framework.fixture.designer.DesignSurfaceFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider;
import java.util.ArrayList;
import org.fest.swing.core.Robot;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static org.fest.swing.timing.Pause.pause;
import static org.junit.Assert.assertTrue;

public class NlDesignSurfaceFixture extends DesignSurfaceFixture<NlDesignSurfaceFixture, NlDesignSurface> {

  public NlDesignSurfaceFixture(@NotNull Robot robot, @NotNull NlDesignSurface designSurface) {
    super(NlDesignSurfaceFixture.class, robot, designSurface);
  }

  public boolean isShowing() {
    return target().isShowing();
  }

  public boolean hasRenderResult() {
    LayoutlibSceneManager sceneManager = target().getSceneManager();
    return sceneManager != null && sceneManager.getRenderResult() != null;
  }

  public boolean isRendering() {
    LayoutlibSceneManager sceneManager = target().getSceneManager();
    return sceneManager != null && sceneManager.isRendering();
  }

  @Override
  public void waitForRenderToFinish(@NotNull Wait wait) {
    super.waitForRenderToFinish(wait);

    wait.expecting("surface is showing and rendered").until(() -> isShowing() && !isRendering() && hasRenderResult());
    // Wait for the animation to finish
    pause(SceneComponent.ANIMATION_DURATION);
  }

  /**
   * Searches for the nth occurrence of a given view in the layout. The ordering of widgets of the same
   * type is by visual order, first vertically, then horizontally (and finally by XML source offset, if they exactly overlap
   * as for example would happen in a {@code <merge>}
   *
   * @param tag        the view tag to search for, e.g. "Button" or "TextView"
   * @param occurrence the index of the occurrence of the tag, e.g. 0 for the first TextView in the layout
   */
  @NotNull
  public NlComponentFixture findView(@NotNull final String tag, int occurrence) {
    waitForRenderToFinish();

    final NlModel model = target().getModel();
    final List<NlComponent> components = new ArrayList<>();

    model.getComponents().forEach(component -> addComponents(tag, component, components));
    // Sort by visual order
    components.sort((component1, component2) -> {
      int delta = NlComponentHelperKt.getY(component1) - NlComponentHelperKt.getY(component2);
      if (delta != -1) {
        return delta;
      }
      delta = NlComponentHelperKt.getX(component1) - NlComponentHelperKt.getX(component2);
      if (delta != -1) {
        return delta;
      }
      // Unlikely
      return component1.getTagDeprecated().getTextOffset() - component2.getTagDeprecated().getTextOffset();
    });

    assertTrue("Only " + components.size() + " found, not enough for occurrence #" + occurrence, components.size() > occurrence);

    NlComponent component = components.get(occurrence);
    return createComponentFixture(component);
  }

  private static void addComponents(@NotNull String tag, @NotNull NlComponent component, @NotNull List<NlComponent> components) {
    if (tag.equals(component.getTagName())) {
      components.add(component);
    }

    for (NlComponent child : component.getChildren()) {
      addComponents(tag, child, components);
    }
  }

  public boolean isInScreenMode(@NotNull NlScreenViewProvider sceneModesProvider) {
    return target().getScreenViewProvider() == sceneModesProvider;
  }

  public void waitUntilNotShowing(@NotNull Wait wait) {
    wait
      .expecting("surface to hide")
      .until(() -> !isShowing());
  }
}
