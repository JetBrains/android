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
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.editors.theme.ThemeEditorConstants;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.attributes.variants.VariantItemListener;
import com.android.tools.idea.editors.theme.attributes.variants.VariantsComboItem;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredElement;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.android.tools.idea.editors.theme.qualifiers.QualifierUtils;
import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ColorUtil;
import org.jetbrains.android.actions.CreateXmlResourceDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Abstract class that implements a {@link JTable} renderer and editor for attributes based on the {@link ResourceComponent} component.
 * This class implements most of the behaviour and handling of things like variants. This class will call
 * {@link #updateComponent(ThemeEditorContext, ResourceComponent, EditedStyleItem)} to allow subclasses to set their own labels
 * and styles on the {@link ResourceComponent}.
 */
public abstract class GraphicalResourceRendererEditor extends TypedCellEditor<EditedStyleItem, String> implements TableCellRenderer {
  static final String DUMB_MODE_MESSAGE = "Editing theme is not possible - indexing is in progress";

  private static final Logger LOG = Logger.getInstance(GraphicalResourceRendererEditor.class);

  protected final ThemeEditorContext myContext;
  protected final ResourceComponent myComponent;
  protected static EditedStyleItem myItem;
  protected String myEditorValue;

  public GraphicalResourceRendererEditor(@NotNull ThemeEditorContext context, boolean isEditor) {
    myContext = context;
    // Override isShowing because of the use of a {@link CellRendererPane}
    myComponent = new ResourceComponent() {
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
    }
  }

  /**
   * Returns a restricted version of the passed configuration. The value returned will incompatible with any other configuration in the item.
   * This configuration can be used when we want to make sure that the configuration selected will be displayed.
   * <p/>
   * This method can return null if there is no configuration that matches the constraints.
   */
  @Nullable
  static FolderConfiguration restrictConfiguration(@NotNull ConfigurationManager manager, @NotNull EditedStyleItem item, @NotNull final FolderConfiguration compatibleConfiguration) {
    ArrayList<FolderConfiguration> incompatibleConfigurations = Lists.newArrayListWithCapacity(
      item.getNonSelectedItemResourceValues().size() + 1);

    for (ConfiguredElement<ItemResourceValue> configuredItem : item.getAllConfiguredItems()) {
      FolderConfiguration configuration = configuredItem.getConfiguration();
      if (configuration == compatibleConfiguration) {
        continue;
      }

      incompatibleConfigurations.add(configuration);
    }

    return QualifierUtils.restrictConfiguration(manager, compatibleConfiguration, incompatibleConfigurations);
  }

  /**
   * Sets the UI state of the passed {@link ResourceComponent} based on the given {@link EditedStyleItem}
   */
  private static void updateComponentInternal(final @NotNull ThemeEditorContext context,
                                              @NotNull ResourceComponent component,
                                              final @NotNull EditedStyleItem item) {
    final ConfigurationManager manager = context.getConfiguration().getConfigurationManager();
    final String currentVariantColor = ColorUtil.toHex(ThemeEditorConstants.CURRENT_VARIANT_COLOR);
    final String notSelectedVariantColor = ColorUtil.toHex(ThemeEditorConstants.NOT_SELECTED_VARIANT_COLOR);
    final ImmutableList.Builder<VariantsComboItem> variantsListBuilder = ImmutableList.builder();

    myItem = item;

    FolderConfiguration restrictedConfig = restrictConfiguration(manager, item, item.getSelectedValueConfiguration());
    variantsListBuilder.add(new VariantsComboItem(
      String.format(ThemeEditorConstants.CURRENT_VARIANT_TEMPLATE, currentVariantColor, item.getSelectedValueConfiguration().toShortDisplayString()),
      restrictedConfig != null ? restrictedConfig : item.getSelectedValueConfiguration()));

    for (ConfiguredElement<ItemResourceValue> configuredItem : item.getNonSelectedItemResourceValues()) {
      restrictedConfig = restrictConfiguration(context.getConfiguration().getConfigurationManager(), item, configuredItem.getConfiguration());

      if (restrictedConfig == null) {
        // This type is not visible
        LOG.warn(String.format(
          "For item '%1$s': Folder configuration '%2$s' can never be selected. There are no qualifiers combination that would allow selecting it.",
          item.getName(), configuredItem.getConfiguration()));
        continue;
      }

      variantsListBuilder.add(new VariantsComboItem(String
                                                      .format(ThemeEditorConstants.NOT_SELECTED_VARIANT_TEMPLATE, notSelectedVariantColor,
                                                              configuredItem.getConfiguration().toShortDisplayString(),
                                                              " - " + configuredItem.getElement().getValue()), restrictedConfig));
    }

    ImmutableList<VariantsComboItem> variantStrings = variantsListBuilder.build();
    component.setVariantsModel(new CollectionComboBoxModel(variantStrings, variantStrings.get(0)));
  }

  protected abstract void updateComponent(@NotNull ThemeEditorContext context,
                                          @NotNull ResourceComponent component,
                                          @NotNull EditedStyleItem item);

  @Override
  public Component getTableCellRendererComponent(JTable table, Object obj, boolean isSelected, boolean hasFocus, int row, int column) {
    assert obj instanceof EditedStyleItem : "Object passed to GraphicalResourceRendererEditor.getTableCellRendererComponent must be instance of EditedStyleItem";

    updateComponentInternal(myContext, myComponent, (EditedStyleItem)obj);
    updateComponent(myContext, myComponent, (EditedStyleItem)obj);

    return myComponent;
  }

  @Override
  public Component getEditorComponent(JTable table, EditedStyleItem value, boolean isSelected, int row, int column) {
    updateComponentInternal(myContext, myComponent, value);
    updateComponent(myContext, myComponent, value);
    myEditorValue = null; // invalidate stored editor value

    return myComponent;
  }

  @Override
  public String getEditorValue() {
    return myEditorValue;
  }

}
