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
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredItemResourceValue;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.rendering.Locale;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
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

/**
 * Abstract class that implements a {@link JTable} renderer and editor for attributes based on the {@link ResourceComponent} component.
 * This class implements most of the behaviour and handling of things like variants. This class will call
 * {@link #updateComponent(ThemeEditorContext, ResourceComponent, EditedStyleItem)} to allow subclasses to set their own labels
 * and styles on the {@link ResourceComponent}.
 */
public abstract class GraphicalResourceRendererEditor extends TypedCellEditor<EditedStyleItem, String> implements TableCellRenderer {
  static final String CURRENT_VARIANT_TEMPLATE = "<html><nobr><font color=\"#%1$s\">%2$s</font>";
  static final String NOT_SELECTED_VARIANT_TEMPLATE = "<html><nobr><font color=\"#%1$s\">%2$s</font><font color=\"#9B9B9B\"> - %3$s</font>";
  @SuppressWarnings("UseJBColor") // LIGHT_GRAY works also in Darcula
  static final Color CURRENT_VARIANT_COLOR = Color.LIGHT_GRAY;
  static final Color NOT_SELECTED_VARIANT_COLOR = JBColor.BLUE;

  private static final Logger LOG = Logger.getInstance(GraphicalResourceRendererEditor.class);

  protected final ThemeEditorContext myContext;
  protected final ResourceComponent myComponent;
  protected String myEditorValue;
  protected EditedStyleItem myItem;

  public GraphicalResourceRendererEditor(@NotNull ThemeEditorContext context, boolean isEditor) {
    myContext = context;
    myComponent = new ResourceComponent();

    if (isEditor) {
      myComponent.addVariantItemListener(new VariantItemListener());
    }
  }

  private static void updateComponentInternal(@NotNull ResourceComponent component,
                                              final @NotNull EditedStyleItem item) {
    final String currentVariantColor = ColorUtil.toHex(CURRENT_VARIANT_COLOR);
    final String notSelectedVariantColor = ColorUtil.toHex(NOT_SELECTED_VARIANT_COLOR);
    final ImmutableList.Builder<VariantsComboItem> variantsListBuilder = ImmutableList.builder();
    variantsListBuilder.add(new VariantsComboItem(
      String.format(CURRENT_VARIANT_TEMPLATE, currentVariantColor, item.getSelectedValueConfiguration().toShortDisplayString()),
      item.getSelectedValueConfiguration()));
    Iterables.all(item.getNonSelectedItemResourceValues(), new Predicate<ConfiguredItemResourceValue>() {
      @Override
      public boolean apply(@Nullable ConfiguredItemResourceValue input) {
        if (input == null) {
          return false;
        }
        variantsListBuilder.add(new VariantsComboItem(String.format(NOT_SELECTED_VARIANT_TEMPLATE, notSelectedVariantColor,
                                                                    input.getConfiguration().toShortDisplayString(),
                                                                    input.getItemResourceValue().getValue()), input.getConfiguration()));
        return true;
      }
    });

    ImmutableList<VariantsComboItem> variantStrings = variantsListBuilder.build();
    component.setVariantsModel(new CollectionComboBoxModel(variantStrings, variantStrings.get(0)));
  }

  protected abstract void updateComponent(@NotNull ThemeEditorContext context,
                                          @NotNull ResourceComponent component,
                                          @NotNull EditedStyleItem item);

  @Override
  public Component getTableCellRendererComponent(JTable table, Object obj, boolean isSelected, boolean hasFocus, int row, int column) {
    assert obj instanceof EditedStyleItem : "Object passed to GraphicalResourceRendererEditor.getTableCellRendererComponent must be instance of EditedStyleItem";

    updateComponentInternal(myComponent, (EditedStyleItem)obj);
    updateComponent(myContext, myComponent, (EditedStyleItem)obj);

    return myComponent;
  }

  @Override
  public Component getEditorComponent(JTable table, EditedStyleItem value, boolean isSelected, int row, int column) {
    myItem = value;
    updateComponentInternal(myComponent, myItem);
    updateComponent(myContext, myComponent, myItem);
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
      Configuration configuration = myContext.getConfiguration();

      IAndroidTarget oldTarget =  configuration.getTarget();
      Locale oldLocale = configuration.getLocale();

      configuration.getEditedConfig().set(item.myFolderConfiguration);
      configuration.updated(ConfigurationListener.MASK_FOLDERCONFIG);

      // Target and locale are global so we need to set them in the configuration manager when updated
      if (oldTarget != configuration.getTarget()) {
        configuration.getConfigurationManager().setTarget(configuration.getTarget());
      }
      if (oldLocale != configuration.getLocale()) {
        configuration.getConfigurationManager().setLocale(configuration.getLocale());
      }

      GraphicalResourceRendererEditor.this.stopCellEditing();
    }
  }
}
