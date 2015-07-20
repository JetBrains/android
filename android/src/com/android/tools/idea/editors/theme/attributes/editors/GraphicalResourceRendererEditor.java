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

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredItemResourceValue;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.qualifiers.QualifierUtils;
import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;

/**
 * Abstract class that implements a {@link JTable} renderer and editor for attributes based on the {@link ResourceComponent} component.
 * This class implements most of the behaviour and handling of things like variants. This class will call
 * {@link #updateComponent(ThemeEditorContext, ResourceComponent, EditedStyleItem)} to allow subclasses to set their own labels
 * and styles on the {@link ResourceComponent}.
 */
public abstract class GraphicalResourceRendererEditor extends TypedCellEditor<EditedStyleItem, String> implements TableCellRenderer {
  static final String CURRENT_VARIANT_TEMPLATE = "<html><nobr><font color=\"#%1$s\">%2$s</font>";
  static final String NOT_SELECTED_VARIANT_TEMPLATE = "<html><nobr><b><font color=\"#%1$s\">%2$s</font></b><font color=\"#9B9B9B\"> %3$s</font>";
  @SuppressWarnings("UseJBColor") // LIGHT_GRAY works also in Darcula
  static final Color CURRENT_VARIANT_COLOR = Color.LIGHT_GRAY;
  @SuppressWarnings("UseJBColor")
  static final Color NOT_SELECTED_VARIANT_COLOR = new Color(0x70ABE3);

  private static final Logger LOG = Logger.getInstance(GraphicalResourceRendererEditor.class);

  protected final ThemeEditorContext myContext;
  protected final ResourceComponent myComponent;
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
      myComponent.addVariantItemListener(new VariantItemListener());
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

    for (ConfiguredItemResourceValue configuredItem : item.getAllConfiguredItems()) {
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
    final String currentVariantColor = ColorUtil.toHex(CURRENT_VARIANT_COLOR);
    final String notSelectedVariantColor = ColorUtil.toHex(NOT_SELECTED_VARIANT_COLOR);
    final ImmutableList.Builder<VariantsComboItem> variantsListBuilder = ImmutableList.builder();
    FolderConfiguration restrictedConfig = restrictConfiguration(manager, item, item.getSelectedValueConfiguration());
    variantsListBuilder.add(new VariantsComboItem(
      String.format(CURRENT_VARIANT_TEMPLATE, currentVariantColor, item.getSelectedValueConfiguration().toShortDisplayString()),
      restrictedConfig != null ? restrictedConfig : item.getSelectedValueConfiguration()));

    for (ConfiguredItemResourceValue configuredItem : item.getNonSelectedItemResourceValues()) {
      restrictedConfig = restrictConfiguration(context.getConfiguration().getConfigurationManager(), item, configuredItem.getConfiguration());

      if (restrictedConfig == null) {
        // This type is not visible
        LOG.warn(String.format(
          "For item '%1$s': Folder configuration '%2$s' can never be selected. There are no qualifiers combination that would allow selecting it.",
          item.getName(), configuredItem.getConfiguration()));
        continue;
      }

      String value = configuredItem.getItemResourceValue() != null ? " - " + configuredItem.getItemResourceValue().getValue() : "";
      variantsListBuilder.add(new VariantsComboItem(String.format(NOT_SELECTED_VARIANT_TEMPLATE, notSelectedVariantColor,
                                                                  configuredItem.getConfiguration().toShortDisplayString(),
                                                                  value), restrictedConfig));
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

  /**
   * Class that wraps the display text of a variant and the folder configuration that represents.
   */
  private static class VariantsComboItem {
    final String myLabel;
    final FolderConfiguration myFolderConfiguration;

    VariantsComboItem(@NotNull String label, @NotNull FolderConfiguration folderConfiguration) {
      myLabel = label;
      myFolderConfiguration = folderConfiguration;
    }

    @Override
    public String toString() {
      return myLabel;
    }
  }

  private class VariantItemListener implements ItemListener {
    @Override
    public void itemStateChanged(ItemEvent e) {
      if (e.getStateChange() != ItemEvent.SELECTED) {
        return;
      }

      VariantsComboItem item = (VariantsComboItem)e.getItem();
      Configuration oldConfiguration = myContext.getConfiguration();
      ConfigurationManager manager = oldConfiguration.getConfigurationManager();
      Configuration newConfiguration = Configuration.create(manager, null, null, item.myFolderConfiguration);

      // Target and locale are global so we need to set them in the configuration manager when updated
      VersionQualifier newVersionQualifier = item.myFolderConfiguration.getVersionQualifier();
      if (newVersionQualifier != null) {
        IAndroidTarget realTarget = manager.getHighestApiTarget() != null ? manager.getHighestApiTarget() : manager.getTarget();
        manager.setTarget(new CompatibilityRenderTarget(realTarget, newVersionQualifier.getVersion(), null));
      } else {
        manager.setTarget(null);
      }

      LocaleQualifier newLocaleQualifier = item.myFolderConfiguration.getLocaleQualifier();
      manager.setLocale(newLocaleQualifier != null ? Locale.create(newLocaleQualifier) : Locale.ANY);

      oldConfiguration.setDevice(null, false);
      Configuration.copyCompatible(newConfiguration, oldConfiguration);
      oldConfiguration.updated(ConfigurationListener.MASK_FOLDERCONFIG);

      GraphicalResourceRendererEditor.this.stopCellEditing();
    }
  }
}
