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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.intellij.ui.JBColor;
import java.awt.event.ActionEvent;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public final class StringTableCellEditorTest {
  private JTextField myComponent;

  @Before
  public void mockComponent() {
    myComponent = Mockito.mock(JTextField.class);
  }

  @Test
  public void stopCellEditing() {
    Mockito.when(myComponent.getText()).thenReturn("https://oslobysykkel.no/_app/options/terms?locale=%1$s&product_id=%2$s");

    assertFalse(new StringTableCellEditor(myComponent).stopCellEditing());

    Mockito.verify(myComponent).setBorder(Mockito.<LineBorder>argThat(
      actual -> JBColor.RED.equals(actual.getLineColor()) && 1 == actual.getThickness() && !actual.getRoundedCorners()));
  }

  @Test
  public void isCellEditable() {
    assertTrue(new StringTableCellEditor(myComponent).isCellEditable(new ActionEvent(Mockito.mock(JTable.class), 0, null)));
  }
}
