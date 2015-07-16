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

import com.android.resources.ResourceType;
import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.android.tools.idea.editors.theme.ThemeEditorConstants;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.preview.AndroidThemePreviewPanel;
import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.swing.ui.SwatchComponent;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
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
  public static final ResourceType[] COLORS_ONLY = {ResourceType.COLOR};
  public static final ResourceType[] DRAWABLES_ONLY = {ResourceType.DRAWABLE, ResourceType.MIPMAP};
  public static final ResourceType[] COLORS_AND_DRAWABLES = {ResourceType.COLOR, ResourceType.DRAWABLE, ResourceType.MIPMAP};

  private final AndroidThemePreviewPanel myPreviewPanel;
  private EditedStyleItem myItem;

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

    myItem = item;

    final List<Color> colors = ResourceHelper.resolveMultipleColors(context.getResourceResolver(), item.getSelectedValue(),
                                                                    context.getProject());
    String colorText = colors.isEmpty() ? LABEL_EMPTY : ResourceHelper.colorToString(colors.get(0));
    component.setSwatchIcons(SwatchComponent.colorListOf(colors));
    component.setNameText(String.format(LABEL_TEMPLATE, ThemeEditorConstants.RESOURCE_ITEM_COLOR.toString(), item.getName(), colorText));
    component.setValueText(item.getValue());
  }

  private class ColorEditorActionListener implements ActionListener {
    @Override
    public void actionPerformed(final ActionEvent e) {
      AttributeDefinition attrDefinition =
        ResolutionUtils.getAttributeDefinition(myContext.getConfiguration(), myItem.getSelectedValue());

      ResourceType[] allowedTypes;
      String attributeName = myItem.getName().toLowerCase();
      if (attributeName.contains("color") || !ThemeEditorUtils.acceptsFormat(attrDefinition, AttributeFormat.Reference)) {
        allowedTypes = COLORS_ONLY;
      }
      else if (attributeName.contains("drawable") || !ThemeEditorUtils.acceptsFormat(attrDefinition, AttributeFormat.Color)) {
        allowedTypes = DRAWABLES_ONLY;
      }
      else {
        allowedTypes = COLORS_AND_DRAWABLES;
      }

      ChooseResourceDialog dialog = ThemeEditorUtils.getResourceDialog(myItem, myContext, allowedTypes);

      final String oldValue = myItem.getSelectedValue().getValue();

      dialog.setResourcePickerListener(new ChooseResourceDialog.ResourcePickerListener() {
        @Override
        public void resourceChanged(final @Nullable String resource) {
          myItem.getSelectedValue().setValue(resource == null ? oldValue : resource);
          myPreviewPanel.invalidateGraphicsRenderer();
        }
      });

      dialog.show();

      // Restore the old value in the properties model
      myItem.getSelectedValue().setValue(oldValue);

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
