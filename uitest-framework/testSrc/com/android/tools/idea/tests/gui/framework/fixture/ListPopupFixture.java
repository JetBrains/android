/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.list.ListPopupModel;
import javax.swing.JList;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class ListPopupFixture {

  /**
   * Get JBlist from ListPopup.
   * After IDEA 2024.2, Resource Manager overflow tabs, Select 'app' for deployment has PopupList
   */
  public static JBList getList(IdeFrameFixture ideFrameFixture) {
    return GuiTests.waitUntilShowingAndEnabled(ideFrameFixture.robot(), ideFrameFixture.target(), new GenericTypeMatcher<JBList>(JBList.class) {
      @Override
      protected boolean isMatching(@NotNull JBList list) {
        return list.getClass().getName().equals("com.intellij.ui.popup.list.ListPopupImpl$MyList");
      }
    });
  }

  /**
   * Select item from the ListPopup using provided string
   */
  public static void selectItemByText(@NotNull IdeFrameFixture ideFrameFixture, @NotNull String text) {
    JList list = getList(ideFrameFixture);
    Wait.seconds(1).expecting("the list to be populated")
      .until(() -> {
        ListPopupModel popupModel = (ListPopupModel)list.getModel();
        for (int i = 0; i < popupModel.getSize(); ++i) {
          PopupFactoryImpl.ActionItem actionItem = (PopupFactoryImpl.ActionItem)popupModel.get(i);
          if (text.equals(actionItem.getText())) {
            return true;
          }
        }
        return false;
      });

    int appIndex = GuiQuery.getNonNull(
      () -> {
        ListPopupModel popupModel = (ListPopupModel)list.getModel();
        for (int i = 0; i < popupModel.getSize(); ++i) {
          PopupFactoryImpl.ActionItem actionItem = (PopupFactoryImpl.ActionItem)popupModel.get(i);
          if (text.equals(actionItem.getText())) {
            return i;
          }
        }
        return -1;
      });
    assertThat(appIndex).isAtLeast(0);

    GuiTask.execute(() -> list.setSelectedIndex(appIndex));
    assertEquals(text, ((PopupFactoryImpl.ActionItem)list.getSelectedValue()).getText());
  }

}
