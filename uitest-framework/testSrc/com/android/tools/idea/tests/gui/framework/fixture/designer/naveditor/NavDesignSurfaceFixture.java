/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor;

import com.android.tools.idea.naveditor.editor.AddExistingDestinationMenu;
import com.android.tools.idea.naveditor.editor.CreateDestinationMenu;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.DesignSurfaceFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.SceneView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing;
import static com.google.common.base.Verify.verifyNotNull;

public class NavDesignSurfaceFixture extends DesignSurfaceFixture<NavDesignSurfaceFixture, NavDesignSurface> {
  public NavDesignSurfaceFixture(@NotNull Robot robot,
                                 @NotNull NavDesignSurface designSurface) {
    super(NavDesignSurfaceFixture.class, robot, designSurface);
  }

  /**
   * Searches for the given destination in the nav graph.
   *
   * @param id the destination id
   */
  @NotNull
  public NlComponentFixture findDestination(@NotNull final String id) {
    waitForRenderToFinish();

    SceneView view = target().getCurrentSceneView();

    final NlModel model = view.getModel();

    NlComponent component = verifyNotNull(model.find(id));

    return createComponentFixture(component);
  }

  @Nullable
  private <T extends AnAction> T openMenu(Class<T> menuType) {
    waitForRenderToFinish();
    ActionToolbarImpl toolbar = robot().finder().findByName(target().getParent(), "NlLayoutToolbar", ActionToolbarImpl.class);
    ActionButton button = waitUntilShowing(robot(), toolbar.getComponent(), new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton component) {
        return menuType.isInstance(component.getAction());
      }
    });
    new ActionButtonFixture(robot(), button).click();
    return (T)button.getAction();
  }

  @Nullable
  public CreateDestinationMenuFixture openNewDestinationMenu() {
    return new CreateDestinationMenuFixture(robot(), openMenu(CreateDestinationMenu.class));
  }

  @Nullable
  public AddExistingDestinationMenuFixture openAddExistingMenu() {
    return new AddExistingDestinationMenuFixture(robot(), openMenu(AddExistingDestinationMenu.class));
  }
}
