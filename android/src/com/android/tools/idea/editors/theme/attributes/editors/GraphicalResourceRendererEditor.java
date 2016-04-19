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

import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.tools.idea.editors.theme.*;
import com.android.tools.idea.editors.theme.attributes.AttributesTableModel;
import com.android.tools.idea.editors.theme.attributes.variants.VariantItemListener;
import com.android.tools.idea.editors.theme.attributes.variants.VariantsComboItem;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredElement;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.editors.theme.preview.AndroidThemePreviewPanel;
import com.android.tools.idea.editors.theme.qualifiers.RestrictedConfiguration;
import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.editors.theme.ui.VariantsComboBox;
import com.android.tools.idea.res.ResourceHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ColorUtil;
import org.jetbrains.android.facet.AndroidFacet;
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

/**
 * Abstract class that implements a {@link JTable} renderer and editor for attributes based on the {@link ResourceComponent} component.
 * This class implements most of the behaviour and handling of things like variants. This class will call
 * {@link #updateComponent(ThemeEditorContext, ResourceComponent, EditedStyleItem)} to allow subclasses to set their own labels
 * and styles on the {@link ResourceComponent}.
 */
public abstract class GraphicalResourceRendererEditor extends TypedCellRendererEditor<EditedStyleItem, String> {
  public static final ResourceType[] COLORS_ONLY = {ResourceType.COLOR};
  public static final ResourceType[] DRAWABLES_ONLY = {ResourceType.DRAWABLE, ResourceType.MIPMAP};
  public static final ResourceType[] COLORS_AND_DRAWABLES = {ResourceType.COLOR, ResourceType.DRAWABLE, ResourceType.MIPMAP};
  static final String DUMB_MODE_MESSAGE = "Editing theme is not possible - indexing is in progress";

  private static final Logger LOG = Logger.getInstance(GraphicalResourceRendererEditor.class);
  private static final Comparator<VariantsComboItem> VARIANTS_COMBO_ITEM_COMPARATOR = new Comparator<VariantsComboItem>() {
    @Override
    public int compare(VariantsComboItem o1, VariantsComboItem o2) {
      FolderConfiguration o1FolderConfiguration = o1.getOriginalConfiguration();
      FolderConfiguration o2FolderConfiguration = o2.getOriginalConfiguration();

      if (o1FolderConfiguration.isDefault() && !o2FolderConfiguration.isDefault()) {
        return -1;
      }
      if (o2FolderConfiguration.isDefault() && !o1FolderConfiguration.isDefault()) {
        return 1;
      }
      return o1FolderConfiguration.toShortDisplayString().compareTo(o2FolderConfiguration.toShortDisplayString());
    }
  };

  protected final ThemeEditorContext myContext;
  protected final ResourceComponent myComponent;
  protected final AndroidThemePreviewPanel myPreviewPanel;
  protected AttributesTableModel myModel;
  protected EditedStyleItem myItem;

  public GraphicalResourceRendererEditor(@NotNull ThemeEditorContext context,
                                         @NotNull AndroidThemePreviewPanel previewPanel,
                                         boolean isEditor) {
    myContext = context;
    // Override isShowing because of the use of a {@link CellRendererPane}
    myComponent = new ResourceComponent(context.getProject(), isEditor) {
      @Override
      public boolean isShowing() {
        return true;
      }
    };

    if (isEditor) {
      myComponent.addVariantItemListener(new VariantItemListener(context));
      myComponent.addVariantItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          stopCellEditing();
        }
      });
      myComponent.addVariantPopupClosingListener(new VariantsComboBox.PopupClosingListener() {
        @Override
        public void popupClosed() {
          stopCellEditing();
        }
      });
      myComponent.addSwatchListener(new EditorClickListener());
      myComponent.addTextListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          stopCellEditing();
        }
      });
      myComponent.addTextFocusListener(new FocusListener() {
        @Override
        public void focusGained(FocusEvent e) {
          DumbService dumbService = DumbService.getInstance(myContext.getProject());
          if (dumbService.isDumb()) {
            dumbService.showDumbModeNotification(DUMB_MODE_MESSAGE);
            cancelCellEditing();
          }
          else {
            AndroidFacet facet = AndroidFacet.getInstance(myContext.getCurrentContextModule());
            assert facet != null;
            myComponent.setCompletionStrings(ResourceHelper.getCompletionFromTypes(facet, getAllowedResourceTypes()));
          }
        }

        @Override
        public void focusLost(FocusEvent e) {
          cancelCellEditing();
        }
      });
    }

    myPreviewPanel = previewPanel;
  }

  /**
   * Sets the UI state of the passed {@link ResourceComponent} based on the given {@link EditedStyleItem}
   */
  private static void updateComponentInternal(@NotNull ResourceComponent component,
                                              final @NotNull EditedStyleItem item) {
    final String currentVariantColor = ColorUtil.toHex(ThemeEditorConstants.CURRENT_VARIANT_COLOR);
    final String notSelectedVariantColor = ColorUtil.toHex(ThemeEditorConstants.NOT_SELECTED_VARIANT_COLOR);

    FolderConfiguration restrictedConfig = RestrictedConfiguration.restrict(item.getSelectedItemResourceValue(),
                                                                          item.getAllConfiguredItems());

    String description = String.format(ThemeEditorConstants.CURRENT_VARIANT_TEMPLATE, currentVariantColor,
                                       item.getSelectedValueConfiguration().toShortDisplayString());
    VariantsComboItem selectedItem =
      new VariantsComboItem(description, restrictedConfig != null ? restrictedConfig : item.getSelectedValueConfiguration(), item.getSelectedValueConfiguration());

    // All the not selected elements are sorted alphabetically
    TreeSet<VariantsComboItem> notSelectedItems = Sets.newTreeSet(VARIANTS_COMBO_ITEM_COMPARATOR);
    for (ConfiguredElement<ItemResourceValue> configuredItem : item.getNonSelectedItemResourceValues()) {
      restrictedConfig = RestrictedConfiguration.restrict(configuredItem, item.getAllConfiguredItems());

      if (restrictedConfig == null) {
        // This type is not visible
        LOG.warn(String.format(
          "For item '%1$s': Folder configuration '%2$s' can never be selected. There are no qualifiers combination that would allow selecting it.",
          item.getName(), configuredItem.getConfiguration()));
        continue;
      }

      description = String.format(ThemeEditorConstants.NOT_SELECTED_VARIANT_TEMPLATE, notSelectedVariantColor,
                                  configuredItem.getConfiguration().toShortDisplayString(), " - " + configuredItem.getElement().getValue());
      notSelectedItems.add(new VariantsComboItem(description, restrictedConfig, configuredItem.getConfiguration()));
    }

    ImmutableList<VariantsComboItem> variantList = ImmutableList.<VariantsComboItem>builder()
      .add(selectedItem)
      .addAll(notSelectedItems)
      .build();
    component.setVariantsModel(new CollectionComboBoxModel<VariantsComboItem>(variantList, selectedItem));
  }

  protected abstract void updateComponent(@NotNull ThemeEditorContext context,
                                          @NotNull ResourceComponent component,
                                          @NotNull EditedStyleItem item);

  @Override
  public Component getRendererComponent(JTable table, EditedStyleItem obj, boolean isSelected, boolean hasFocus, int row, int column) {
    myItem = obj;

    myComponent.setSize(table.getCellRect(row, column, false).getSize());
    Font font = table.getFont();
    myComponent.setFont(ThemeEditorUtils.scaleFontForAttribute(font));
    updateComponentInternal(myComponent, obj);
    updateComponent(myContext, myComponent, obj);

    return myComponent;
  }

  @Override
  public Component getEditorComponent(final JTable table, EditedStyleItem value, boolean isSelected, final int row, final int column) {
    myModel = (AttributesTableModel)table.getModel();
    myItem = value;

    myComponent.setSize(table.getCellRect(row, column, false).getSize());
    Font font = table.getFont();
    myComponent.setFont(ThemeEditorUtils.scaleFontForAttribute(font));
    updateComponentInternal(myComponent, value);
    updateComponent(myContext, myComponent, value);

    return myComponent;
  }

  @Override
  public String getEditorValue() {
    return myComponent.getValueText();
  }

  /**
   * Returns the allowed resource types for the attribute being edited
   */
  @NotNull
  protected abstract ResourceType[] getAllowedResourceTypes();

  private class EditorClickListener extends DumbAwareActionListener {
    public EditorClickListener() {
      super(myContext.getProject());
    }

    @Override
    public void dumbActionPerformed(ActionEvent e) {
      DumbService.getInstance(myContext.getProject()).showDumbModeNotification(DUMB_MODE_MESSAGE);
      GraphicalResourceRendererEditor.this.cancelCellEditing();
    }

    @Override
    public void smartActionPerformed(ActionEvent e) {
      ConfiguredThemeEditorStyle style = myModel.getSelectedStyle();
      ResourceResolver styleResourceResolver = myContext.getResourceResolver();

      assert styleResourceResolver != null;

      ItemResourceValue primaryColorResourceValue =
        ThemeEditorUtils.resolveItemFromParents(style, MaterialColors.PRIMARY_MATERIAL_ATTR, !ThemeEditorUtils.isAppCompatTheme(style));

      final Project project = myContext.getProject();
      Color primaryColor = ResourceHelper.resolveColor(styleResourceResolver, primaryColorResourceValue, project);

      ChooseResourceDialog dialog = ThemeEditorUtils.getResourceDialog(myItem, myContext, getAllowedResourceTypes());

      String attributeName = myItem.getName();
      if (primaryColor != null) {
        dialog.generateColorSuggestions(primaryColor, attributeName);
      }

      ImmutableMap<String, Color> contrastColorsWithDescription = ColorUtils.getContrastColorsWithDescription(myContext, attributeName);
      if (!contrastColorsWithDescription.isEmpty()) {
        dialog.setContrastParameters(contrastColorsWithDescription, ColorUtils.isBackgroundAttribute(attributeName), true);
      }

      final String oldValue = myItem.getSelectedValue().getValue();

      dialog.setResourcePickerListener(new ChooseResourceDialog.ResourcePickerListener() {
        @Override
        public void resourceChanged(final @Nullable String resource) {
          if (resource != null) {
            ResourceResolver resourceResolver = myContext.getResourceResolver();
            assert resourceResolver != null;

            ResourceValue resValue = resourceResolver.findResValue(resource, false);
            String resolvedResource = resource;
            if (resValue != null && resValue.getResourceType() == ResourceType.COLOR && !resValue.getValue().endsWith(".xml")) {
              // resValue ending in ".xml" signifies a color state list, in which case we do not want to resolve it further
              // resolveColor applied to a state list would pick only one color, while the preview will deal with it correctly
              Color color = ResourceHelper.resolveColor(resourceResolver, resValue, project);
              if (color != null) { // color may be null if the user has entered a color that points to an invalid color, (user error)
                resolvedResource = ResourceHelper.colorToString(color);
              }
            }
            myItem.getSelectedValue().setValue(resolvedResource);
          }
          else {
            myItem.getSelectedValue().setValue(oldValue);
          }
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

      String editorValue = null;
      if (dialog.isOK()) {
        editorValue = dialog.getResourceName();
      }
      else {
        // User cancelled, clean up the preview
        myPreviewPanel.invalidateGraphicsRenderer();
      }

      if (editorValue == null) {
        GraphicalResourceRendererEditor.this.cancelCellEditing();
      }
      else {
        myComponent.setValueText(editorValue);
        GraphicalResourceRendererEditor.this.stopCellEditing();
      }
    }
  }
}