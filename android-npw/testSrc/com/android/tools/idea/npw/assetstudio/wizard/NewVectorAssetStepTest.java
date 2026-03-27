/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio.wizard;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.npw.assetstudio.wizard.NewVectorAssetStep.NumericDocumentFilter;
import com.intellij.testFramework.RunsInEdt;
import javax.swing.JTextField;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import org.junit.Test;

@RunsInEdt
public class NewVectorAssetStepTest {

  @Test
  public void widthTextFieldOnlyAcceptsIntegers() throws BadLocationException {
    JTextField textField = new JTextField();
    ((AbstractDocument) textField.getDocument()).setDocumentFilter(new NumericDocumentFilter());

    textField.getDocument().insertString(0, "abc12.3def", null);
    assertThat(textField.getText()).isEqualTo("123");
  }

  @Test
  public void heightTextFieldOnlyAcceptsIntegers() throws BadLocationException {
    JTextField textField = new JTextField();
    ((AbstractDocument) textField.getDocument()).setDocumentFilter(new NumericDocumentFilter());

    textField.getDocument().insertString(0, "45.6gh", null);
    assertThat(textField.getText()).isEqualTo("456");
  }

  @Test
  public void opacityTextFieldOnlyAcceptsIntegersUpTo100() throws BadLocationException {
    JTextField textField = new JTextField();
    ((AbstractDocument) textField.getDocument()).setDocumentFilter(new NumericDocumentFilter(100));

    textField.getDocument().insertString(0, "78.9", null);
    assertThat(textField.getText()).isEqualTo("78");

    textField.setText("");
    textField.getDocument().insertString(0, "150", null);
    // Opacity has a maximum value of 100, so trying to input a digit that will make the value greater than 100 will be rejected.
    assertThat(textField.getText()).isEqualTo("15");
  }

  @Test
  public void replaceNumericFieldWithValidAndInvalidText() throws BadLocationException {
    JTextField textField = new JTextField();
    ((AbstractDocument) textField.getDocument()).setDocumentFilter(new NumericDocumentFilter(100));

    textField.setText("50");
    // Replace "5" with "9", result "90" (valid)
    ((AbstractDocument) textField.getDocument()).replace(0, 1, "9", null);
    assertThat(textField.getText()).isEqualTo("90");

    // Replace "90" with "110" (invalid because > 100)
    // '1' replaces "90" -> "1" (valid)
    // '1' inserted at offset 1 -> "11" (valid)
    // '0' inserted at offset 2 -> "110" (invalid)
    // Result should be "11"
    textField.setText("90");
    ((AbstractDocument) textField.getDocument()).replace(0, 2, "110", null);
    assertThat(textField.getText()).isEqualTo("11");
  }

  @Test
  public void deleteFromNumericField() throws BadLocationException {
    JTextField textField = new JTextField();
    ((AbstractDocument) textField.getDocument()).setDocumentFilter(new NumericDocumentFilter());

    textField.setText("12345");
    // Remove "23", should result in "145"
    textField.getDocument().remove(1, 2);
    assertThat(textField.getText()).isEqualTo("145");

    // Remove everything
    textField.getDocument().remove(0, 3);
    assertThat(textField.getText()).isEqualTo("");
  }

  @Test
  public void replaceNumericFieldWithNonDigitText() throws BadLocationException {
    JTextField textField = new JTextField();
    ((AbstractDocument) textField.getDocument()).setDocumentFilter(new NumericDocumentFilter());

    textField.setText("123");
    // Replace "2" with "a4" -> "143"
    ((AbstractDocument) textField.getDocument()).replace(1, 1, "a4", null);
    assertThat(textField.getText()).isEqualTo("143");
  }

  @Test
  public void replaceWithOnlyInvalidCharactersPerformsDeletion() throws BadLocationException {
    JTextField textField = new JTextField();
    ((AbstractDocument) textField.getDocument()).setDocumentFilter(new NumericDocumentFilter());

    textField.setText("123");
    // Replace "2" with "a", should result in "13" because "a" is invalid but deleting "2" is valid
    ((AbstractDocument) textField.getDocument()).replace(1, 1, "a", null);
    assertThat(textField.getText()).isEqualTo("13");
  }
}
