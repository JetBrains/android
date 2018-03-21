/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.property.fixtures;

import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.common.property.fixtures.EditorFixtureBase;
import com.android.tools.idea.naveditor.property.editors.TextEditor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.*;

import static com.google.common.truth.Truth.assertThat;
import static java.awt.event.ComponentEvent.COMPONENT_RESIZED;

public class TextEditorFixture extends EditorFixtureBase {
  private final TextEditor myComponentEditor;
  private final EditorTextField myEditor;

  private TextEditorFixture(@NotNull TextEditor editor) {
    super(editor);
    myComponentEditor = editor;
    myEditor = findSubComponent(myComponentEditor.getComponent(), EditorTextField.class);
    myEditor.addNotify();    // Creates editor
  }

  public static TextEditorFixture create(@NotNull Project project) {
    return new TextEditorFixture(new TextEditor(project, createListener()));
  }

  @Override
  public void tearDown() {
    myEditor.removeNotify(); // Removes editor
    super.tearDown();
  }

  public TextEditorFixture setProperty(@NotNull NlProperty property) {
    myComponentEditor.setProperty(property);
    myComponentEditor.getComponent().doLayout();
    return this;
  }

  public TextEditorFixture gainFocus() {
    setFocusedComponent(myEditor);
    myEditor.focusGained(new FocusEvent(myEditor, FocusEvent.FOCUS_GAINED));
    return this;
  }

  public TextEditorFixture loseFocus() {
    setFocusedComponent(null);
    myEditor.focusLost(new FocusEvent(myEditor, FocusEvent.FOCUS_GAINED));
    return this;
  }

  public TextEditorFixture expectValue(@Nullable String expectedValue) {
    assertThat(myComponentEditor.getProperty().getValue()).isEqualTo(expectedValue);
    return this;
  }

  public TextEditorFixture expectText(@Nullable String expectedText) {
    assertThat(myEditor.getText()).isEqualTo(expectedText);
    return this;
  }

  public TextEditorFixture expectSelectedText(@Nullable String expectedSelectedText) {
    assertThat(myEditor.getEditor()).isNotNull();
    assertThat(myEditor.getEditor().getSelectionModel().getSelectedText()).isEqualTo(expectedSelectedText);
    return this;
  }

  public TextEditorFixture setWidth(int width) {
    JComponent panel = myComponentEditor.getComponent();
    panel.setSize(width, 80);
    ComponentEvent event = new ComponentEvent(panel, COMPONENT_RESIZED);
    for (ComponentListener listener : myComponentEditor.getComponent().getComponentListeners()) {
      listener.componentResized(event);
    }
    panel.doLayout();
    return this;
  }

  public TextEditorFixture verifyStopEditingCalled() {
    verifyStopEditingCalled(myComponentEditor);
    return this;
  }

  public TextEditorFixture verifyCancelEditingCalled() {
    verifyCancelEditingCalled(myComponentEditor);
    return this;
  }

  public TextEditorFixture type(@NotNull String value) {
    value.chars().forEach(key -> fireKeyStroke(KeyStroke.getKeyStroke((char)key)));
    return this;
  }

  public TextEditorFixture key(@MagicConstant(flagsFromClass = KeyEvent.class) int keyCode) {
    return key(keyCode, 0);
  }

  public TextEditorFixture key(@MagicConstant(flagsFromClass = KeyEvent.class) int keyCode,
                               @MagicConstant(flagsFromClass = InputEvent.class) int modifiers) {
    fireKeyStroke(KeyStroke.getKeyStroke(keyCode, modifiers));
    return this;
  }

  private void fireKeyStroke(@NotNull KeyStroke stroke) {
    ActionListener listener = myEditor.getActionForKeyStroke(stroke);
    if (listener != null) {
      listener.actionPerformed(new ActionEvent(myEditor, 0, String.valueOf(stroke.getKeyChar()), stroke.getModifiers()));
    }
    else if (stroke.getKeyCode() == KeyEvent.VK_UNDEFINED) {
      EditorEx editor = (EditorEx)myEditor.getEditor();
      assertThat(editor).isNotNull();
      KeyEvent event = new KeyEvent(myEditor, KeyEvent.KEY_TYPED, 0, stroke.getModifiers(), stroke.getKeyCode(), stroke.getKeyChar());
      editor.processKeyTyped(event);
    }
  }
}
