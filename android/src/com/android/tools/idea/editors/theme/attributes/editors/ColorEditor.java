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
package com.android.tools.idea.editors.theme.attributes.editors;

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.AndroidThemePreviewPanel;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.rendering.ResourceHelper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.util.ui.AbstractTableCellEditor;
import org.jetbrains.android.uipreview.ChooseResourceDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class ColorEditor extends AbstractTableCellEditor {
  private static final Logger LOG = Logger.getInstance(ColorEditor.class);

  private final Module myModule;
  private final Configuration myConfiguration;

  private final ColorComponent myComponent;
  private ColorInfo myEditorValue = null;

  private final AndroidThemePreviewPanel myPreviewPanel;

  private EditedStyleItem myItem;

  public ColorEditor(@NotNull Module module, @NotNull Configuration configuration, AndroidThemePreviewPanel previewPanel) {
    myModule = module;
    myConfiguration = configuration;

    myComponent = new ColorComponent();
    myComponent.addActionListener(new ColorEditorActionListener());

    myPreviewPanel = previewPanel;
  }

  public static class ColorInfo {
    private final @NotNull String myResourceValue;
    private final boolean myForceReload;

    @NotNull
    public String getResourceValue() {
      return myResourceValue;
    }

    public boolean isForceReload() {
      return myForceReload;
    }

    public ColorInfo(@NotNull String resourceValue, boolean forceReload) {
      myResourceValue = resourceValue;
      myForceReload = forceReload;
    }

    public static ColorInfo of(@NotNull String resourceValue, boolean forceReload) {
      return new ColorInfo(resourceValue, forceReload);
    }
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    if (value instanceof EditedStyleItem) {
      myItem = (EditedStyleItem) value;
      final List<Color> colors = ResourceHelper.resolveMultipleColors(myConfiguration.getResourceResolver(), myItem.getItemResourceValue());
      myComponent.configure(myItem, colors);
    } else {
      LOG.error(String.format("Object passed to ColorRendererEditor has class %1$s instead of ItemResourceValueWrapper", value.getClass().getName()));
    }
    myEditorValue = null; // invalidate stored editor value

    return myComponent;
  }

  @Override
  public Object getCellEditorValue() {
    return myEditorValue;
  }

  private class ColorEditorActionListener implements ActionListener {
    @Override
    public void actionPerformed(final ActionEvent e) {
      final ChooseResourceDialog dialog =
        new ChooseResourceDialog(myModule, ChooseResourceDialog.COLOR_TYPES, myComponent.getValue(), null, ChooseResourceDialog.ResourceNameVisibility.FORCE);

      final String oldValue = myItem.getItemResourceValue().getValue();

      dialog.setResourcePickerListener(new ChooseResourceDialog.ResourcePickerListener() {
        @Override
        public void resourceChanged(@Nullable String resource) {
          myItem.getItemResourceValue().setValue(resource == null ? oldValue : resource);
          myPreviewPanel.invalidateGraphicsRenderer();
        }
      });

      dialog.show();

      // Dialog has been closed, clean up
      myItem.getItemResourceValue().setValue(oldValue);
      myPreviewPanel.invalidateGraphicsRenderer();

      myEditorValue = null;
      if (dialog.isOK()) {
        String value = dialog.getResourceName();
        if (value != null) {
          myEditorValue = ColorInfo.of(dialog.getResourceName(), dialog.overwriteResource());
        }
      }

      if (myEditorValue == null) {
        ColorEditor.this.cancelCellEditing();
      } else {
        ColorEditor.this.stopCellEditing();
      }
    }
  }
}
