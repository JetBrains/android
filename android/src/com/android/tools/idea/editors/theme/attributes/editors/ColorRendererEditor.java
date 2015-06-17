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

import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.editors.theme.*;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.attributes.AttributesTableModel;
import com.android.tools.idea.editors.theme.MaterialColors;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.android.tools.idea.editors.theme.preview.AndroidThemePreviewPanel;
import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.swing.ui.SwatchComponent;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.ui.JBMenuItem;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import java.awt.Component;
import javax.swing.JTable;
import org.jetbrains.android.uipreview.ChooseResourceDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Set;

/**
 * Class that implements a {@link javax.swing.JTable} renderer and editor for color attributes.
 */
public class ColorRendererEditor extends GraphicalResourceRendererEditor {
  public static final ResourceType[] COLORS_ONLY = {ResourceType.COLOR};
  public static final ResourceType[] DRAWABLES_ONLY = {ResourceType.DRAWABLE, ResourceType.MIPMAP};
  public static final ResourceType[] COLORS_AND_DRAWABLES = {ResourceType.COLOR, ResourceType.DRAWABLE, ResourceType.MIPMAP};

  private AttributesTableModel myModel;

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

    final List<Color> colors = ResourceHelper.resolveMultipleColors(context.getResourceResolver(), item.getSelectedValue(),
                                                                    context.getProject());
    component.setSwatchIcons(SwatchComponent.colorListOf(colors));
    component.setNameText(item.getQualifiedName());
    component.setValueText(item.getValue());

    Set<String> lowContrastColors = ColorUtils.getLowContrastColors(context, item);
    if (!lowContrastColors.isEmpty()) {
      component.setWarningVisible(true);
      // Using html for the tooltip because the color names are bold
      // Formatted color names are concatenated into an error message
      StringBuilder contrastErrorMessageBuilder = new StringBuilder("<html>Not enough contrast with ");
      int i = 0;
      for (String color : lowContrastColors) {
        contrastErrorMessageBuilder.append(color);
        if (i < lowContrastColors.size() - 2) {
          contrastErrorMessageBuilder.append(", ");
        }
        else if (i == lowContrastColors.size() - 2) {
          contrastErrorMessageBuilder.append(" and ");
        }
        i++;
      }
      myComponent.setWarning(contrastErrorMessageBuilder.toString());
    }
    else {
      component.setWarningVisible(false);
    }
  }

  @Override
  public Component getEditorComponent(JTable table, EditedStyleItem value, boolean isSelected, int row, int column) {
    myModel = (AttributesTableModel)table.getModel();
    return super.getEditorComponent(table, value, isSelected, row, column);
  }

  private class ColorEditorActionListener extends DumbAwareActionListener {
    public ColorEditorActionListener() {
      super(myContext.getProject());
    }

    @Override
    public void dumbActionPerformed(ActionEvent e) {
      DumbService.getInstance(myProject).showDumbModeNotification(DUMB_MODE_MESSAGE);
      ColorRendererEditor.this.cancelCellEditing();
    }

    @Override
    public void smartActionPerformed(ActionEvent e) {
      AttributeDefinition attrDefinition = ResolutionUtils.getAttributeDefinition(myContext.getConfiguration(), myItem.getSelectedValue());

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

      ThemeEditorStyle style = myModel.getSelectedStyle();
      ResourceResolver styleResourceResolver = myContext.getConfiguration().getResourceResolver();

      assert styleResourceResolver != null;

      Color primaryColor = ResourceHelper.resolveColor(styleResourceResolver, ThemeEditorUtils
        .resolveItemFromParents(style, MaterialColors.PRIMARY_MATERIAL_ATTR, !ThemeEditorUtils.isAppCompatTheme(style)),
                                                       myContext.getProject());

      ChooseResourceDialog dialog = ThemeEditorUtils.getResourceDialog(myItem, myContext, allowedTypes);
      if (primaryColor != null) {
        dialog.generateColorSuggestions(primaryColor, myItem.getName());
      }

      final String oldValue = myItem.getSelectedValue().getValue();

      dialog.setResourcePickerListener(new ChooseResourceDialog.ResourcePickerListener() {
        @Override
        public void resourceChanged(final @Nullable String resource) {
          myItem.getSelectedValue().setValue(resource == null ? oldValue : resource);
          myPreviewPanel.invalidateGraphicsRenderer();
        }
      });

      if (e.getSource() instanceof JBMenuItem) {
        // This has been triggered from the "Add variations" menu option so display location settings
        dialog.openLocationSettings();
      }

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