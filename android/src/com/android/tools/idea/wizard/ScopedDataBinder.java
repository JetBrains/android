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
package com.android.tools.idea.wizard;

import com.android.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.*;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.wizard.ScopedStateStore.*;

/**
 * A data binding class that links Swing UI elements to a {@link ScopedStateStore}.
 */
public class ScopedDataBinder implements ScopedStateStore.ScopedStoreListener, FocusListener, ChangeListener, ActionListener,
                                         DocumentListener, ItemListener {
  // State store
  protected ScopedStateStore myState;

  // Mapping documents to components.
  private Map<Document, JComponent> myDocumentsToComponent = Maps.newIdentityHashMap();

  // Map of keys to custom value derivations
  private BiMap<Key, ValueDeriver> myValueDerivers = HashBiMap.create();

  // Table mapping components and keys to bindings
  private Table<JComponent, Key<?>, ComponentBinding<?, ?>> myComponentBindings = HashBasedTable.create();

  // Record of keys that have already been changed or updated during a round to prevent
  // recursive derivations.
  private Set<Key> myGuardedKeys = Sets.newHashSet();

  // Record of keys that the user has manually edited
  private Set<Key> myUserEditedKeys = Sets.newHashSet();

  // Flags to guard against cyclical updates
  private boolean myAlreadySavingState;

  // Binding that triggered the store update or null if none
  private JComponent myUpdateTrigger;

  public ScopedDataBinder() {
    myState = new ScopedStateStore(Scope.STEP, null, this);
  }

  /**
   * Triggered on every state change. Will call object's value derivers and value setters.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T> void invokeUpdate(@Nullable Key<T> changedKey) {
    if (changedKey == null) {
      return;
    }
    myGuardedKeys.add(changedKey);

    // Update the UI value if the user has not caused this state change
    Object value = unwrap(myState.get(changedKey));
    if (value != null) {
      for (Map.Entry<JComponent, ComponentBinding<?, ?>> entry : myComponentBindings.column(changedKey).entrySet()) {
        JComponent component = entry.getKey();
        if (!Objects.equal(component, myUpdateTrigger)) {
          ComponentBinding binding = myComponentBindings.get(component, changedKey);
          try {
            binding.setValue(value, component);
          }
          catch (UnsupportedOperationException e) {
            // This binding is not two-way, and does not support setting the value.
            // Nothing to do here.
          }
        }
      }

      // Loop over our value derivers and call them if necessary
      for (Key key : myValueDerivers.keySet()) {
        // Don't derive values that have already been updated this round
        if (!myGuardedKeys.contains(key)) {
          ValueDeriver deriver = myValueDerivers.get(key);
          // Don't overwrite values that the user has manually entered
          if (myUserEditedKeys.contains(key) && deriver.respectUserEdits()) {
            continue;
          }
          Set<Key> triggerKeys = deriver.getTriggerKeys();
          // Respect the deriver's filter of triggers
          if (triggerKeys != null && !triggerKeys.contains(changedKey)) {
            continue;
          }
          deriveValue(key, deriver, changedKey);
        }
      }
    }
    myGuardedKeys.remove(changedKey);
  }

  /**
   * Use the given deriver to update the value associated with the given key.
   */
  private <T> void deriveValue(Key<T> key, ValueDeriver<T> deriver, Key<?> changedKey) {
    Pair<T, Key<T>> scopedPair = myState.get(key);
    T newValue = deriver.deriveValue(myState, changedKey, scopedPair.first);
    myState.put(key, newValue);
  }

  /**
   * Triggered by a UI change. Will persist the change to the state store.
   */
  @VisibleForTesting
  @SuppressWarnings("unchecked")
  protected <T> void saveState(@NotNull JComponent component) {
    if (myAlreadySavingState) {
      return;
    }
    myAlreadySavingState = true;
    // Update all keys associated with this component
    for (Map.Entry<Key<?>, ComponentBinding<?, ?>> entry :
        myComponentBindings.row(component).entrySet()) {
      if (myGuardedKeys.contains(entry.getKey())) {
        continue;
      }
      ComponentBinding<T, JComponent> binding = (ComponentBinding<T, JComponent>)entry.getValue();
      try {
        T value = binding.getValue(component);
        storeValue((Key<T>)entry.getKey(), component, value);
      } catch (UnsupportedOperationException e) {
        // This binding must be one-way, not supporting getting the value
        // In this case, there is no update to do here
      }
    }
    myAlreadySavingState = false;
  }

  /**
   * Store the given element in the state store without invoking another update round.
   */
  private <T> void storeValue(@NotNull Key<T> key, @NotNull JComponent updateTrigger, @Nullable T value) {
    T oldValue = unwrap(myState.get(key));
    // If the incoming value is new (not already reflected in the model) it must be from a user edit.
    if (oldValue != null && !oldValue.equals(value) && !myGuardedKeys.contains(key)) {
      myUserEditedKeys.add(key);
    }
    myUpdateTrigger = updateTrigger;
    myState.put(key, value);
    myUpdateTrigger = null;
  }

  /**
   * Retrieve a value from the given JComponent
   */
  @Nullable
  protected Object getComponentValue(@NotNull JComponent component) {
    Object newValue = null;
    if (component instanceof JCheckBox) {
      newValue = ((JCheckBox)component).isSelected();
    }
    else if (component instanceof JComboBox) {
      ComboBoxItem selectedItem = (ComboBoxItem)((JComboBox)component).getSelectedItem();
      if (selectedItem != null) {
        newValue = selectedItem.id;
      }
    }
    else if (component instanceof JTextField) {
      newValue = ((JTextField)component).getText();
    } else if (component instanceof TextFieldWithBrowseButton) {
      newValue = ((TextFieldWithBrowseButton)component).getText();
    } else if (component instanceof JSlider) {
      newValue = ((JSlider)component).getValue();
    } else if (component instanceof JSpinner) {
      newValue = ((JSpinner)component).getValue();
    } else if (component instanceof ColorPanel) {
      newValue = ((ColorPanel)component).getSelectedColor();
    }
    return newValue;
  }

  /**
   * Set the value for the given JComponent
   */
  protected void setComponentValue(@Nullable Object value, @NotNull JComponent component) {
    if (component instanceof JCheckBox) {
      ((JCheckBox)component).setSelected(Boolean.TRUE.equals(value));
    } else if (component instanceof JComboBox) {
      setSelectedItem((JComboBox)component, value);
    } else if (component instanceof JTextField) {
      ((JTextField)component).setText((String)value);
    } else if (component instanceof TextFieldWithBrowseButton) {
      ((TextFieldWithBrowseButton)component).setText(StringUtil.notNullize((String)value));
    } else if (component instanceof JSlider) {
      assert value != null;
      ((JSlider)component).setValue((Integer)value);
    } else if (component instanceof JSpinner) {
      ((JSpinner)component).setValue(value);
    } else if (component instanceof ColorPanel) {
      assert value != null;
      ((ColorPanel)component).setSelectedColor((Color)value);
    }
  }

  /**
   * Allows clients of {@link ScopedDataBinder} to register custom logic which occurs whenever
   * a value is updated in order to update the underlying fields.
   * @param <T> the type of object that is derived.
   */
  public static abstract class ValueDeriver<T> {
    /**
     * @return false if this ValueDeriver should overwrite user-entered values.
     */
    public boolean respectUserEdits() {
      return true;
    }

    /**
     * @return a list of keys. The deriveValue function will only be called if the changed key is one of
     * the trigger keys. Return null to trigger on every update.
     */
    @Nullable
    public Set<Key<?>> getTriggerKeys() {
      return null;
    }

    @Nullable
    protected static Set<Key<?>> makeSetOf(Key<?>... elements) {
      Set<Key<?>> keys = new HashSet<Key<?>>(elements.length);
      Collections.addAll(keys, elements);
      return keys;
    }

    /**
     * When this ValueDeriver is registered for a given key, the return value of this function will be stored in the state store
     * after every update to the state store which meets the parameters set by the other functions in this class.
     */
    @Nullable
    public abstract T deriveValue(ScopedStateStore state, Key changedKey, @Nullable T currentValue);
  }

  /**
   * Connects the given {@link ValueDeriver} to the given key. Whenever an update is triggered, the given deriver will
   * be queried to update the underlying value.
   */
  protected <T> void registerValueDeriver(@NotNull Key<T> key, @NotNull ValueDeriver<T> deriver) {
    myValueDerivers.put(key, deriver);
  }

  /**
   * Allows clients of {@link ScopedDataBinder} to use custom JComponents by writing their own
   * setValue and getValue functions to support their custom implementations.
   */
  public static abstract class ComponentBinding<T, C extends JComponent> {
    /**
     * Set the given component to the given value, such that, given ExampleObject e,
     * calling setValue(e, sampleComponent) means that a subsequent call to
     * getValue(sampleComponent) returns e.
     */
    public void setValue(@Nullable T newValue, @NotNull C component) {
      throw new UnsupportedOperationException();
    }

    /**
     * @return the current value of the component.
     */
    @Nullable
    public T getValue(@NotNull C component) {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Connects the given {@link JComponent} to the given key through the given binding
   * and sets a listener to pick up changes that need to trigger validation and UI updates.
   */
  protected <T, C extends JComponent> void register(@NotNull Key<T> key, @NotNull C component,
                              @NotNull ComponentBinding<T, C> binding) {
    T value = bindAndGet(key, component, binding);
    if (value != null) {
      binding.setValue(value, component);
    } else {
      try {
        myState.put(key, binding.getValue(component));
      } catch (UnsupportedOperationException e) {
        // Expected.
      }
    }
  }

  /**
   * This is a default binding implementation for controls that the framework supports directly.
   *
   * @see #getComponentValue(javax.swing.JComponent)
   * @see #setComponentValue(Object, javax.swing.JComponent)
   */
  private class DefaultBinding extends ComponentBinding<Object, JComponent> {
    @Override
    public void setValue(@Nullable Object newValue, @NotNull JComponent component) {
      setComponentValue(newValue, component);
    }

    @Nullable
    @Override
    public Object getValue(@NotNull JComponent component) {
      return getComponentValue(component);
    }
  }

  /**
   * Registers a binding in the table and returns current key value.
   */
  @Nullable
  private <T> T bindAndGet(@NotNull Key<T> key, @NotNull JComponent component, @Nullable ComponentBinding<T, ?> binding) {
    ComponentBinding<?, ?> b = binding == null ? new DefaultBinding() : binding;
    myComponentBindings.put(component, key, b);
    return unwrap(myState.get(key));
  }

  /**
   * Connects the given {@link javax.swing.JCheckBox} to the given key and sets a listener to pick up changes that
   * need to trigger validation and UI updates.
   */
  protected void register(@NotNull Key<Boolean> key, @NotNull JCheckBox checkBox) {
    Boolean value = bindAndGet(key, checkBox, null);
    if (value != null) {
      checkBox.setSelected(value);
    } else {
      myState.put(key, false);
    }
    checkBox.addFocusListener(this);
    checkBox.addItemListener(this);
  }

  /**
   * Connects the given {@link JComboBox} to the given key and sets a listener to pick up changes that need to trigger validation
   * and UI updates.
   */
  protected <T> void register(@NotNull Key<T> key, @NotNull JComboBox comboBox) {
    T value = bindAndGet(key, comboBox, null);
    if (value != null) {
      setSelectedItem(comboBox, value);
    }
    comboBox.addFocusListener(this);
    comboBox.addActionListener(this);
  }

  /**
   * Utility method for setting the selected index of a combo box by value.
   */
  private static <T> void setSelectedItem(JComboBox comboBox, @Nullable T value) {
    for (int i = 0; i < comboBox.getItemCount(); i++) {
      Object item = comboBox.getItemAt(i);
      if (!(item instanceof ComboBoxItem)) {
        continue;
      }
      if (((ComboBoxItem)item).id.equals(value)) {
        comboBox.setSelectedIndex(i);
        break;
      }
    }
  }

  /**
   * Connects the given {@link JTextField} to the given key and sets a listener to pick up changes that need to trigger validation
   * and UI updates.
   */
  protected void register(@NotNull Key<String> key, @NotNull JTextField textField) {
    myDocumentsToComponent.put(textField.getDocument(), textField);
    String value = bindAndGet(key, textField, null);
    if (value != null) {
      textField.setText(value);
    } else {
      myState.put(key, textField.getText());
    }
    textField.addFocusListener(this);
    textField.getDocument().addDocumentListener(this);
  }

  /**
   * Connects the given {@link JRadioButton} to the given key and sets a listener to pick up changes that need to trigger validation
   * and UI updates.
   */
  protected <T> void register(@NotNull Key<T> key, @NotNull JRadioButton radioButton, @Nullable T defaultValue) {
    T currentValue = bindAndGet(key, radioButton, null);
    radioButton.setSelected(currentValue != null && currentValue.equals(defaultValue));
    if (defaultValue != null) {
      myState.put(key, defaultValue);
    }
    radioButton.addFocusListener(this);
    radioButton.addActionListener(this);
  }

  /**
   * Connects the given {@link JSlider} to the given key and sets a listener to pick up changes that need to trigger validation
   * and UI updates.
   */
  protected void register(@NotNull Key<Integer> key, @NotNull JSlider paddingSlider) {
    Integer value = bindAndGet(key, paddingSlider, null);
    if (value != null) {
      paddingSlider.setValue(value);
    } else {
      myState.put(key, paddingSlider.getValue());
    }
    paddingSlider.addFocusListener(this);
    paddingSlider.addChangeListener(this);
  }

  /**
   * Connects the given {@link JSpinner} to the given key and sets a listener to pick up changes that need to trigger validation
   * and UI updates.
   */
  protected void register(@NotNull Key<Object> key, @NotNull JSpinner spinner) {
    Object value = bindAndGet(key, spinner, null);
    if (value != null) {
      spinner.setValue(value);
    } else {
      myState.put(key, spinner.getValue());
    }
    spinner.addFocusListener(this);
    spinner.addChangeListener(this);
  }

  /**
   * Connects the given {@link TextFieldWithBrowseButton} to the given key and sets a listener to pick up
   * changes that need to trigger validation and UI updates.
   */
  protected void register(@NotNull Key<String> key, @NotNull final TextFieldWithBrowseButton field) {
    myDocumentsToComponent.put(field.getTextField().getDocument(), field);
    String value = bindAndGet(key, field, null);
    if (value != null) {
      field.setText(value);
    } else {
      myState.put(key, field.getText());
    }
    field.addFocusListener(this);
    field.getTextField().getDocument().addDocumentListener(this);
    field.getTextField().addFocusListener(this);
  }

  /**
   * Connects the given {@link ColorPanel} to the given key and sets a listener to pick up changes that need to trigger validation
   * and UI updates.
   */
  protected void register(@NotNull Key<Color> key, @NotNull ColorPanel colorPanel) {
    Color value = bindAndGet(key, colorPanel, null);
    if (value != null) {
      colorPanel.setSelectedColor(value);
    } else {
      myState.put(key, colorPanel.getSelectedColor());
    }
    colorPanel.addFocusListener(this);
    colorPanel.addActionListener(this);
  }

  @Override
  public void focusGained(FocusEvent e) {

  }

  @Override
  public void focusLost(FocusEvent e) {

  }

  @Override
  public void stateChanged(ChangeEvent e) {
    saveState((JComponent)e.getSource());
  }


  @Override
  public void actionPerformed(ActionEvent e) {
    saveState((JComponent)e.getSource());
  }

  @Override
  public void itemStateChanged(ItemEvent e) {
    saveState((JComponent)e.getSource());
  }

  @Override
  public void insertUpdate(DocumentEvent e) {
    saveState(myDocumentsToComponent.get(e.getDocument()));
  }

  @Override
  public void removeUpdate(DocumentEvent e) {
    saveState(myDocumentsToComponent.get(e.getDocument()));
  }

  @Override
  public void changedUpdate(DocumentEvent e) {
    // No-op. We only care about insertions and removals.
  }
}
