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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.adtui.swing.FakeUi;
import com.android.tools.idea.res.IdeResourceNameValidator;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.CheckBoxList;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.text.JTextComponent;
import org.junit.Rule;
import org.junit.Test;

@RunsInEdt
public final class CreateXmlResourcePanelImplTest {
  @Rule
  public final AndroidProjectRule myProjectRule = AndroidProjectRule.onDisk();

  @Rule
  public final EdtRule myEdtRule = new EdtRule();

  @Test
  public void testExistingResourceValidation_resourceExists() {
    myProjectRule.getFixture().addFileToProject("res/values/strings.xml",
                               //language=XML
                               "<resources>" +
                               "  <string name=\"foo\">foo</string>" +
                               "  <string name=\"bar\">@string/foo</string>" +
                               "</resources>");
    CreateXmlResourcePanelImpl xmlResourcePanel = new CreateXmlResourcePanelImpl(myProjectRule.getModule(), ResourceType.STRING, ResourceFolderType.VALUES,
                                                                                 "foo", "foobar", false, false, false, null, null,
                                                                                 validatorModule -> IdeResourceNameValidator
                                                                                   .forResourceName(ResourceType.STRING));
    ValidationInfo validationInfo = xmlResourcePanel.doValidate();
    assertNotNull(validationInfo);
    JTextField validationComponent = (JTextField)validationInfo.component;
    assertNotNull(validationComponent);
    assertThat(validationComponent.getText()).isEqualTo("foo");
    assertThat(validationInfo.message).isEqualTo("foo is a resource that already exists");
  }

  @Test
  public void testExistingResourceValidation_resourceDoesNotExist() {
    myProjectRule.getFixture().addFileToProject("res/values/strings.xml",
                               //language=XML
                               "<resources>" +
                               "  <string name=\"foo\">foo</string>" +
                               "  <string name=\"bar\">@string/foo</string>" +
                               "</resources>");
    CreateXmlResourcePanelImpl correctResourcePanel = new CreateXmlResourcePanelImpl(myProjectRule.getModule(), ResourceType.STRING,
                                                                                     ResourceFolderType.VALUES, "brandnewname", "foobar",
                                                                                     false, false, false, null, null,
                                                                                     validatorModule -> IdeResourceNameValidator
                                                                                       .forResourceName(ResourceType.STRING));
    assertThat(correctResourcePanel.doValidate()).isNull();
  }

  @Test
  public void testStringResourceNotEncoded() {
    // See b/196248641. This panel should show the "plain-text" version of a string, since users aren't expected to input a value here with
    // correct Android encoding. The string is encoded later at the point where it is written into the resource file.
    testStringResourceNotEncoded("simple value");
    testStringResourceNotEncoded("value with double quote \"");
    testStringResourceNotEncoded("value with trailing space ");
    testStringResourceNotEncoded("value with emoji " + "\uD83D\uDE00" + "😛");
    testStringResourceNotEncoded("value with Unicode chars ãä");
    testStringResourceNotEncoded("value with escape sequences \t\b\n\r\f'\"\\");
    StringBuilder allAscii = new StringBuilder();
    for (int i = 0; i < 256; i++) {
      allAscii.append((char)i);
    }
    testStringResourceNotEncoded(allAscii.toString());
  }

  private void testStringResourceNotEncoded(String resourceValue) {
    CreateXmlResourcePanelImpl correctResourcePanel = new CreateXmlResourcePanelImpl(myProjectRule.getModule(), ResourceType.STRING,
                                                                                     ResourceFolderType.VALUES, "resName",
                                                                                     resourceValue,
                                                                                     false, true, false, null, null,
                                                                                     validatorModule -> IdeResourceNameValidator
                                                                                       .forResourceName(ResourceType.STRING));

    assertThat(correctResourcePanel.getValue()).isEqualTo(resourceValue);
  }

  @Test
  public void testFocusedValueFieldWhenResourceNameIsGivenForString() {
    testFocusedValueFieldWhenResourceNameIsGiven(myProjectRule.getModule(), "string_name", ResourceType.STRING, JTextArea.class);
  }

  @Test
  public void testFocusedValueFieldWhenResourceNameIsGivenForColor() {
    testFocusedValueFieldWhenResourceNameIsGiven(myProjectRule.getModule(), "color_name", ResourceType.COLOR, JTextField.class);
  }

  @Test
  public void testFocusedNameFieldWhenResourceValueIsGivenForString() {
    testFocusedNameFieldWhenResourceValueIsGiven(myProjectRule.getModule(), "string_value", ResourceType.STRING);
  }

  @Test
  public void testFocusedNameFieldWhenResourceValueIsGivenForColor() {
    testFocusedNameFieldWhenResourceValueIsGiven(myProjectRule.getModule(), "#AFA", ResourceType.COLOR);
  }

  @Test
  public void testFolderDeletionCanBeUndone() throws InterruptedException {
    myProjectRule.getFixture().addFileToProject("res/values/colors.xml",
                                                //language=XML
                                                """
                                                  <?xml version="1.0" encoding="utf-8"?>
                                                  <resources>
                                                      <color name="purple_200">#FFBB86FC</color>
                                                      <color name="black">#FF000000</color>
                                                      <color name="white">#FFFFFFFF</color>
                                                  </resources>""");

    CreateXmlResourcePanelImpl xmlResourcePanel = new CreateXmlResourcePanelImpl(myProjectRule.getModule(),
                                                                                 ResourceType.COLOR,
                                                                                 ResourceFolderType.VALUES,
                                                                                 null,
                                                                                 null,
                                                                                 true,
                                                                                 true,
                                                                                 true,
                                                                                 null,
                                                                                 null,
                                                                                 validatorModule -> IdeResourceNameValidator
                                                                                   .forResourceName(ResourceType.COLOR));
    xmlResourcePanel.getPanel().setSize(640, 480);
    FakeUi fakeUi = new FakeUi(xmlResourcePanel.getPanel(), 1.0, true, myProjectRule.getTestRootDisposable());
    fakeUi.layoutAndDispatchEvents();

    // Select first element
    @SuppressWarnings("rawtypes")
    CheckBoxList checkBoxList = fakeUi.findComponent(CheckBoxList.class, checklist -> true);
    assertNotNull(checkBoxList);
    checkBoxList.getSelectionModel().setSelectionInterval(0, 0);
    fakeUi.layoutAndDispatchEvents();

    ActionToolbar toolbar = fakeUi.findComponent(ActionToolbar.class, toolbar1 -> true);
    assertNotNull(toolbar);
    AnActionButton removeAction =
      (AnActionButton)toolbar.getActions().stream().filter(action -> "Remove".equals(action.getTemplateText())).findFirst().orElseThrow();
    assertTrue(removeAction.isEnabled());

    // The delete action will ask for confirmation, respond yes.
    TestDialogManager.setTestDialog(TestDialog.YES);
    removeAction.actionPerformed(TestActionEvent.createTestEvent(removeAction));

    UndoManager undoManager = UndoManager.getInstance(myProjectRule.getProject());
    Pair<String, String> undoText = undoManager.getUndoActionNameAndDescription(null);
    assertEquals("_Undo Deleting Files", undoText.first);

    undoManager.undo(null);
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