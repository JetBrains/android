/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.fixtures;

import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.*;
import com.android.tools.idea.uibuilder.property.editors.support.ValueWithDisplayString;
import com.google.common.base.Objects;
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI;
import com.intellij.openapi.ui.ComboBox;
import junit.framework.Assert;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static java.awt.event.KeyEvent.VK_DOWN;
import static java.awt.event.KeyEvent.VK_UP;
import static javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class NlEnumEditorFixture {
  private final NlEditingListener myListener;
  private final NlEnumEditor myComponentEditor;
  private final ComboBox myCombo;
  private final JTextField myEditor;
  private final Component mySource;

  private Component myFocusedComponent;
  private int myStopEditingCallCount;
  private int myCancelEditingCallCount;

  private NlEnumEditorFixture() {
    myListener = createListener();
    myComponentEditor = NlEnumEditor.createForTest(myListener, spy(new NlEnumEditor.CustomComboBox()));
    myCombo = findSubComponent(myComponentEditor.getComponent(), ComboBox.class);
    myCombo.setUI(new ComboUI(myCombo));
    when(myCombo.isShowing()).thenReturn(true);
    myEditor = (JTextField)myCombo.getEditor().getEditorComponent();
    myEditor.getCaret().setBlinkRate(0);
    KeyboardFocusManager focusManager = mock(KeyboardFocusManager.class);
    doReturn(myFocusedComponent).when(focusManager).getFocusOwner();
    KeyboardFocusManager.setCurrentKeyboardFocusManager(focusManager);
    mySource = mock(JComponent.class);
  }

  public static NlEnumEditorFixture create() {
    return new NlEnumEditorFixture();
  }

  public void tearDown() {
    KeyboardFocusManager.setCurrentKeyboardFocusManager(null);
  }

  public NlEnumEditorFixture setProperty(@NotNull NlProperty property) {
    myComponentEditor.setProperty(property);
    return this;
  }

  public NlEnumEditorFixture gainFocus() {
    setFocusedComponent(myEditor);

    for (FocusListener listener : myEditor.getFocusListeners()) {
      listener.focusGained(new FocusEvent(mySource, FocusEvent.FOCUS_GAINED));
    }
    return this;
  }

  public NlEnumEditorFixture loseFocus() {
    myFocusedComponent = null;
    for (FocusListener listener : myEditor.getFocusListeners()) {
      listener.focusLost(new FocusEvent(mySource, FocusEvent.FOCUS_LOST));
    }
    return this;
  }

  public NlEnumEditorFixture showPopup() {
    assertThat(myCombo).isNotNull();
    myCombo.isPopupVisible();
    myCombo.showPopup();
    ComboPopup popup = myCombo.getPopup();
    assertThat(popup).isNotNull();
    setFocusedComponent(popup.getList());
    assertThat(myCombo.isPopupVisible()).isTrue();
    return this;
  }

  public NlEnumEditorFixture hidePopup() {
    assertThat(myCombo).isNotNull();
    myCombo.hidePopup();
    assertThat(myCombo.isPopupVisible()).isFalse();
    setFocusedComponent(myEditor);
    return this;
  }

  public NlEnumEditorFixture expectPopupVisible(boolean expected) {
    assertThat(myCombo.isPopupVisible()).isEqualTo(expected);
    return this;
  }

  public NlEnumEditorFixture setSelectedModelItem(@Nullable Object item) {
    @SuppressWarnings("unchecked")
    ComboBoxModel<ValueWithDisplayString> model = myCombo.getModel();
    model.setSelectedItem(item);
    return this;
  }

  public NlEnumEditorFixture type(@NotNull String value) {
    value.chars().forEach(key -> fireKeyStroke(KeyStroke.getKeyStroke((char)key)));
    return this;
  }

  public NlEnumEditorFixture key(@MagicConstant(flagsFromClass = KeyEvent.class) int keyCode) {
    return key(keyCode, 0);
  }

  public NlEnumEditorFixture key(@MagicConstant(flagsFromClass = KeyEvent.class) int keyCode,
                                 @MagicConstant(flagsFromClass = InputEvent.class) int modifiers) {
    fireKeyStroke(KeyStroke.getKeyStroke(keyCode, modifiers));
    return this;
  }

  private void fireKeyStroke(@NotNull KeyStroke stroke) {
    JComponent component = myCombo.isPopupVisible() ? myCombo : myEditor;
    ActionListener listener = myEditor.getActionForKeyStroke(stroke);
    if (listener == null && myCombo.isPopupVisible()) {
      listener = myCombo.getActionForKeyStroke(stroke);
    }
    assertThat(listener).named("actionListener for key: " + stroke).isNotNull();
    assert listener != null;
    listener.actionPerformed(new ActionEvent(component, 0, String.valueOf(stroke.getKeyChar()), stroke.getModifiers()));
  }

  public NlEnumEditorFixture expectValue(@Nullable String expectedValue) {
    assertThat(myComponentEditor.getProperty()).isNotNull();
    assertThat(myComponentEditor.getProperty().getValue()).isEqualTo(expectedValue);
    return this;
  }

  public NlEnumEditorFixture expectText(@Nullable String expectedText) {
    assertThat(myEditor.getText()).isEqualTo(expectedText);
    return this;
  }

  public NlEnumEditorFixture expectSelectedText(@Nullable String expectedSelectedText) {
    assertThat(myEditor.getSelectedText()).isEqualTo(expectedSelectedText);
    return this;
  }

  public NlEnumEditorFixture expectChoices(@NotNull String... choices) {
    @SuppressWarnings("unchecked")
    ComboBoxModel<ValueWithDisplayString> model = myCombo.getModel();
    int rows = choices.length / 2;
    if (model.getSize() * 2 == choices.length) {
      checkChoices(model, rows, choices);
    }
    else {
      dumpDifference(model, choices);
    }
    return this;
  }

  public NlEnumEditorFixture expectFirstChoices(int count, @NotNull String... choices) {
    @SuppressWarnings("unchecked")
    ComboBoxModel<ValueWithDisplayString> model = myCombo.getModel();
    int rows = choices.length / 2;
    assertThat(count).isAtMost(rows);
    if (countDifferences(model, rows, choices) > 2) {
      dumpDifference(model, choices);
    }
    else {
      checkChoices(model, rows, choices);
    }
    return this;
  }

  private static void checkChoices(@NotNull ComboBoxModel<ValueWithDisplayString> model, int count, @NotNull String... choices) {
    for (int index = 0; index < count; index++) {
      ValueWithDisplayString value = model.getElementAt(index);
      assertThat(value).isNotNull();
      assertThat(value.getDisplayString()).isEqualTo(choices[2 * index]);
      assertThat(value.getValue()).isEqualTo(choices[2 * index + 1]);
    }
  }

  private static void dumpDifference(@NotNull ComboBoxModel<ValueWithDisplayString> model, @NotNull String... choices) {
    StringBuilder msg = new StringBuilder();
    msg.append("\nExpected:\n");
    int rows = choices.length / 2;
    for (int index = 0; index < rows; index++) {
      msg.append(String.format("%10s - %s\n", choices[2 * index], choices[2 * index + 1]));
    }
    msg.append("\nActual:\n");
    for (int index = 0; index < model.getSize(); index++) {
      ValueWithDisplayString value = model.getElementAt(index);
      msg.append(String.format("%10s - %s\n", value.getDisplayString(), value.getValue()));
    }
    msg.append(String.format("\nExpected count: %d, Actual count: %d\n", choices.length, model.getSize() * 2));
    Assert.fail(msg.toString());
  }

  private static int countDifferences(@NotNull ComboBoxModel<ValueWithDisplayString> model, int count, @NotNull String... choices) {
    int differences = 0;
    for (int index = 0; index < count; index++) {
      ValueWithDisplayString value = model.getElementAt(index);
      if (value == null) {
        differences++;
      }
      else {
        if (!Objects.equal(value.getDisplayString(), choices[2 * index])) {
          differences++;
        }
        if (!Objects.equal(value.getValue(), choices[2 * index + 1])) {
          differences++;
        }
      }
    }
    return differences;
  }

  public NlEnumEditorFixture verifyStopEditingCalled() {
    verify(myListener, times(++myStopEditingCallCount)).stopEditing(eq(myComponentEditor), any());
    return this;
  }

  public NlEnumEditorFixture verifyCancelEditingCalled() {
    verify(myListener, times(++myCancelEditingCallCount)).cancelEditing(eq(myComponentEditor));
    return this;
  }

  private void setFocusedComponent(@Nullable Component component) {
    myFocusedComponent = component;
  }

  private static class ComboUI extends DarculaComboBoxUI {
    private boolean myPopupIsVisible;
    private JComboBox myCombo;

    public ComboUI(@NotNull JComboBox comboBox) {
      super(comboBox);
      myCombo = comboBox;
    }

    @Override
    protected ComboPopup createPopup() {
      BasicComboPopup popup = spy((BasicComboPopup)super.createPopup());

      doAnswer(invocation -> {
        if (myPopupIsVisible) {
          myCombo.firePopupMenuWillBecomeInvisible();
          uninstallKeyMap(myCombo);
        }
        myPopupIsVisible = false;
        return null;
      }).when(popup).hide();

      doAnswer(invocation -> {
        if (!myPopupIsVisible) {
          myCombo.firePopupMenuWillBecomeVisible();
          installKeyMap(myCombo);
        }
        myPopupIsVisible = true;
        return null;
      }).when(popup).show();

      doAnswer(invocation -> myPopupIsVisible).when(popup).isVisible();
      return popup;
    }
  }

  private static void installKeyMap(@NotNull JComboBox comboBox) {
    InputMap map = comboBox.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    map.put(KeyStroke.getKeyStroke(VK_UP, 0), "selectPrevious");
    map.put(KeyStroke.getKeyStroke(VK_DOWN, 0), "selectNext");
  }

  private static void uninstallKeyMap(@NotNull JComboBox comboBox) {
    InputMap map = comboBox.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    map.remove(KeyStroke.getKeyStroke(VK_UP, 0));
    map.remove(KeyStroke.getKeyStroke(VK_DOWN, 0));
  }

  protected static NlEditingListener createListener() {
    NlEditingListener listener = mock(NlEditingListener.class);
    doAnswer(invocation -> {
      Object[] args = invocation.getArguments();
      NlComponentEditor editor = (NlComponentEditor)args[0];
      Object value = args[1];
      NlProperty property = editor.getProperty();
      assertThat(property).isNotNull();
      editor.getProperty().setValue(value);
      return null;
    }).when(listener).stopEditing(any(NlComponentEditor.class), any());
    return listener;
  }

  @NotNull
  private static <T> T findSubComponent(JComponent panel, Class<T> componentClass) {
    Optional<Component> result = Arrays.stream(panel.getComponents())
      .filter(componentClass::isInstance)
      .findFirst();
    //noinspection unchecked
    return (T)result.orElseThrow(() -> new RuntimeException("No such component found: " + componentClass.getName()));
  }
}
