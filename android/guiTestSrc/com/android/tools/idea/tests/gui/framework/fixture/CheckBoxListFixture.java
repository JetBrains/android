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

import com.intellij.ui.CheckBoxList;
import org.fest.swing.annotation.RunsInEDT;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.BasicJListCellReader;
import org.fest.swing.driver.CellRendererReader;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.JListFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Fixture for {@link com.intellij.ui.CheckBoxList}s.
 */
public class CheckBoxListFixture<T> extends JListFixture {
  @NotNull private final CheckBoxList<T> myTarget;

  public CheckBoxListFixture(@NotNull Robot robot, @NotNull CheckBoxList<T> target) {
    super(robot, target);
    myTarget = target;
    cellReader(new BasicJListCellReader(new CellRendererReader() {
      @Override
      @Nullable
      public String valueFrom(Component c) {
        if (c instanceof JCheckBox) {
          return ((JCheckBox)c).getText();
        }
        return null;
      }
    }));
  }

  @RunsInEDT
  @NotNull
  public String[] getCheckedItems() {
    return GuiActionRunner.execute(new GuiQuery<String[]>() {
      @Override
      protected String[] executeInEDT() throws Throwable {
        ListModel model = myTarget.getModel();
        int elementCount = model.getSize();
        String[] values = new String[elementCount];
        for (int i = 0; i < elementCount; i++) {
          if (myTarget.isItemSelected(i)) {
            values[i] = valueAt(i);
          }
        }
        return values;
      }
    });
  }

  @RunsInEDT
  @NotNull
  public CheckBoxListFixture setItemChecked(@NotNull final String text, final boolean checked) {
    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        ListModel model = myTarget.getModel();
        int elementCount = model.getSize();
        for (int i = 0; i < elementCount; i++) {
          if (text.equals(valueAt(i))) {
            //noinspection ConstantConditions
            myTarget.setItemSelected(myTarget.getItemAt(i), checked);
          }
        }
      }
    });
    return this;
  }
}
