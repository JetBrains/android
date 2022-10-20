/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing;
import static com.google.common.base.Joiner.on;
import static org.junit.Assert.fail;

import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.list.ListPopupModel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.KeyPressInfo;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JPopupMenuFixture;
import org.jetbrains.annotations.NotNull;

public class PopupMenuFixture {

  static IdeFrameFixture myIdeFrame;

  public static void clickPopupMenuItem(IdeFrameFixture ideFrameFixture, String labelToClick) {
    myIdeFrame = ideFrameFixture;
    Component component = ideFrameFixture.target();
    Robot robot = ideFrameFixture.robot();
    clickPopupMenuItemMatching(s -> s.startsWith(labelToClick), component, robot);
  }

  public static void clickPopupMenuItemMatching (@NotNull Predicate<String> predicate, @NotNull Component component, @NotNull Robot robot) {
    JPopupMenu menu = GuiQuery.get(robot::findActivePopupMenu);
    if (menu != null) {
      new JPopupMenuFixture(robot, menu).menuItem(new GenericTypeMatcher<JMenuItem>(JMenuItem.class) {
        @Override
        protected boolean isMatching(@NotNull JMenuItem component) {
          return predicate.test(component.getText());
        }
      }).click();
      robot.click(myIdeFrame.target(), new Point(100, 100));
      return;
    }

    // IntelliJ doesn't seem to use a normal JPopupMenu, so this won't work:
    //    JPopupMenu menu = myRobot.findActivePopupMenu();
    // Instead, it uses a JList (technically a JBList), which is placed somewhere
    // under the root pane.

    Container root = GuiQuery.getNonNull(() -> (Container)SwingUtilities.getRoot(component));
    // First find the JBList which holds the popup. There could be other JBLists in the hierarchy,
    // so limit it to one that is actually used as a popup, as identified by its model being a ListPopupModel:
    JBList list = waitUntilShowing(robot, root, new GenericTypeMatcher<JBList>(JBList.class) {
      @Override
      protected boolean isMatching(@NotNull JBList list) {
        ListModel model = list.getModel();
        return model instanceof ListPopupModel;
      }
    });

    // We can't use the normal JListFixture method to click by label since the ListModel items are
    // ActionItems whose toString does not reflect the text, so search through the model items instead:
    ListPopupModel model = (ListPopupModel)list.getModel();
    List<String> items = new ArrayList<>();
    for (int i = 0; i < model.getSize(); i++) {
      Object elementAt = model.getElementAt(i);
      String s;
      if (elementAt instanceof PopupFactoryImpl.ActionItem) {
        s = ((PopupFactoryImpl.ActionItem)elementAt).getText();
      }
      else { // For example package private class IntentionActionWithTextCaching used in quickfix popups
        s = elementAt.toString();
      }

      if (predicate.test(s)) {
        // Some IJ menu items detect "mouse moves" to get the focus, but FEST clicks on a point without moving the mouse there first.
        new JListFixture(robot, list).drag(i).clickItem(i);
        return;
      }
      items.add(s);
    }

    if (items.isEmpty()) {
      fail("Could not find any menu items in popup");
    }
    fail("Did not find the correct menu item among " + on(", ").join(items));
  }
}
