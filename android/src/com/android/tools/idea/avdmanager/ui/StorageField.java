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
package com.android.tools.idea.avdmanager.ui;

import com.android.sdklib.devices.Storage;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.SimpleListCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;

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

  private final ObjectProperty<Storage> myStorage = new ObjectValueProperty<>(new Storage(0, DEFAULT_UNIT));
  public ObjectProperty<Storage> storage() {
    return myStorage;
  }

  public final Dimension getPreferredSizeOfUnitsDropdown() {
    return myUnitsCombo.getPreferredSize();
  }

  public StorageField() {
    super();
    setLayout(new BorderLayout(3, 0));

    updateStorageField();

    add(myValueField, BorderLayout.CENTER);
    add(myUnitsCombo, BorderLayout.EAST);

    myUnitsCombo.setSelectedItem(DEFAULT_UNIT);

    myUnitsCombo.setRenderer(SimpleListCellRenderer.create("", Unit::getDisplayValue));

    myValueField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        // updateStorage might set myValueField as a side effect, which will cause this document
        // to throw an exception. Side-step that problem by invoking the call to happen later.
        ApplicationManager.getApplication().invokeLater(() -> updateStorage());
      }
    });

    myUnitsCombo.addActionListener(e -> updateStorageField());
    myStorage.addListener(() -> updateStorageField());
  }

  private void updateStorageField() {
    myCurrentUnit = (Unit)myUnitsCombo.getSelectedItem();
    String newText = Long.toString(myStorage.get().getSizeAsUnit(myCurrentUnit));
    String oldText = myValueField.getText();
    if (!newText.equals(oldText)) {
      // This puts the cursor at the end. Don't call it unless it's necessary.
      myValueField.setText(newText);
    }
  }

  private void updateStorage() {
    String text = myValueField.getText();
    if (text == null || text.isEmpty()) {
      myStorage.set(new Storage(0, myCurrentUnit));
    }
    else {
      try {
        long newValue = Long.parseLong(text);
        myStorage.set(new Storage(newValue, myCurrentUnit));
      }
      catch (NumberFormatException ex) {
        long oldValue = myStorage.get().getSizeAsUnit(myCurrentUnit);
        myValueField.setText(Long.toString(oldValue));
      }
    }
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myUnitsCombo.setEnabled(enabled);
    myValueField.setEnabled(enabled);
  }
}
