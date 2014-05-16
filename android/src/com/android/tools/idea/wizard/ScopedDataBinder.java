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
import com.google.common.collect.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ColorPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

import static com.android.tools.idea.wizard.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope;
import static com.android.tools.idea.wizard.ScopedStateStore.unwrap;

/**
 * A data binding class that links Swing UI elements to a {@link ScopedStateStore}.
 */
public class ScopedDataBinder implements ScopedStateStore.ScopedStoreListener, FocusListener, ChangeListener, ActionListener,
                                         DocumentListener, ItemListener {
  private static final Logger LOG = Logger.getInstance(ScopedDataBinder.class);

  // State store
  protected ScopedStateStore myState;

  // Mapping of keys to UI Components. Must be 1 to 1
  private Map<Key, JComponent> myComponents = Maps.newHashMap();

  // Mapping of UI components to keys. May be 1 to many
  private Multimap<Document, Key> myDocumentsToKeys = ArrayListMultimap.create();
  private Multimap<JComponent, Key> myComponentsToKeys = ArrayListMultimap.create();

  // Map of keys to custom value derivations
  private BiMap<Key, ValueDeriver> myValueDerivers = HashBiMap.create();

  // Map of keys to custom component bindings
  private BiMap<Key, ComponentBinding> myComponentBindings = HashBiMap.create();

  // Record of keys that have already been changed or updated during a round to prevent
  // recursive derivations.
  private Set<Key> myGuardedKeys = Sets.newHashSet();

  // Record of keys that the user has manually edited
  private Set<Key> myUserEditedKeys = Sets.newHashSet();

  // Flags to guard against cyclical updates
  private boolean myAlreadySavingState;
  private boolean myChangeComingFromUI;

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
      if (myComponents.containsKey(changedKey) && !myChangeComingFromUI) {
        ComponentBinding binding = myComponentBindings.get(changedKey);
        JComponent component = myComponents.get(changedKey);
        if (binding != null) {
          try {
            binding.setValue(value, component);
          } catch (UnsupportedOperationException e) {
            // This binding is not two-way, and does not support setting the value.
            // Nothing to do here.
          }
        } else {
          setComponentValue(value, component);
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
  private <T> void deriveValue(Key<T> key, ValueDeriver<T> deriver, Key changedKey) {
    Pair<T, Key<T>> scopedPair = myState.get(key);
    T newValue = deriver.deriveValue(myState, changedKey, scopedPair.first);
    myState.put(key, newValue);
  }

  /**
   * Triggered by a UI change. Will persist the change to the state store.
   */
  @VisibleForTesting
  @SuppressWarnings("unchecked")
  protected <T> void saveState(@Nullable Object source) {
    if (myAlreadySavingState) {
      return;
    }
    T value;
    myAlreadySavingState = true;
    if (source instanceof JComponent) {
      // Update all keys associated with this component
      for (Key<T> key : myComponentsToKeys.get((JComponent)source)) {
        if (myGuardedKeys.contains(key)) {
          continue;
        }
        ComponentBinding<T, JComponent> binding = myComponentBindings.get(key);
        if (binding != null) {
          try {
            value = binding.getValue((JComponent)source);
          } catch (UnsupportedOperationException e) {
            // This binding must be one-way, not supporting getting the value
            // In this case, there is no update to do here
            continue;
          }
        } else {
          value = (T)getComponentValue((JComponent)source);
        }
        storeValue(key, value);
      }
    } else if (source instanceof Document) {
      // Update all keys associated with this document
      for (Key<T> key : myDocumentsToKeys.get((Document)source)) {
        if (myGuardedKeys.contains(key)) {
          continue;
        }
        try {
          value = (T)((Document)source).getText(0, ((Document)source).getLength());
        }
        catch (BadLocationException e) {
          LOG.error("Malformed Document registered for " + key);
          throw new RuntimeException(e);
        }
        storeValue(key, value);
      }
    } else {
      throw new IllegalArgumentException("Invalid UI Object: " + source);
    }
    myAlreadySavingState = false;
  }

  /**
   * Store the given element in the state store without invoking another update round.
   */
  private <T> void storeValue(@NotNull Key<T> key, @Nullable T value) {
    T oldValue = unwrap(myState.get(key));
    // If the incoming value is new (not already reflected in the model) it must be from a user edit.
    if (oldValue != null && !oldValue.equals(value) && !myGuardedKeys.contains(key)) {
      myUserEditedKeys.add(key);
    }
    myChangeComingFromUI = true;
    myState.put(key, value);
    myChangeComingFromUI = false;
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
  protected void setComponentValue(@NotNull Object value, @NotNull JComponent component) {
    if (component instanceof JCheckBox) {
      ((JCheckBox)component).setSelected((Boolean)value);
    } else if (component instanceof JComboBox) {
      setSelectedItem((JComboBox)component, value);
    } else if (component instanceof JTextField) {
      ((JTextField)component).setText((String)value);
    } else if (component instanceof TextFieldWithBrowseButton) {
      ((TextFieldWithBrowseButton)component).setText((String)value);
    } else if (component instanceof JSlider) {
      ((JSlider)component).setValue((Integer)value);
    } else if (component instanceof JSpinner) {
      ((JSpinner)component).setValue(value);
    } else if (component instanceof ColorPanel) {
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
    public Set<Key> getTriggerKeys() {
      return null;
    }

    @Nullable
    protected static Set<Key> makeSetOf(Key... elements) {
      Set<Key> keys = new HashSet<Key>(elements.length);
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
  public  static abstract class ComponentBinding<T, C extends JComponent> {
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
    myComponents.put(key, component);
    myComponentsToKeys.put(component, key);
    T value = unwrap(myState.get(key));
    if (value != null) {
      binding.setValue(value, component);
    } else {
      myState.put(key, binding.getValue(component));
    }
  }

  /**
   * Connects the given {@link javax.swing.JCheckBox} to the given key and sets a listener to pick up changes that
   * need to trigger validation and UI updates.
   */
  protected void register(@NotNull Key<Boolean> key, @NotNull JCheckBox checkBox) {
    myComponents.put(key, checkBox);
    myComponentsToKeys.put(checkBox, key);
    Boolean value = unwrap(myState.get(key));
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
    myComponents.put(key, comboBox);
    myComponentsToKeys.put(comboBox, key);
    T value = unwrap(myState.get(key));
    if (value != null) {
      setSelectedItem(comboBox, value);
    }
    comboBox.addFocusListener(this);
    comboBox.addActionListener(this);
  }

  /**
   * Utility method for setting the selected index of a combo box by value.
   */
  private static <T> void setSelectedItem(JComboBox comboBox, T value) {
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
    myComponents.put(key, textField);
    myComponentsToKeys.put(textField, key);
    myDocumentsToKeys.put(textField.getDocument(), key);
    String value = unwrap(myState.get(key));
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
    myComponents.put(key, radioButton);
    myComponentsToKeys.put(radioButton, key);
    T currentValue = unwrap(myState.get(key));
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
    myComponents.put(key, paddingSlider);
    myComponentsToKeys.put(paddingSlider, key);
    Integer value = unwrap(myState.get(key));
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
    myComponents.put(key, spinner);
    myComponentsToKeys.put(spinner, key);
    Object value = unwrap(myState.get(key));
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
    myComponents.put(key, field);
    myComponentsToKeys.put(field, key);
    myDocumentsToKeys.put(field.getTextField().getDocument(), key);
    String value = unwrap(myState.get(key));
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
    myComponents.put(key, colorPanel);
    myComponentsToKeys.put(colorPanel, key);
    Color value = unwrap(myState.get(key));
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
    saveState(e.getSource());
  }


  @Override
  public void actionPerformed(ActionEvent e) {
    saveState(e.getSource());
  }

  @Override
  public void itemStateChanged(ItemEvent e) {
    saveState(e.getSource());
  }

  @Override
  public void insertUpdate(DocumentEvent e) {
    saveState(e.getDocument());
  }

  @Override
  public void removeUpdate(DocumentEvent e) {
    saveState(e.getDocument());
  }

  @Override
  public void changedUpdate(DocumentEvent e) {
    // No-op. We only care about insertions and removals.
  }
}
