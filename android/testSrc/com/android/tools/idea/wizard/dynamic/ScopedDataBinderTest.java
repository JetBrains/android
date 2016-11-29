/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.wizard.dynamic;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.ColorPanel;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.android.tools.idea.wizard.dynamic.ScopedDataBinder.ValueDeriver;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;

/**
 * Tests for {@link ScopedDataBinder}. These tests
 * exercise the UI binding and validation methods.
 */
public class ScopedDataBinderTest extends TestCase {

  ScopedDataBinder myScopedDataBinder = new ScopedDataBinder();
  ScopedStateStore myState = myScopedDataBinder.myState;

  @Override
  public void setUp() throws Exception {
    super.setUp();

  }

  public void testInvokeUpdate() throws Exception {

  }

  public void testRegisterValueDeriver() throws Exception {

  }

  public void testRegisterTextField() throws Exception {
    Key<String> textKey = myState.createKey("textField", String.class);
    Key<String> textKey2 = myState.createKey("boundSecond", String.class);
    final Key<String> triggerKey = myState.createKey("triggerKey", String.class);
    JTextField textField = new JTextField();

    myScopedDataBinder.register(textKey, textField);
    myScopedDataBinder.register(textKey2, textField);

    // Test binding UI -> Store
    textField.setText("Hello World!");
    assertEquals("Hello World!", myState.get(textKey));
    assertEquals("Hello World!", myState.get(textKey2));

    // Test binding Store -> UI
    myState.put(textKey, "Awesome");
    assertEquals("Awesome", textField.getText());
    assertEquals("Awesome", myState.get(textKey2));

    myState.put(textKey2, "Goodbye");
    assertEquals("Goodbye", textField.getText());
    assertEquals("Goodbye", myState.get(textKey));


    final AtomicBoolean respectsUserEdits = new AtomicBoolean(true);

    // Test value derivation
    myScopedDataBinder.registerValueDeriver(textKey, new ValueDeriver<String>() {
      @Nullable
      @Override
      public Set<Key<?>> getTriggerKeys() {
        return makeSetOf(triggerKey);
      }

      @Override
      public boolean respectUserEdits() {
        return respectsUserEdits.get();
      }

      @NotNull
      @Override
      public String deriveValue(ScopedStateStore state, Key changedKey, @Nullable String currentValue) {
        String trigger = state.get(triggerKey);
        if (trigger == null) {
          return "UNEXPECTED NULL!";
        } else {
          return trigger.toUpperCase();
        }
      }
    });

    myState.put(triggerKey, "Some value to trigger update");
    // The deriver does not fire because user edits are respected
    assertEquals("Goodbye", textField.getText());

    respectsUserEdits.set(false);
    myState.put(triggerKey, "the quick brown fox");
    // The deriver fires because user edits are not respected
    assertEquals("THE QUICK BROWN FOX", textField.getText());
  }

  public void testRegisterTextFieldWithBrowseButton() throws Exception {
    Key<String> textKey = myState.createKey("textField", String.class);
    Key<String> textKey2 = myState.createKey("boundSecond", String.class);
    final Key<String> triggerKey = myState.createKey("triggerKey", String.class);
    TextFieldWithBrowseButton textField = new TextFieldWithBrowseButton();

    myScopedDataBinder.register(textKey, textField);
    myScopedDataBinder.register(textKey2, textField);

    // Test binding UI -> Store
    textField.setText("Hello World!");
    assertEquals("Hello World!", myState.get(textKey));
    assertEquals("Hello World!", myState.get(textKey2));

    // Test binding Store -> UI
    myState.put(textKey, "Awesome");
    assertEquals("Awesome", textField.getText());
    assertEquals("Awesome", myState.get(textKey2));

    myState.put(textKey2, "Goodbye");
    assertEquals("Goodbye", textField.getText());
    assertEquals("Goodbye", myState.get(textKey));

    final AtomicBoolean respectsUserEdits = new AtomicBoolean(true);

    // Test value derivation
    myScopedDataBinder.registerValueDeriver(textKey, new ValueDeriver<String>() {
      @Nullable
      @Override
      public Set<Key<?>> getTriggerKeys() {
        return makeSetOf(triggerKey);
      }

      @Override
      public boolean respectUserEdits() {
        return respectsUserEdits.get();
      }

      @NotNull
      @Override
      public String deriveValue(ScopedStateStore state, Key changedKey, @Nullable String currentValue) {
        String trigger = state.get(triggerKey);
        if (trigger == null) {
          return "UNEXPECTED NULL!";
        } else {
          return trigger.toUpperCase();
        }
      }
    });

    myState.put(triggerKey, "Some value to trigger update");
    // The deriver does not fire because user edits are respected
    assertEquals("Goodbye", textField.getText());

    respectsUserEdits.set(false);
    myState.put(triggerKey, "the quick brown fox");
    // The deriver fires because user edits are not respected
    assertEquals("THE QUICK BROWN FOX", textField.getText());
  }

  public void testRegisterCheckbox() throws Exception {
    Key<Boolean> booleanKey = myState.createKey("checkBox", Boolean.class);
    Key<Boolean> booleanKey2 = myState.createKey("boundSecond", Boolean.class);
    final Key<String> triggerKey = myState.createKey("triggerKey", String.class);
    JCheckBox checkBox = new JCheckBox();

    myScopedDataBinder.register(booleanKey, checkBox);
    myScopedDataBinder.register(booleanKey2, checkBox);

    // Test binding UI -> Store
    checkBox.setSelected(true);
    assertEquals(Boolean.TRUE, myState.get(booleanKey));
    assertEquals(Boolean.TRUE, myState.get(booleanKey2));

    // Test binding Store -> UI
    myState.put(booleanKey, Boolean.FALSE);
    assertEquals(false, checkBox.isSelected());
    assertEquals(Boolean.FALSE, myState.get(booleanKey2));

    myState.put(booleanKey2, true);
    assertEquals(true, checkBox.isSelected());
    assertEquals(Boolean.TRUE, myState.get(booleanKey));

    final AtomicBoolean respectsUserEdits = new AtomicBoolean(true);

    // Test value derivation
    myScopedDataBinder.registerValueDeriver(booleanKey, new ValueDeriver<Boolean>() {
      @Nullable
      @Override
      public Set<Key<?>> getTriggerKeys() {
        return makeSetOf(triggerKey);
      }

      @Override
      public boolean respectUserEdits() {
        return respectsUserEdits.get();
      }

      @Override
      public Boolean deriveValue(ScopedStateStore state, Key changedKey, @Nullable Boolean currentValue) {
        String trigger = state.get(triggerKey);
        if (trigger == null) {
          return null;
        } else {
          return Boolean.parseBoolean(trigger);
        }
      }
    });

    myState.put(triggerKey, "Not A Value");
    // The deriver does not fire because user edits are respected
    assertEquals(true, checkBox.isSelected());

    respectsUserEdits.set(false);
    myState.put(triggerKey, "false");
    // The deriver fires because user edits are not respected
    assertEquals(false, checkBox.isSelected());
  }

  public void testRegisterSlider() throws Exception {
    Key<Integer> integerKey = myState.createKey("slider", Integer.class);
    Key<Integer> integerKey2 = myState.createKey("boundSecond", Integer.class);
    final Key<String> triggerKey = myState.createKey("triggerKey", String.class);
    JSlider slider = new JSlider();

    myScopedDataBinder.register(integerKey, slider);
    myScopedDataBinder.register(integerKey2, slider);

    // Test binding UI -> Store
    slider.setValue(25);
    assertEquals(Integer.valueOf(25), myState.get(integerKey));
    assertEquals(Integer.valueOf(25), myState.get(integerKey2));

    // Test binding Store -> UI
    myState.put(integerKey, 75);
    assertEquals(75, slider.getValue());
    assertEquals(Integer.valueOf(75), myState.get(integerKey2));

    myState.put(integerKey2, 33);
    assertEquals(33, slider.getValue());
    assertEquals(Integer.valueOf(33), myState.get(integerKey));

    final AtomicBoolean respectsUserEdits = new AtomicBoolean(true);

    // Test value derivation
    myScopedDataBinder.registerValueDeriver(integerKey, new ValueDeriver<Integer>() {
      @Nullable
      @Override
      public Set<Key<?>> getTriggerKeys() {
        return makeSetOf(triggerKey);
      }

      @Override
      public boolean respectUserEdits() {
        return respectsUserEdits.get();
      }

      @Override
      public Integer deriveValue(ScopedStateStore state, Key changedKey, @Nullable Integer currentValue) {
        String trigger = state.get(triggerKey);
        if (trigger == null) {
          return null;
        } else {
          return Integer.parseInt(trigger);
        }
      }
    });

    myState.put(triggerKey, "24");
    // The deriver does not fire because user edits are respected
    assertEquals(33, slider.getValue());

    respectsUserEdits.set(false);
    myState.put(triggerKey, "42");
    // The deriver fires because user edits are not respected
    assertEquals(42, slider.getValue());
  }

  public void testRegisterColorPanel() throws Exception {
    Key<Color> colorKey = myState.createKey("colorPanel", Color.class);
    Key<Color> colorKey2 = myState.createKey("boundSecond", Color.class);
    final Key<String> triggerKey = myState.createKey("triggerKey", String.class);
    ColorPanel colorPanel = new ColorPanel();

    myScopedDataBinder.register(colorKey, colorPanel);
    myScopedDataBinder.register(colorKey2, colorPanel);

    // Test binding UI -> Store
    colorPanel.setSelectedColor(Color.BLUE);
    // ColorPanel doesn't call listeners on setSelectedColor, so we manually invoke here
    myScopedDataBinder.saveState(colorPanel);
    assertEquals(Color.BLUE, myState.get(colorKey));
    assertEquals(Color.BLUE, myState.get(colorKey2));

    // Test binding Store -> UI
    myState.put(colorKey, Color.RED);
    // ColorPanel doesn't call listeners on setSelectedColor, so we manually invoke here
    myScopedDataBinder.saveState(colorPanel);
    assertEquals(Color.RED, colorPanel.getSelectedColor());
    assertEquals(Color.RED, myState.get(colorKey2));

    myState.put(colorKey2, Color.GREEN);
    // ColorPanel doesn't call listeners on setSelectedColor, so we manually invoke here
    myScopedDataBinder.saveState(colorPanel);
    assertEquals(Color.GREEN, colorPanel.getSelectedColor());
    assertEquals(Color.GREEN, myState.get(colorKey));

    final AtomicBoolean respectsUserEdits = new AtomicBoolean(true);

    // Test value derivation
    myScopedDataBinder.registerValueDeriver(colorKey, new ValueDeriver<Color>() {
      @Nullable
      @Override
      public Set<Key<?>> getTriggerKeys() {
        return makeSetOf(triggerKey);
      }

      @Override
      public boolean respectUserEdits() {
        return respectsUserEdits.get();
      }

      @Override
      public Color deriveValue(ScopedStateStore state, Key changedKey, @Nullable Color currentValue) {
        String trigger = state.get(triggerKey);
        if (trigger == null) {
          return null;
        } else {
          return Color.decode(trigger);
        }
      }
    });

    myState.put(triggerKey, "Not A Value");
    // The deriver does not fire because user edits are respected
    assertEquals(Color.GREEN, colorPanel.getSelectedColor());

    respectsUserEdits.set(false);
    myState.put(triggerKey, "#FFFFFF");
    // The deriver fires because user edits are not respected
    assertEquals(Color.WHITE, colorPanel.getSelectedColor());
  }

  public void testRegisterRadioButton() throws Exception {
    Key<Boolean> booleanKey = myState.createKey("textField", Boolean.class);
    Key<Boolean> booleanKey2 = myState.createKey("boundSecond", Boolean.class);
    final Key<String> triggerKey = myState.createKey("triggerKey", String.class);
    JCheckBox checkBox = new JCheckBox();

    myScopedDataBinder.register(booleanKey, checkBox);
    myScopedDataBinder.register(booleanKey2, checkBox);

    // Test binding UI -> Store
    checkBox.setSelected(true);
    assertEquals(Boolean.TRUE, myState.get(booleanKey));
    assertEquals(Boolean.TRUE, myState.get(booleanKey2));

    // Test binding Store -> UI
    myState.put(booleanKey, Boolean.FALSE);
    assertEquals(false, checkBox.isSelected());
    assertEquals(Boolean.FALSE, myState.get(booleanKey2));

    myState.put(booleanKey2, true);
    assertEquals(true, checkBox.isSelected());
    assertEquals(Boolean.TRUE, myState.get(booleanKey));

    final AtomicBoolean respectsUserEdits = new AtomicBoolean(true);

    // Test value derivation
    myScopedDataBinder.registerValueDeriver(booleanKey, new ValueDeriver<Boolean>() {
      @Nullable
      @Override
      public Set<Key<?>> getTriggerKeys() {
        return makeSetOf(triggerKey);
      }

      @Override
      public boolean respectUserEdits() {
        return respectsUserEdits.get();
      }

      @Override
      public Boolean deriveValue(ScopedStateStore state, Key changedKey, @Nullable Boolean currentValue) {
        String trigger = state.get(triggerKey);
        if (trigger == null) {
          return null;
        } else {
          return Boolean.parseBoolean(trigger);
        }
      }
    });

    myState.put(triggerKey, "Not A Value");
    // The deriver does not fire because user edits are respected
    assertEquals(true, checkBox.isSelected());

    respectsUserEdits.set(false);
    myState.put(triggerKey, "false");
    // The deriver fires because user edits are not respected
    assertEquals(false, checkBox.isSelected());
  }

  public void testRegister6() throws Exception {

  }

  public void testRegister7() throws Exception {

  }

  public void testRegister8() throws Exception {

  }
}
