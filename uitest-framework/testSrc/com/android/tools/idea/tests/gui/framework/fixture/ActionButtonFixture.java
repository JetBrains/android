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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import java.awt.Container;
import java.lang.reflect.Field;
import javax.swing.Icon;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ActionButtonFixture extends JComponentFixture<ActionButtonFixture, ActionButton> {
  @NotNull
  public static ActionButtonFixture locateByActionId(@NotNull final String actionId,
                                                     @NotNull final Robot robot,
                                                     @NotNull final Container container,
                                                     long secondsToWait) {
    // Sometimes we need to locate the button without it being enabled, because there is an issue with the UI where it needs
    // the mouse to be over it and moving before it will refresh itself.
    ActionButton button = GuiTests.waitUntilShowing(robot, container, new GenericTypeMatcher<>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton component) {
        String id = ActionManager.getInstance().getId(component.getAction());
        return actionId.equals(id);
      }
    }, secondsToWait);
    return new ActionButtonFixture(robot, button);
  }

  @NotNull
  public static ActionButtonFixture findByActionId(@NotNull final String actionId,
                                                   @NotNull final Robot robot,
                                                   @NotNull final Container container,
                                                   long secondsToWait) {
    ActionButton button = GuiTests.waitUntilShowingAndEnabled(robot, container, new GenericTypeMatcher<>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton component) {
        String id = ActionManager.getInstance().getId(component.getAction());
        return actionId.equals(id);
      }
    }, secondsToWait);
    return new ActionButtonFixture(robot, button);
  }

  @NotNull
  public ActionButtonFixture waitUntilEnabledAndShowing() {
    // move mouse over the target button due to Intellij bug that can cause buttons state to not be updated correctly
    // see: http://b.android.com/231853
    robot().moveMouse(target());
    Wait.seconds(1).expecting("action to be enabled and showing").until(() -> GuiQuery.getNonNull(
      () -> target().getAction().getTemplatePresentation().isEnabledAndVisible()
            && target().isShowing() && target().isVisible() && target().isEnabled()));
    return this;
  }

  @NotNull
  public static ActionButtonFixture findByActionClass(
    @NotNull Class<? extends AnAction> actionClass,
    @NotNull Robot robot,
    @NotNull Container container
  ) {
    return findByMatcher(new GenericTypeMatcher<>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton component) {
        return actionClass.equals(component.getAction().getClass());
      }
    }, robot, container);
  }

  @NotNull
  public static ActionButtonFixture findByActionInstance(
    @NotNull AnAction actionInstance,
    @NotNull Robot robot,
    @NotNull Container container
  ) {
    return findByMatcher(new GenericTypeMatcher<>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton component) {
        return actionInstance == component.getAction();
      }
    }, robot, container);
  }

  @NotNull
  public static ActionButtonFixture findByText(@NotNull final String text, @NotNull Robot robot, @NotNull Container container) {
    return findByMatcher(Matchers.byText(ActionButton.class, text), robot, container);
  }

  @NotNull
  static ActionButtonFixture findByMatcher(@NotNull GenericTypeMatcher<ActionButton> matcher,
                                           @NotNull Robot robot,
                                           @NotNull Container container) {
    return new ActionButtonFixture(robot, robot.finder().find(container, matcher));
  }

  @NotNull
  public static ActionButtonFixture findByIcon(@NotNull final Icon icon, @NotNull Robot robot, @Nullable Container container) {
    ActionButton button = GuiTests.waitUntilShowing(robot, container, new GenericTypeMatcher<>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton component) {
        try {
          Field field = ActionButton.class.getDeclaredField("myIcon");
          field.setAccessible(true);
          Icon fieldIcon = (Icon)field.get(component);
          return icon.equals(fieldIcon);
        }
        catch (NoSuchFieldException | IllegalAccessException e) {
          return false;
        }
      }
    });
    return new ActionButtonFixture(robot, button);
  }

  @NotNull
  public static ActionButtonFixture findByIcon(@NotNull final Icon icon, @NotNull Robot robot) {
    return findByIcon(icon, robot, null);
  }

  public ActionButtonFixture(@NotNull Robot robot, @NotNull ActionButton target) {
    super(ActionButtonFixture.class, robot, target);
  }
}
