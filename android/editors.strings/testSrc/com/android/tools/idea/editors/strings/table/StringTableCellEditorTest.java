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
package com.android.tools.idea.editors.strings.table;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.adtui.swing.laf.HeadlessTableUI;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.ui.JBColor;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class StringTableCellEditorTest {
  @Rule
  public ApplicationRule myRule = new ApplicationRule();

  private FrozenColumnTable<DefaultTableModel> myFrozenColumnTable = null;
  private StringTableCellEditor myEditor = null;

  @Before
  public void setUp() {
    DefaultTableModel model = getModel();
    myFrozenColumnTable = new FrozenColumnTable<>(model, 4);
    myFrozenColumnTable.createDefaultColumnsFromModel();
    myFrozenColumnTable.getFrozenTable().setUI(new HeadlessTableUI());
    myFrozenColumnTable.getScrollableTable().setUI(new HeadlessTableUI());
    myEditor = new StringTableCellEditor();
    myFrozenColumnTable.setDefaultEditor(Object.class, myEditor);
  }

  private static @NotNull DefaultTableModel getModel() {
    Object[][] data = new Object[][] {
      new Object[]{"east", "app/src/main/res", false, "east", "est", "øst"},
      new Object[]{"west", "app/src/main/res", false, "west", "ouest", "vest"},
      new Object[]{"north", "app/src/main/res", false, "north", "nord", "nord"},
      new Object[]{"south", "app/src/main/res", false, "south", "sud", "syd"}
    };
    Object[] columns = new Object[]{"Key", "Resource Folder", "Untranslatable", "Default Value", "French (fr)", "Danish (da)"};
    return new DefaultTableModel(data, columns);
  }

  @Test
  public void stopCellEditing() {
    myFrozenColumnTable.getFrozenTable().editCellAt(1, 3);
    myEditor.setCellEditorValue("Hello World");
    assertTrue(myEditor.stopCellEditing());
    LineBorder border = (LineBorder)myEditor.getComponent().getBorder();
    assertThat(border.getLineColor()).isEqualTo(JBColor.BLACK);
    assertThat(border.getThickness()).isEqualTo(1);
    assertThat(myEditor.getComponent().getToolTipText()).isNull();
  }

  @Test
  public void stopCellEditingWithInvalidValue() {
    myFrozenColumnTable.getScrollableTable().editCellAt(1, 0);
    myEditor.setCellEditorValue("https://oslobysykkel.no/_app/options/terms?locale=%1$s&product_id=%2$s");
    assertFalse(myEditor.stopCellEditing());
    LineBorder border = (LineBorder)myEditor.getComponent().getBorder();
    assertThat(border.getLineColor()).isEqualTo(JBColor.RED);
    assertThat(border.getThickness()).isEqualTo(1);
    assertThat(myEditor.getComponent().getToolTipText()).isEqualTo("Invalid value");
  }

  @Test
  public void stopCellEditingWithInvalidKeyValue() {
    myFrozenColumnTable.getFrozenTable().editCellAt(1, 0);
    myEditor.setCellEditorValue("a b c");
    assertFalse(myEditor.stopCellEditing());
    LineBorder border = (LineBorder)myEditor.getComponent().getBorder();
    assertThat(border.getLineColor()).isEqualTo(JBColor.RED);
    assertThat(border.getThickness()).isEqualTo(1);
    assertThat(myEditor.getComponent().getToolTipText()).isEqualTo("' ' is not a valid resource name character");
  }
}
