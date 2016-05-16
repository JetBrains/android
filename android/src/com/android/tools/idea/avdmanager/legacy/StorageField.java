/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.avdmanager.legacy;

import com.android.sdklib.devices.Storage;
import com.android.tools.idea.ui.properties.core.OptionalProperty;
import com.android.tools.idea.wizard.dynamic.ScopedDataBinder;
import com.google.common.base.Optional;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import static com.android.sdklib.devices.Storage.Unit;

/**
 * Storage field for displaying and editing a {@link Storage} value
 * @deprecated Replaced by {@link com.android.tools.idea.avdmanager.StorageField}
 */
@Deprecated
public class StorageField extends JPanel {
  public static final Unit DEFAULT_UNIT = Unit.MiB;

  private final ComboBoxModel unitModel = new EnumComboBoxModel<Unit>(Unit.class);

  private final ComboBox myUnitsCombo = new ComboBox(unitModel);
  private final JTextField myValueField = new JTextField();

  private static final LineBorder ERROR_BORDER = new LineBorder(JBColor.RED);
  private final Border myBorder;

  private long myBytes = 0l;

  private Unit myCurrentUnit = DEFAULT_UNIT;
  private boolean myIgnoreUpdates;

  public StorageField() {
    super();
    setLayout(new BorderLayout(3, 0));

    add(myValueField, BorderLayout.CENTER);
    add(myUnitsCombo, BorderLayout.EAST);

    myUnitsCombo.setRenderer(new ColoredListCellRenderer<Unit>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, Unit value, int index, boolean selected, boolean hasFocus) {
        append(value.getDisplayValue());
      }
    });

    myUnitsCombo.setSelectedItem(DEFAULT_UNIT);
    ItemListener unitChangeListener = new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myCurrentUnit = (Unit)myUnitsCombo.getSelectedItem();
        Storage value = new Storage(myBytes);
        myIgnoreUpdates = true;
        myValueField.setText(Long.toString(value.getSizeAsUnit(myCurrentUnit)));
        myIgnoreUpdates = false;

      }
    };
    myUnitsCombo.addItemListener(unitChangeListener);
    myBorder = myValueField.getBorder();
  }

  private void updateBytes() {
    if (myIgnoreUpdates) {
      return;
    }
    String text = myValueField.getText();
    myBytes = 0;
    if (text != null) {
      try {
        Long valueAsUnits = Long.parseLong(text);
        Storage value = new Storage(valueAsUnits, myCurrentUnit);
        myBytes = value.getSize();
      } catch (NumberFormatException e) {
        // Pass
      }
    }
  }

  @Nullable
  private Storage getCurrentValue() {
    if (myBytes <= 0) {
      return null;
    }
    return new Storage(myBytes);
  }

  public ComboBox getUnitsCombo() {
    return myUnitsCombo;
  }

  public Dimension getPreferredSizeOfUnitsDropdown() {
    return myUnitsCombo.getPreferredSize();
  }

  public ScopedDataBinder.ComponentBinding<Storage, StorageField> getBinding() {
    return myBinding;
  }

  private final ScopedDataBinder.ComponentBinding<Storage, StorageField> myBinding =
      new ScopedDataBinder.ComponentBinding<Storage, StorageField>() {
    @Override
    public void setValue(@Nullable Storage newValue, @NotNull StorageField component) {
      if (newValue != null) {
        Unit unit = newValue.getAppropriateUnits();
        String newText = Long.toString(newValue.getSizeAsUnit(unit));
        if (!component.myValueField.getText().equals(newText)) {
          component.myValueField.setText(newText);
        }
        component.myUnitsCombo.setSelectedItem(unit);
        myBytes = newValue.getSize();
        myCurrentUnit = unit;
      }
    }

    @Nullable
    @Override
    public Storage getValue(@NotNull StorageField component) {
      updateBytes();
      return getCurrentValue();
    }

    @Override
    public void addItemListener(@NotNull ItemListener listener, @NotNull StorageField component) {
      component.myUnitsCombo.addItemListener(listener);
    }

    @Nullable
    @Override
    public Document getDocument(@NotNull StorageField component) {
      return component.myValueField.getDocument();
    }
  };

  public void setError(boolean hasError) {
    if (hasError) {
      myValueField.setBorder(ERROR_BORDER);
    } else {
      myValueField.setBorder(myBorder);
    }
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myUnitsCombo.setEnabled(enabled);
    myValueField.setEnabled(enabled);
  }

  public final class StorageProperty extends OptionalProperty<Storage> implements ItemListener {
    @NotNull private StorageField myStorageField;

    public StorageProperty(@NotNull StorageField storageField) {
      myStorageField = storageField;
      myStorageField.getUnitsCombo().addItemListener(this);
    }

    @Override
    public void itemStateChanged(ItemEvent e) {

    }

    @Override
    protected void setDirectly(@NotNull Optional<Storage> value) {

    }

    @NotNull
    @Override
    public Optional<Storage> get() {
      return null;
    }
  }
}
