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
package com.android.tools.idea.editors.theme.attributes.editors;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.editors.theme.ThemeEditorConstants;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.preview.AndroidThemePreviewPanel;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.swing.ui.SwatchComponent;
import org.jetbrains.android.uipreview.ChooseResourceDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * Class that implements a {@link javax.swing.JTable} renderer and editor for color attributes.
 */
public class ColorRendererEditor extends GraphicalResourceRendererEditor {
  static final String LABEL_TEMPLATE = "<html><nobr><b><font color=\"#%1$s\">%2$s</font></b><font color=\"#9B9B9B\"> - %3$s</font>";
  static final String LABEL_EMPTY = "(empty)";

  private final AndroidThemePreviewPanel myPreviewPanel;

  public ColorRendererEditor(@NotNull ThemeEditorContext context, @NotNull AndroidThemePreviewPanel previewPanel, boolean isEditor) {
    super(context, isEditor);

    if (isEditor) {
      myComponent.addActionListener(new ColorEditorActionListener());
    }
    myPreviewPanel = previewPanel;
  }

  @Override
  protected void updateComponent(@NotNull ThemeEditorContext context, @NotNull ResourceComponent component, @NotNull EditedStyleItem item) {
    assert context.getResourceResolver() != null;

    final List<Color> colors = ResourceHelper.resolveMultipleColors(context.getResourceResolver(), item.getItemResourceValue());
    String colorText = colors.isEmpty() ? LABEL_EMPTY : ResourceHelper.colorToString(colors.get(0));
    component.setSwatchIcons(SwatchComponent.colorListOf(colors));
    component.setNameText(String.format(LABEL_TEMPLATE, ThemeEditorConstants.RESOURCE_ITEM_COLOR.toString(), item.getName(), colorText));
    component.setValueText(item.getValue());
  }

  private class ColorEditorActionListener implements ActionListener {
    @Override
    public void actionPerformed(final ActionEvent e) {
      String itemValue = myItem.getValue();
      final String colorName;
      // If it points to an existing resource.
      if (!RenderResources.REFERENCE_EMPTY.equals(itemValue) &&
          !RenderResources.REFERENCE_NULL.equals(itemValue) &&
          itemValue.startsWith(SdkConstants.PREFIX_RESOURCE_REF)) {
        // Use the name of that resource.
        colorName = itemValue.substring(itemValue.indexOf('/') + 1);
      }
      else {
        // Otherwise use the name of the attribute.
        colorName = myItem.getName();
      }

      // TODO we need to handle color state lists correctly here.
      ResourceResolver resourceResolver = myContext.getResourceResolver();
      assert resourceResolver != null;
      String resolvedColor = ResourceHelper.colorToString(ResourceHelper.resolveColor(resourceResolver, myItem.getItemResourceValue()));

      final ChooseResourceDialog dialog =
        new ChooseResourceDialog(myContext.getCurrentThemeModule(), ChooseResourceDialog.COLOR_TYPES, resolvedColor, null,
                                 ChooseResourceDialog.ResourceNameVisibility.FORCE, colorName);

      final String oldValue = myItem.getItemResourceValue().getValue();

      dialog.setResourcePickerListener(new ChooseResourceDialog.ResourcePickerListener() {
        @Override
        public void resourceChanged(final @Nullable String resource) {
          myItem.getItemResourceValue().setValue(resource == null ? oldValue : resource);
          myPreviewPanel.invalidateGraphicsRenderer();
        }
      });

      dialog.show();

      // Restore the old value in the properties model
      myItem.getItemResourceValue().setValue(oldValue);

      myEditorValue = null;
      if (dialog.isOK()) {
        String value = dialog.getResourceName();
        if (value != null) {
          myEditorValue = dialog.getResourceName();
        }
      }
      else {
        // User cancelled, clean up the preview
        myPreviewPanel.invalidateGraphicsRenderer();
      }

      if (myEditorValue == null) {
        ColorRendererEditor.this.cancelCellEditing();
      }
      else {
        ColorRendererEditor.this.stopCellEditing();
      }
    }
  }
}
