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
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.ThemeSelectionDialog;
import com.android.tools.idea.configurations.ThemeSelectionPanel;
import com.android.tools.idea.editors.theme.ParentThemesListModel;
import com.android.tools.idea.editors.theme.ThemeEditorConstants;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.attributes.variants.VariantItemListener;
import com.android.tools.idea.editors.theme.attributes.variants.VariantsComboItem;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredElement;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.editors.theme.qualifiers.RestrictedConfiguration;
import com.android.tools.idea.editors.theme.ui.VariantsComboBox;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ColorUtil;
import org.jetbrains.android.actions.CreateXmlResourceDialog;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Custom Renderer and Editor for the theme parent attribute.
 * Uses a dropdown to offer the choice between Material Dark, Material Light or Other.
 * Deals with Other through a separate dialog window.
 */
public class ParentRendererEditor extends TypedCellRendererEditor<ConfiguredThemeEditorStyle, String> {
  private static final Logger LOG = Logger.getInstance(ParentRendererEditor.class);

  public static final String NO_PARENT = "[no parent]";
  private static final CollectionComboBoxModel NO_PARENT_MODEL = new CollectionComboBoxModel<String>(ImmutableList.of(NO_PARENT), NO_PARENT);

  private final JComboBox myParentComboBox;
  private final VariantsComboBox myVariantsComboBox;
  private @Nullable String myResultValue;
  private final ThemeEditorContext myContext;
  private final JPanel myPanel;
  private final ThemeParentChangedListener myThemeParentChangedListener;
  private ConfiguredThemeEditorStyle myItem;
  private final JLabel myLabel;

  public interface ThemeParentChangedListener extends ThemeSelectionPanel.ThemeChangedListener {
    /**
     * Returns the theme editor to its state before the theme parent was changed
     */
    void reset();
  }

  public ParentRendererEditor(@NotNull ThemeEditorContext context, @NotNull ThemeParentChangedListener themeParentChangedListener) {
    myContext = context;
    myThemeParentChangedListener = themeParentChangedListener;
    myParentComboBox = new JComboBox();
    // Override isShowing because of the use of a {@link CellRendererPane}
    myPanel = new JPanel(new BorderLayout(0, ThemeEditorConstants.ATTRIBUTE_ROW_GAP)) {
      @Override
      public boolean isShowing() {
        return true;
      }
    };
    myPanel.setBorder(
      BorderFactory.createEmptyBorder(ThemeEditorConstants.ATTRIBUTE_MARGIN / 2, 0, ThemeEditorConstants.ATTRIBUTE_MARGIN / 2, 0));

    myVariantsComboBox = new VariantsComboBox();
    myVariantsComboBox.addItemListener(new VariantItemListener(context));
    myVariantsComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        stopCellEditing();
      }
    });
    myVariantsComboBox.addPopupClosingListener(new VariantsComboBox.PopupClosingListener() {
      @Override
      public void popupClosed() {
        stopCellEditing();
      }
    });

    myParentComboBox.setRenderer(new StyleListCellRenderer(context, myParentComboBox));
    myParentComboBox.addActionListener(new ParentChoiceListener());
    myParentComboBox.setMinimumSize(ThemeEditorConstants.ATTRIBUTES_PANEL_COMBO_MIN_SIZE);

    JPanel topLine = new JPanel(new BorderLayout());
    myLabel = new JLabel(String.format(ThemeEditorConstants.ATTRIBUTE_LABEL_TEMPLATE,
                                            ColorUtil.toHex(ThemeEditorConstants.RESOURCE_ITEM_COLOR), "Theme parent"));
    topLine.add(myLabel, BorderLayout.WEST);
    topLine.add(myVariantsComboBox, BorderLayout.EAST);

    myPanel.add(topLine, BorderLayout.PAGE_START);
    myPanel.add(myParentComboBox, BorderLayout.CENTER);

    myVariantsComboBox.addAction(new AbstractAction("Add variation") {
      @Override
      public boolean isEnabled() {
        return myItem != null && myItem.isProjectStyle();
      }

      @Override
      public void actionPerformed(ActionEvent e) {
        final ConfiguredThemeEditorStyle sourceStyle = myItem;
        String themeName = sourceStyle.getName();
        Module module = myContext.getCurrentContextModule();

        CreateXmlResourceDialog
          resourceDialog = new CreateXmlResourceDialog(module, ResourceType.STYLE, themeName, " ", false);
        resourceDialog.setTitle(String.format("Create Theme '%1$s' Variant", themeName));
        if (!resourceDialog.showAndGet()) {
          return;
        }

        String fileName = resourceDialog.getFileName();
        List<String> dirNames = resourceDialog.getDirNames();
        String resName = resourceDialog.getResourceName();
        String parentName = myItem.getParent().getQualifiedName();
        Project project = module.getProject();
        VirtualFile resourceDir = resourceDialog.getResourceDirectory();
        if (resourceDir == null) {
          AndroidUtils.reportError(project, AndroidBundle.message("check.resource.dir.error", module.getName()));
          return;
        }

        ThemeEditorUtils.createNewStyle(project, resourceDir, resName, parentName, fileName, dirNames);
      }
    });

    ThemeEditorUtils.setInheritsPopupMenuRecursive(myPanel);
  }

  private void updateVariantsCombo() {
    if (myItem == null) {
      myVariantsComboBox.setVisible(false);
      return;
    }
    myVariantsComboBox.setVisible(true);

    Collection<ConfiguredElement<String>> allParents = myItem.getParentNames();
    final String currentVariantColor = ColorUtil.toHex(ThemeEditorConstants.CURRENT_VARIANT_COLOR);
    final String notSelectedVariantColor = ColorUtil.toHex(ThemeEditorConstants.NOT_SELECTED_VARIANT_COLOR);
    final ArrayList<VariantsComboItem> variants = Lists.newArrayListWithCapacity(allParents.size());

    ConfiguredThemeEditorStyle currentParent = myItem.getParent(myContext.getThemeResolver());

    ConfiguredElement<String> selectedElement = null;
    if (currentParent != null) {
      //noinspection unchecked
      selectedElement = (ConfiguredElement<String>)currentParent.getConfiguration().getFullConfig()
        .findMatchingConfigurable(ImmutableList.copyOf(allParents));
    }
    if (selectedElement == null) {
      selectedElement = allParents.iterator().next();
    }

    for (ConfiguredElement<String> configuredParent : allParents) {
      FolderConfiguration restrictedConfig = RestrictedConfiguration.restrict(configuredParent, allParents);
      String parentName = configuredParent.getElement();

      if (restrictedConfig == null) {
        // This type is not visible
        LOG.warn(String.format(
          "For style '%1$s': Folder configuration '%2$s' can never be selected. There are no qualifiers combination that would allow selecting it.",
          parentName, configuredParent.getConfiguration()));
        continue;
      }

      if (configuredParent.getConfiguration().equals(selectedElement.getConfiguration())) {
        // This is the selected parent
        variants.add(0, new VariantsComboItem(String.format(ThemeEditorConstants.CURRENT_VARIANT_TEMPLATE, currentVariantColor,
                                                            configuredParent.getConfiguration().toShortDisplayString()), restrictedConfig,
                                              configuredParent.getConfiguration()));
      }
      else {
        variants.add(new VariantsComboItem(String.format(ThemeEditorConstants.NOT_SELECTED_VARIANT_TEMPLATE, notSelectedVariantColor,
                                                         configuredParent.getConfiguration().toShortDisplayString(), " - " + parentName),
                                           restrictedConfig,
                                           configuredParent.getConfiguration()));
      }
    }

    myVariantsComboBox.setModel(new CollectionComboBoxModel<VariantsComboItem>(variants, variants.get(0)));
  }

  @Override
  public Component getRendererComponent(JTable table, ConfiguredThemeEditorStyle value, boolean isSelected, boolean hasFocus, int row, int column) {
    final TableModel model = table.getModel();
    final ConfiguredThemeEditorStyle parent = value.getParent();

    Font font = table.getFont();
    Font scaledFont = ThemeEditorUtils.scaleFontForAttribute(font);
    myParentComboBox.setFont(scaledFont);
    myLabel.setFont(scaledFont);
    myParentComboBox.setEnabled(model.isCellEditable(row, column));

    if (parent == null) {
      myParentComboBox.setModel(NO_PARENT_MODEL);
      myItem = null;
    }
    else {
      ImmutableList<String> defaultThemeNames = ThemeEditorUtils.getDefaultThemeNames(myContext.getThemeResolver());
      myParentComboBox.setModel(new ParentThemesListModel(defaultThemeNames, parent.getQualifiedName()));
      myItem = value;
    }
    updateVariantsCombo();

    return myPanel;
  }

  @Override
  public Component getEditorComponent(JTable table, ConfiguredThemeEditorStyle value, boolean isSelected, int row, int column) {
    Font font = table.getFont();
    Font scaledFont = ThemeEditorUtils.scaleFontForAttribute(font);
    myParentComboBox.setFont(scaledFont);
    myLabel.setFont(scaledFont);

    ConfiguredThemeEditorStyle parent = value.getParent();
    ImmutableList<String> defaultThemeNames = ThemeEditorUtils.getDefaultThemeNames(myContext.getThemeResolver());
    myParentComboBox.setModel(new ParentThemesListModel(defaultThemeNames, parent.getQualifiedName()));
    myResultValue = parent.getQualifiedName();
    myItem = value;
    updateVariantsCombo();
    return myPanel;
  }

  @Override
  public String getEditorValue() {
    return myResultValue;
  }

  private class ParentChoiceListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
      String selectedValue = (String)myParentComboBox.getSelectedItem();
      if (ParentThemesListModel.SHOW_ALL_THEMES.equals(selectedValue)) {
        myParentComboBox.hidePopup();

        ConfiguredThemeEditorStyle currentTheme = myContext.getCurrentTheme();
        assert currentTheme != null;

        final ThemeSelectionDialog dialog =
          new ThemeSelectionDialog(myContext.getConfiguration(), Collections.singleton(currentTheme.getQualifiedName()));
        dialog.setThemeChangedListener(myThemeParentChangedListener);
        dialog.show();

        myThemeParentChangedListener.reset();

        if (dialog.isOK()) {
          String theme = dialog.getTheme();
          myResultValue = theme == null ? null : theme;
          stopCellEditing();
        }
        else {
          myResultValue = null;
          cancelCellEditing();
        }
      }
      else {
        myResultValue = selectedValue;
        stopCellEditing();
      }
    }
  }
}
