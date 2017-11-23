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
package com.android.tools.idea.uibuilder.property.fixtures;

import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.common.property.fixtures.EditorFixtureBase;
import com.android.tools.idea.uibuilder.property.editors.BrowsePanel;
import com.android.tools.idea.uibuilder.property.editors.NlReferenceEditor;
import com.android.tools.idea.uibuilder.property.editors.NlReferenceEditor.SliderWithTimeDelay;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.event.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static com.google.common.truth.Truth.assertThat;
import static java.awt.event.ComponentEvent.COMPONENT_RESIZED;

public class NlReferenceEditorFixture extends EditorFixtureBase {
  private final NlReferenceEditor myComponentEditor;
  private final TextFieldWithCompletion myEditor;
  private final SliderWithTimeDelay mySlider;
  private final JLabel myIconLabel;
  private final Instant myStartTime;

  private NlReferenceEditorFixture(@NotNull NlReferenceEditor editor) {
    super(editor);
    myStartTime = Instant.now();
    myComponentEditor = editor;
    myEditor = findSubComponent(myComponentEditor.getComponent(), TextFieldWithCompletion.class);
    myIconLabel = findSubComponent(myComponentEditor.getComponent(), JLabel.class);
    mySlider = findSubComponent(myComponentEditor.getComponent(), SliderWithTimeDelay.class);
    mySlider.setUI(new MySliderUI(mySlider));
    mySlider.setClock(Clock.fixed(myStartTime, ZoneId.systemDefault()));
    myEditor.addNotify();    // Creates editor
    mySlider.addNotify();    // Adds initial timestamp
    mySlider.removeNotify(); // Removed peer object
  }

  @Override
  public void tearDown() {
    myEditor.removeNotify(); // Removes editor
    super.tearDown();
  }

  public static NlReferenceEditorFixture createForInspector(@NotNull Project project) {
    NlReferenceEditor editor = NlReferenceEditor.createForInspector(project, createListener());
    return new NlReferenceEditorFixture(editor);
  }

  public static NlReferenceEditorFixture createForTable(@NotNull Project project) {
    BrowsePanel browsePanel = new BrowsePanel(null, true);
    NlReferenceEditor editor = NlReferenceEditor.createForTableTesting(project, createListener(), browsePanel);
    return new NlReferenceEditorFixture(editor);
  }

  public NlReferenceEditorFixture setProperty(@NotNull NlProperty property) {
    myComponentEditor.setProperty(property);
    myComponentEditor.getComponent().doLayout();
    return this;
  }

  public NlReferenceEditorFixture gainFocus() {
    setFocusedComponent(myEditor);
    myEditor.focusGained(new FocusEvent(myEditor, FocusEvent.FOCUS_GAINED));
    return this;
  }

  public NlReferenceEditorFixture loseFocus() {
    setFocusedComponent(null);
    myEditor.focusLost(new FocusEvent(myEditor, FocusEvent.FOCUS_GAINED));
    return this;
  }

  public NlReferenceEditorFixture expectValue(@Nullable String expectedValue) {
    assertThat(myComponentEditor.getProperty().getValue()).isEqualTo(expectedValue);
    return this;
  }

  public NlReferenceEditorFixture expectText(@Nullable String expectedText) {
    assertThat(myEditor.getText()).isEqualTo(expectedText);
    return this;
  }

  public NlReferenceEditorFixture expectSelectedText(@Nullable String expectedSelectedText) {
    assertThat(myEditor.getEditor()).isNotNull();
    assertThat(myEditor.getEditor().getSelectionModel().getSelectedText()).isEqualTo(expectedSelectedText);
    return this;
  }

  public NlReferenceEditorFixture expectSliderVisible(boolean expected) {
    boolean sliderVisible = mySlider.getParent() != null && mySlider.isVisible();
    assertThat(sliderVisible).isEqualTo(expected);
    return this;
  }

  public NlReferenceEditorFixture expectIconVisible(boolean expected) {
    boolean iconVisible = myIconLabel.getParent() != null && myIconLabel.isVisible();
    assertThat(iconVisible).isEqualTo(expected);
    return this;
  }

  public NlReferenceEditorFixture setWidth(int width) {
    JComponent panel = myComponentEditor.getComponent();
    panel.setSize(width, 80);
    ComponentEvent event = new ComponentEvent(panel, COMPONENT_RESIZED);
    for (ComponentListener listener : myComponentEditor.getComponent().getComponentListeners()) {
      listener.componentResized(event);
    }
    panel.doLayout();
    return this;
  }

  public NlReferenceEditorFixture updateTime(int secondsAfterStart) {
    mySlider.setClock(Clock.fixed(myStartTime.plus(secondsAfterStart, ChronoUnit.SECONDS), ZoneId.systemDefault()));
    return this;
  }

  public NlReferenceEditorFixture clickOnSlider(double percent) throws InterruptedException {
    int width = mySlider.getWidth();
    int position = (int)(width * percent) + 1;
    position = Math.min(position, width);
    position = Math.max(position, 0);
    MouseEvent mousePressed =
      new MouseEvent(mySlider, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK, position, 8, 1, false, MouseEvent.BUTTON1);
    MouseEvent mouseReleased =
      new MouseEvent(mySlider, MouseEvent.MOUSE_RELEASED, 0, InputEvent.BUTTON1_DOWN_MASK, position, 8, 1, false, MouseEvent.BUTTON1);

    // On Windows & Linux the slider jumps in small increments, on Mac it does one jump tot he specified position.
    // Repeat this event several times to go to the wanted position.
    for (int repeat = 0; repeat < 20; repeat++) {
      mySlider.dispatchEvent(mousePressed);
      mySlider.dispatchEvent(mouseReleased);
    }
    // Sleep to allow all timers in the slider to fire.
    Thread.sleep(40);
    // Make sure all timer events are processed.
    UIUtil.dispatchAllInvocationEvents();
    return this;
  }

  public NlReferenceEditorFixture verifyStopEditingCalled() {
    verifyStopEditingCalled(myComponentEditor);
    return this;
  }

  public NlReferenceEditorFixture verifyCancelEditingCalled() {
    verifyCancelEditingCalled(myComponentEditor);
    return this;
  }

  public NlReferenceEditorFixture type(@NotNull String value) {
    value.chars().forEach(key -> fireKeyStroke(KeyStroke.getKeyStroke((char)key)));
    return this;
  }

  public NlReferenceEditorFixture key(@MagicConstant(flagsFromClass = KeyEvent.class) int keyCode) {
    return key(keyCode, 0);
  }

  public NlReferenceEditorFixture key(@MagicConstant(flagsFromClass = KeyEvent.class) int keyCode,
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

  private static class MySliderUI extends BasicSliderUI {

    public MySliderUI(@NotNull JSlider slider) {
      super(slider);
    }

    @Override
    public void installUI(@NotNull JComponent component) {
      super.installUI(component);
      scrollTimer.setInitialDelay(1);
      scrollTimer.setDelay(1);
    }
  }
}
