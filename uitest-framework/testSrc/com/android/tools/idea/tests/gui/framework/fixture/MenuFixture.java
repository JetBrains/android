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
package com.android.tools.idea.tests.gui.framework.fixture;

import static com.android.tools.idea.tests.gui.framework.UiTestUtilsKt.fixupWaiting;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.util.containers.ContainerUtil;
import java.awt.Container;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collection;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.timing.Pause;
import org.jetbrains.annotations.NotNull;

class MenuFixture {
  @NotNull private final Robot myRobot;
  @NotNull private final IdeFrameImpl myContainer;

  MenuFixture(@NotNull Robot robot, @NotNull IdeFrameImpl container) {
    myRobot = robot;
    myContainer = container;
  }

  /**
   * Invokes an action by menu path
   *
   * @param path the series of menu names, e.g. {@link invokeActionByMenuPath("Build", "Make Project ")}
   */
  void invokeMenuPath(int timeToWaitAtStepMs, @NotNull String... path) {
    doInvokeMenuPath(path, false, timeToWaitAtStepMs);
  }

  /**
   * Invokes an action by menu path in a contextual menu
   *
   * @param path the series of menu names, e.g. {@link invokeActionByMenuPath("Build", "Make Project ")}
   */
  void invokeContextualMenuPath(@NotNull String... path) {
    doInvokeMenuPath(path, true, 10);
  }

  private void doInvokeMenuPath(@NotNull String[] path, boolean isContextual, int wait) {
    System.out.printf("Attempting to open menu path: %s%n", String.join(" / ", path));
    assertThat(path).isNotEmpty();
    int segmentCount = path.length;

    // If waiting requested we can use a reliable robot. Otherwise we might be called in an unsafe context when the IdeEventQueue has
    // already been shut down.
    Robot robot = (wait > 0) ? fixupWaiting(myRobot) : myRobot;

    long started = System.currentTimeMillis();

    int initialPopups = isContextual ? 1 : robot.finder().findAll(Matchers.byType(JPopupMenu.class).andIsShowing()).size();
    int popupsToClose = 0;
    // Repeat attempts to open all nested menus until we either succeed or the timeout expires.
    tryAgain:
    while (true) {
      // Close any previously opened popups.
      while (
        robot.finder().findAll(Matchers.byType(JPopupMenu.class).andIsShowing()).size() > initialPopups && popupsToClose > 0) {
        System.out.println("Attempting to close a previously opened menu");
        robot.pressAndReleaseKey(KeyEvent.VK_ESCAPE);
        popupsToClose--;
      }
      popupsToClose = 0;

      Collection<JPopupMenu> initiallyVisible = robot.finder().findAll(Matchers.byType(JPopupMenu.class).andIsShowing());
      if (initialPopups != initiallyVisible.size()) {
        if (initialPopups == initiallyVisible.size() + 1) {
          Pause.pause();
        continue;
        } else {
          fail(
            String.format("Cannot open menu %s. Incorrect number of initially visible popups: %d (Expected: %d)",
                          String.join(" / ", path), initiallyVisible.size(), initialPopups));
        }
      }

      // Check whether the timeout has expired after we closed all previously opened popups.
      if (System.currentTimeMillis() - started > wait * 3000L) {
        throw new WaitTimedOutError("Timed out while attempting to open: " + String.join(" / ", path));
      }

      Container root = isContextual ? robot.finder().findByType(JPopupMenu.class) : myContainer;

      // Attempt to open nested menus and click the item. Do no wait for menu items to become enabled since they are not updated while
      // popups are visible.
      for (int i = 0; i < segmentCount; i++) {
        String segment = path[i];
        System.out.printf("Opening segment: %s%n", segment);
        Collection<JMenuItem> menuItems = robot.finder().findAll(root, Matchers.byText(JMenuItem.class, segment).and(
          new GenericTypeMatcher<>(JMenuItem.class) {
            @Override
            protected boolean isMatching(@NotNull JMenuItem component) {
              return component.getHeight() > 0;
            }
          }));
        if (menuItems.isEmpty()) {
          System.out.println("No menu items found");
          continue tryAgain;
        }
        JMenuItem menuItem = ContainerUtil.getOnlyItem(menuItems);
        GuiTask.execute(() -> menuItem.requestFocus());
        robot.waitForIdle();
        if (!GuiQuery.get(() -> menuItem.isEnabled())) continue tryAgain;
        robot.moveMouse(menuItem);
        robot.click(menuItem);
        popupsToClose++;
        if (GuiQuery.getNonNull(() -> menuItem.isShowing() && !menuItem.isEnabled())) {
          System.out.printf("Menu item %s got disabled while clicking. Trying again...%n", String.join(" / ", Arrays.copyOf(path, i + 1)));
          continue tryAgain;
        }
        if (i < segmentCount - 1) {
          int expectedCount = i + 1 + initialPopups;

          Collection<JPopupMenu> popups;
          int counter = 100; // Wait approx 1 second for a popup to appear on hover/click.
          do {
            popups = robot.finder().findAll(Matchers.byType(JPopupMenu.class).andIsShowing());
            if (popups.size() == expectedCount) break;
            Pause.pause();
          }
          while (counter-- > 0);

          if (expectedCount != popups.size()) {
            System.out.printf("Trying again because expectedCount: %d != popups.size: %d.%n", expectedCount, popups.size());
            continue tryAgain;
          }
        }
      }
      break;
    }
    robot.waitForIdle();
  }
}
