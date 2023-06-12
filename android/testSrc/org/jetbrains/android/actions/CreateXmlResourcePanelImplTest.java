/*
 * Copyright (C) 2020 The Android Open Source Project
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
package org.jetbrains.android.actions;

import static com.google.common.truth.Truth.assertThat;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.IdeResourceNameValidator;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.ValidationInfo;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.text.JTextComponent;
import org.jetbrains.android.AndroidTestCase;

public final class CreateXmlResourcePanelImplTest extends AndroidTestCase {

  public void testExistingResourceValidation_resourceExists() {
    myFixture.addFileToProject("res/values/strings.xml",
                               //language=XML
                               "<resources>" +
                               "  <string name=\"foo\">foo</string>" +
                               "  <string name=\"bar\">@string/foo</string>" +
                               "</resources>");
    CreateXmlResourcePanelImpl xmlResourcePanel = new CreateXmlResourcePanelImpl(myModule, ResourceType.STRING, ResourceFolderType.VALUES,
                                                                                 "foo", "foobar", false, false, false, null, null,
                                                                                 validatorModule -> IdeResourceNameValidator
                                                                                   .forResourceName(ResourceType.STRING));
    ValidationInfo validationInfo = xmlResourcePanel.doValidate();
    assertThat(((JTextField)validationInfo.component).getText()).isEqualTo("foo");
    assertThat(validationInfo.message).isEqualTo("foo is a resource that already exists");
  }

  public void testExistingResourceValidation_resourceDoesNotExist() {
    myFixture.addFileToProject("res/values/strings.xml",
                               //language=XML
                               "<resources>" +
                               "  <string name=\"foo\">foo</string>" +
                               "  <string name=\"bar\">@string/foo</string>" +
                               "</resources>");
    CreateXmlResourcePanelImpl correctResourcePanel = new CreateXmlResourcePanelImpl(myModule, ResourceType.STRING,
                                                                                     ResourceFolderType.VALUES, "brandnewname", "foobar",
                                                                                     false, false, false, null, null,
                                                                                     validatorModule -> IdeResourceNameValidator
                                                                                       .forResourceName(ResourceType.STRING));
    assertThat(correctResourcePanel.doValidate()).isNull();
  }

  public void testStringResourceNotEncoded() {
    // See b/196248641. This panel should show the "plain-text" version of a string, since users aren't expected to input a value here with
    // correct Android encoding. The string is encoded later at the point where it is written into the resource file.
    testStringResourceNotEncoded("simple value");
    testStringResourceNotEncoded("value with double quote \"");
    testStringResourceNotEncoded("value with trailing space ");
    testStringResourceNotEncoded("value with emoji " + "\uD83D\uDE00" + "😛");
    testStringResourceNotEncoded("value with Unicode chars \u00e3\u00e4");
    testStringResourceNotEncoded("value with escape sequences \t\b\n\r\f\'\"\\");
    StringBuilder allAscii = new StringBuilder();
    for (int i = 0; i < 256; i++) {
      allAscii.append((char)i);
    }
    testStringResourceNotEncoded(allAscii.toString());
  }

  private void testStringResourceNotEncoded(String resourceValue) {
    CreateXmlResourcePanelImpl correctResourcePanel = new CreateXmlResourcePanelImpl(myModule, ResourceType.STRING,
                                                                                     ResourceFolderType.VALUES, "resName",
                                                                                     resourceValue,
                                                                                     false, true, false, null, null,
                                                                                     validatorModule -> IdeResourceNameValidator
                                                                                       .forResourceName(ResourceType.STRING));

    assertThat(correctResourcePanel.getValue()).isEqualTo(resourceValue);
  }

  public void testFocusedValueFieldWhenResourceNameIsGivenForString() {
    testFocusedValueFieldWhenResourceNameIsGiven(myModule, "string_name", ResourceType.STRING, JTextArea.class);
  }

  public void testFocusedValueFieldWhenResourceNameIsGivenForColor() {
    testFocusedValueFieldWhenResourceNameIsGiven(myModule, "color_name", ResourceType.COLOR, JTextField.class);
  }

  public void testFocusedNameFieldWhenResourceValueIsGivenForString() {
    testFocusedNameFieldWhenResourceValueIsGiven(myModule, "string_value", ResourceType.STRING);
  }

  public void testFocusedNameFieldWhenResourceValueIsGivenForColor() {
    testFocusedNameFieldWhenResourceValueIsGiven(myModule, "#AFA", ResourceType.COLOR);
  }

  private static void testFocusedNameFieldWhenResourceValueIsGiven(Module module, String resourceValue, ResourceType type) {
    CreateXmlResourcePanelImpl xmlResourcePanel = new CreateXmlResourcePanelImpl(module,
                                                                                 type,
                                                                                 ResourceFolderType.VALUES,
                                                                                 null,
                                                                                 resourceValue,
                                                                                 true,
                                                                                 true,
                                                                                 true,
                                                                                 null,
                                                                                 null,
                                                                                 validatorModule -> IdeResourceNameValidator
                                                                                   .forResourceName(type));
    JComponent focusComponent = xmlResourcePanel.getPreferredFocusedComponent();
    assertThat(focusComponent).isInstanceOf(JTextField.class);
    assertThat(((JTextField)focusComponent).getText()).isEmpty();

    assertThat(xmlResourcePanel.getValue()).isEqualTo(resourceValue);
    assertThat(xmlResourcePanel.getResourceName()).isEmpty(); // If only the name is empty, then it's likely the focused component.
  }

  private static void testFocusedValueFieldWhenResourceNameIsGiven(Module module,
                                                                   String resourceName,
                                                                   ResourceType type,
                                                                   Class<? extends JTextComponent> expectedTextComponent) {
    CreateXmlResourcePanelImpl xmlResourcePanel = new CreateXmlResourcePanelImpl(module,
                                                                                 type,
                                                                                 ResourceFolderType.VALUES,
                                                                                 resourceName,
                                                                                 null,
                                                                                 true,
                                                                                 true,
                                                                                 true,
                                                                                 null,
                                                                                 null,
                                                                                 validatorModule -> IdeResourceNameValidator
                                                                                   .forResourceName(type));
    JComponent focusComponent = xmlResourcePanel.getPreferredFocusedComponent();
    assertThat(focusComponent).isInstanceOf(expectedTextComponent);
    assertThat(((JTextComponent)focusComponent).getText()).isEmpty();

    assertThat(xmlResourcePanel.getValue()).isEmpty(); // If only the value is empty, then it's likely the focused component.
    assertThat(xmlResourcePanel.getResourceName()).isEqualTo(resourceName);
  }
}