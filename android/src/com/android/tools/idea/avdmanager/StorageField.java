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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.devices.Storage;
import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.core.ObjectProperty;
import com.android.tools.idea.ui.properties.core.ObjectValueProperty;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.EnumComboBoxModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.text.DecimalFormat;

import static com.android.sdklib.devices.Storage.Unit;

/**
 * Storage field for displaying and editing a {@link Storage} value
 */
public class StorageField extends JPanel {

  private final Unit DEFAULT_UNIT = Unit.MiB;
  private final ComboBoxModel unitModel = new EnumComboBoxModel<Unit>(Unit.class);

  private final ComboBox myUnitsCombo = new ComboBox(unitModel);
  private final JTextField myValueField = new JTextField();

  private Unit myCurrentUnit = DEFAULT_UNIT;

  private ObjectProperty<Storage> myStorage = new ObjectValueProperty<Storage>(new Storage(0, DEFAULT_UNIT));
  public ObjectProperty<Storage> storage() {
    return myStorage;
  }

  public final Dimension getPreferredSizeOfUnitsDropdown() {
    return myUnitsCombo.getPreferredSize();
  }

  public StorageField() {
    super();
    setLayout(new BorderLayout(3, 0));

    add(myValueField, BorderLayout.CENTER);
    add(myUnitsCombo, BorderLayout.EAST);

    myUnitsCombo.setSelectedItem(DEFAULT_UNIT);

    myUnitsCombo.setRenderer(new ColoredListCellRenderer<Unit>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, Unit value, int index, boolean selected, boolean hasFocus) {
        append(value.getDisplayValue());
      }
    });

    myUnitsCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myCurrentUnit = (Unit)myUnitsCombo.getSelectedItem();
        String value = new DecimalFormat("0.####").format(myStorage.get().getPreciseSizeAsUnit(myCurrentUnit));
        myValueField.setText(value);
      }
    });

    myValueField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        updateStorage();
      }
    });

    storage().addListener(new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        String value = new DecimalFormat("0.####").format(myStorage.get().getPreciseSizeAsUnit(myCurrentUnit));
        myValueField.setText(value);
      }
    });
  }

  private void updateStorage() {
    String text = myValueField.getText();
    Storage storage;

    if (text != null) {
      try {
        Double valueAsUnits = Double.parseDouble(text);
        storage = new Storage(valueAsUnits.longValue(), myCurrentUnit);
      }
      catch (NumberFormatException ex) {
        storage = new Storage(0, DEFAULT_UNIT);
        myValueField.setText("0");
        myUnitsCombo.setSelectedItem(DEFAULT_UNIT);
      }
      myStorage.set(storage);
    }
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myUnitsCombo.setEnabled(enabled);
    myValueField.setEnabled(enabled);
  }
}
