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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.ui.ApiComboBoxItem;
import com.android.tools.idea.ui.DocumentAccessor;
import com.android.tools.idea.ui.TextAccessors;
import com.google.common.base.Objects;
import com.google.common.collect.*;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.TextAccessor;
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

import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope;

/**
 * A data binding class that links Swing UI elements to a {@link ScopedStateStore}.
 * Provides data bindings between Swing JComponents of various types and a ScopedStateStore.
 * Components may be registered in a 2-way binding by calling
 * {@link #register(ScopedStateStore.Key, javax.swing.JCheckBox)}.
 * Once registered, any change to the state store will trigger an update of the UI, and any update of the UI will automatically
 * enter the value into the state store.
 *
 * <h3>Built in bindings</h3>
 * The ScopedDataBinder provides built-in bindings for:
 * <ul>
 *   <li>JTextField</li>
 *   <li>JComboBox</li>
 *   <li>JRadioButton</li>
 *   <li>JCheckbox</li>
 *   <li>JSlider</li>
 *   <li>JSpinner</li>
 *   <li>TextFieldWithBrowseButton (from IntelliJPlatform)</li>
 *   <li>ColorPanel</li>
 *   <li>JLabel</li>
 * </ul>
 *
 * Additional components (including custom built components) that inherit from JComponent can be registered by providing an
 * implementation of the {@link ComponentBinding} class which allows clients to provide custom getValue/setValue functions for an
 * additional component type.
 *
 * <h3>Value Derivation</h3>
 * One of the primary advantages of the databinding system provided by TemplateWizardStep is the derivation step that it provides in
 * the update process. Values can depend on other values and receive automatic updates when their dependencies changed.
 * The ScopedDataBinder provides this through the registration of {@link ValueDeriver}s. These derivers provide a filterset of keys which
 * they subscribe to, and a function that will be called on each matching update.
 *
 * For example, the following ValueDeriver will be invoked whenever the value associated with the keys “operand1” or “operand2” is changed
 * in the state store, and the value returned by deriveValue() will be stored in the state store for the key “result”
 * <p>
 * <pre>
 * {@code
 * registerValueDeriver(DERIVED_KEY, new ValueDeriver <String>() {
 *   @Nullable
 *   @Override
 *   public Set<Key<?>> getTriggerKeys() {
 *     return makeSetOf(OPERAND_1_KEY, OPERAND_2_KEY);
 *   }
 *
 *   @NotNull
 *   @Override
 *   public String deriveValue(ScopedStateStore state, Key changedKey, @Nullable String currentValue) {
 *     return state.get(OPERAND_1_KEY) + state.get(OPERAND_2_KEY);
 *   }
 * });
 * }
 * </pre>
 * Value derivation cannot be cyclical. Each key may only be touched once per update cycle.
 */
public class ScopedDataBinder implements ScopedStateStore.ScopedStoreListener, FocusListener, ChangeListener, ActionListener,
                                         DocumentListener, ItemListener {
  // State store
  // TODO: Temporary change. Set to private in a followup CL!
  @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
  public ScopedStateStore myState;

  // Mapping documents to components.
  private Map<Document, JComponent> myDocumentsToComponent = Maps.newIdentityHashMap();

  // Map of keys to custom value derivations
  private Map<Key, ValueDeriver> myValueDerivers = Maps.newHashMap();

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
  public <T> void invokeUpdate(@Nullable Key<T> changedKey) {
    if (changedKey == null) {
      // Protect from concurrent modification failures
      Set<Key<?>> allKeys = ImmutableSet.copyOf(myComponentBindings.columnKeySet());
      myGuardedKeys.addAll(allKeys);
      for (Key<?> key : allKeys) {
        if (myComponentBindings.containsColumn(key)) {
          internalUpdateKey(key);
        }
      }
      deriveValues(null);
    }
    else {
      myGuardedKeys.add(changedKey);
      internalUpdateKey(changedKey);
    }
  }

  /**
   * Unconditionally updates all bindings for a given key.
   */
  @SuppressWarnings("unchecked")
  private <T> void internalUpdateKey(@NotNull Key<T> changedKey) {
    // Update the UI value if the user has not caused this state change
    Object value = myState.get(changedKey);
    // We need to protect from concurrent binding modifications that may happen as a result of the update
    Map<JComponent, ComponentBinding<?, ?>> keyBindings =
        ImmutableMap.copyOf(myComponentBindings.column(changedKey));
    for (Map.Entry<JComponent, ComponentBinding<?, ?>> entry : keyBindings.entrySet()) {
      JComponent component = entry.getKey();
      if (myComponentBindings.contains(component, changedKey) &&
          !Objects.equal(component, myUpdateTrigger)) {
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

    deriveValues(changedKey);
    myGuardedKeys.remove(changedKey);
  }

  private <T> void deriveValues(@Nullable Key<T> changedKey) {
    // Loop over our value derivers and call them if necessary
    for (Key key : myValueDerivers.keySet()) {
      // Don't derive values that have already been updated this round
      if (!myGuardedKeys.contains(key)) {
        ValueDeriver deriver = myValueDerivers.get(key);
        // Don't overwrite values that the user has manually entered
        if (myUserEditedKeys.contains(key) && deriver.respectUserEdits()) {
          continue;
        }
        Set<Key<?>> triggerKeys = deriver.getTriggerKeys();
        // Respect the deriver's filter of triggers
        if (changedKey != null && triggerKeys != null && !triggerKeys.contains(changedKey)) {
          continue;
        }
        deriveValue(key, deriver, changedKey);
      }
    }
  }

  /**
   * Use the given deriver to update the value associated with the given key.
   */
  private <T> void deriveValue(@NotNull Key<T> key, @NotNull ValueDeriver<T> deriver, @Nullable Key<?> changedKey) {
    T currentValue = myState.get(key);
    T newValue = deriver.deriveValue(myState, changedKey, currentValue);
    // Catch issues missed by the compiler due to erasure
    if (newValue != null && !key.expectedClass.isInstance(newValue)) {
      throw new IllegalArgumentException(String.format("Deriver %1$s returned value for key %2$s of type %3$s, should be %4$s",
                                                       deriver.toString(), key.name, newValue.getClass().getName(),
                                                       key.expectedClass.getName()));
    }
    myState.put(key, newValue);
  }

  /**
   * Triggered by a UI change. Will persist the change to the state store.
   */
  @VisibleForTesting
  @SuppressWarnings("unchecked")
  public <T> void saveState(@NotNull JComponent component) {
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
    T oldValue = myState.get(key);
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
      Object selectedObject = ((JComboBox)component).getSelectedItem();
      if (selectedObject instanceof ApiComboBoxItem) {
        ApiComboBoxItem selectedItem = (ApiComboBoxItem)selectedObject;
        newValue = selectedItem.getData();
      } else {
        newValue = selectedObject;
      }
    } else if (component instanceof JSlider) {
      newValue = ((JSlider)component).getValue();
    } else if (component instanceof JSpinner) {
      newValue = ((JSpinner)component).getValue();
    } else if (component instanceof ColorPanel) {
      newValue = ((ColorPanel)component).getSelectedColor();
    } else {
      TextAccessor accessor = TextAccessors.getTextAccessor(component);
      if (accessor != null) {
        newValue = accessor.getText();
      }
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
    } else if (component instanceof JSlider) {
      assert value != null;
      ((JSlider)component).setValue((Integer)value);
    } else if (component instanceof JSpinner) {
      ((JSpinner)component).setValue(value);
    } else if (component instanceof ColorPanel && value != null) {
      ((ColorPanel)component).setSelectedColor((Color)value);
    } else {
      TextAccessor accessor = TextAccessors.getTextAccessor(component);
      if (accessor != null) {
        String newValue = StringUtil.notNullize((String)value);
        if (!newValue.equals(accessor.getText())) {
          accessor.setText(newValue);
        }
      }
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
    public abstract T deriveValue(@NotNull ScopedStateStore state, @Nullable Key changedKey, @Nullable T currentValue);
  }

  /**
   * Connects the given {@link ValueDeriver} to the given key. Whenever an update is triggered, the given deriver will
   * be queried to update the underlying value.
   */
  public <T> void registerValueDeriver(@NotNull Key<T> key, @NotNull ValueDeriver<T> deriver) {
    myValueDerivers.put(key, deriver);
  }

  protected <T> void unregisterValueDeriver(@NotNull Key<T> key) {
    myValueDerivers.remove(key);
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

    /**
     * Attach an action listener to the underlying component.
     */
    public void addActionListener(@NotNull ActionListener listener, @NotNull C component) {

    }

    /**
     * Attach a change listener to the underlying component.
     */
    public void addChangeListener(@NotNull ChangeListener listener, @NotNull C component) {

    }

    /**
     * Attach an item listener to the underlying component.
     */
    public void addItemListener(@NotNull ItemListener listener, @NotNull C component) {

    }

    /**
     * @return the Document object of the underlying component or null if no such document exists.
     */
    @Nullable
    public Document getDocument(@NotNull C component) {
      return null;
    }
  }

  /**
   * Connects the given {@link JComponent} to the given key through the given binding
   * and sets a listener to pick up changes that need to trigger validation and UI updates.
   */
  public <T, C extends JComponent> void register(@NotNull Key<T> key, @NotNull C component,
                        @NotNull ComponentBinding<T, ? super C> binding) {
    T value = bindAndGet(key, component, binding);
    if (value != null) {
      try {
        binding.setValue(value, component);
      } catch (UnsupportedOperationException e) {
        // Expected.
      }
    } else {
      try {
        myState.put(key, binding.getValue(component));
      } catch (UnsupportedOperationException e) {
        // Expected.
      }
    }
    component.addFocusListener(this);
    binding.addActionListener(this, component);
    binding.addChangeListener(this, component);
    binding.addItemListener(this, component);
    Document document = binding.getDocument(component);
    if (document != null) {
      myDocumentsToComponent.put(document, component);
      document.addDocumentListener(this);
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
    return myState.get(key);
  }

  /**
   * Connects the given {@link javax.swing.JCheckBox} to the given key and sets a listener to pick up changes that
   * need to trigger validation and UI updates.
   */
  public void register(@NotNull Key<Boolean> key, @NotNull JCheckBox checkBox) {
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
  public static <T> void setSelectedItem(JComboBox comboBox, @Nullable T value) {
    int index = -1;
    for (int i = 0; i < comboBox.getItemCount(); i++) {
      Object item = comboBox.getItemAt(i);
      if (item instanceof ApiComboBoxItem) {
        item = ((ApiComboBoxItem)item).getData();
      }
      if (Objects.equal(item, value)) {
        index = i;
        break;
      }
    }
    comboBox.setSelectedIndex(index);
  }

  /**
   * Connects the given {@link JTextField} to the given key and sets a listener to pick up changes that need to trigger validation
   * and UI updates.
   */
  public void register(@NotNull Key<String> key, @NotNull JTextField textField) {
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

  public void register(@NotNull Key<String> key, @NotNull JLabel label) {
    String value = bindAndGet(key, label, null);
    if (value != null) {
      label.setText(value);
    } else {
      myState.put(key, label.getText());
    }
  }

  /**
   * Connects the given {@link JSlider} to the given key and sets a listener to pick up changes that need to trigger validation
   * and UI updates.
   */
  public void register(@NotNull Key<Integer> key, @NotNull JSlider paddingSlider) {
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
  public void register(@NotNull Key<Object> key, @NotNull JSpinner spinner) {
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
  public void register(@NotNull Key<String> key, @NotNull final TextFieldWithBrowseButton field) {
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

  public void register(@NotNull Key<String> key, @NotNull final DocumentAccessor field) {
    assert field instanceof JComponent;

    JComponent component = (JComponent)field;
    String value = bindAndGet(key, component, null);
    if (value != null) {
      field.setText(value);
    } else {
      myState.put(key, field.getText());
    }
    component.addFocusListener(this);
    myDocumentsToComponent.put(field.getDocument(), component);
    field.getDocument().addDocumentListener(this);
  }

  /**
   * Connects the given {@link ColorPanel} to the given key and sets a listener to pick up changes that need to trigger validation
   * and UI updates.
   */
  public void register(@NotNull Key<Color> key, @NotNull ColorPanel colorPanel) {
    Color value = bindAndGet(key, colorPanel, null);
    if (value != null) {
      colorPanel.setSelectedColor(value);
    } else {
      myState.put(key, colorPanel.getSelectedColor());
    }
    colorPanel.addFocusListener(this);
    colorPanel.addActionListener(this);
  }

  /**
   * Removes all component bindings and listeners.
   */
  protected void deregister(JComponent component) {
    if (myComponentBindings.rowMap().remove(component) != null) {
      component.removeFocusListener(this);
      if (component instanceof JCheckBox) {
        ((JCheckBox)component).removeItemListener(this);
      }
      else if (component instanceof JComboBox) {
        ((JComboBox)component).removeActionListener(this);
      }
      else if (component instanceof JTextField) {
        ((JTextField)component).getDocument().removeDocumentListener(this);
      }
      else if (component instanceof JRadioButton) {
        ((JRadioButton)component).removeActionListener(this);
      }
      else if (component instanceof JSlider) {
        ((JSlider)component).removeChangeListener(this);
      }
      else if (component instanceof JSpinner) {
        ((JSpinner)component).removeChangeListener(this);
      }
      else if (component instanceof TextFieldWithBrowseButton) {
        ((TextFieldWithBrowseButton)component).getTextField().getDocument().removeDocumentListener(this);
        ((TextFieldWithBrowseButton)component).getTextField().removeFocusListener(this);
      }
      else if (component instanceof ColorPanel) {
        ((ColorPanel)component).removeActionListener(this);
      }
    }
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
