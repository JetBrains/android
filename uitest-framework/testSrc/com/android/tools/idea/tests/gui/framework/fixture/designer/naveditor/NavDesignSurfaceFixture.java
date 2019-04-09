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

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowingAndEnabled;
import static com.google.common.base.Verify.verifyNotNull;

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.naveditor.editor.AddDestinationMenu;
import com.android.tools.idea.naveditor.scene.targets.EmptyDesignerTarget;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.DesignSurfaceFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.util.ui.UIUtil;
import java.awt.Point;
import java.util.List;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  /**
   * Does a single click in the center of the empty designer target.
   */
  public void clickOnEmptyDesignerTarget() {
    @SwingCoordinate Point point = UIUtil.invokeAndWaitIfNeeded(() -> {
      List<Target> targets = target().getScene().getRoot().getTargets();
      Target emptyTarget = null;

      if (!targets.isEmpty()) {
        emptyTarget = target().getScene().getRoot().getTargets().get(0);
      }

      if (!(emptyTarget instanceof EmptyDesignerTarget)) {
        throw new IllegalStateException("Empty designer target not found");
      }

      SceneView view = target().getCurrentSceneView();
      @SwingCoordinate int x = Coordinates.getSwingXDip(view, (int)emptyTarget.getCenterX());
      @SwingCoordinate int y = Coordinates.getSwingYDip(view, (int)emptyTarget.getCenterY());

      return new Point(x, y);
    });

    robot().click(target(), point, MouseButton.LEFT_BUTTON, 1);
  }

  @Nullable
  public AddDestinationMenuFixture openAddDestinationMenu() {
    return new AddDestinationMenuFixture(robot(), openMenu(AddDestinationMenu.class));
  }

  @Nullable
  public AddDestinationMenuFixture getAddDestinationMenu() {
    return new AddDestinationMenuFixture(robot(), getMenu(AddDestinationMenu.class));
  }

  @Nullable
  private <T extends AnAction> T openMenu(Class<T> menuType) {
    ActionButton button = getActionButton(menuType);
    new ActionButtonFixture(robot(), button).click();
    return (T)button.getAction();
  }

  @Nullable
  private <T extends AnAction> T getMenu(Class<T> menuType) {
    ActionButton button = getActionButton(menuType);
    return (T)button.getAction();
  }

  @Nullable
  private <T extends AnAction> ActionButton getActionButton(Class<T> menuType) {
    waitForRenderToFinish();
    ActionToolbarImpl toolbar = robot().finder().findByName(target().getParent(), "NlLayoutToolbar", ActionToolbarImpl.class);
    ActionButton button =
      waitUntilShowingAndEnabled(robot(), toolbar.getComponent(), new GenericTypeMatcher<ActionButton>(ActionButton.class) {
        @Override
        protected boolean isMatching(@NotNull ActionButton component) {
          return menuType.isInstance(component.getAction());
        }
      });

    return button;
  }
}
