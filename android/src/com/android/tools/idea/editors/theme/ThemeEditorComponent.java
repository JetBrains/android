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
package com.android.tools.idea.editors.theme;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.configurations.DeviceMenuAction;
import com.android.tools.idea.editors.theme.attributes.AttributesTableModel;
import com.android.tools.idea.editors.theme.attributes.TableLabel;
import com.android.tools.idea.editors.theme.attributes.editors.*;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Processor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.dom.drawable.DrawableDomElement;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Style;
import org.jetbrains.android.dom.resources.StyleItem;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;
import java.util.List;

public class ThemeEditorComponent extends Splitter {
  private static final Logger LOG = Logger.getInstance(ThemeEditorComponent.class);
  private static final Font HEADER_FONT = UIUtil.getTitledBorderFont().deriveFont(20.0f);

  private static final int PROPERTIES_DEFAULT_ROW_HEIGHT = 20;

  private static final Map<Class<?>, Integer> ROW_HEIGHTS = ImmutableMap.of(
    Color.class, 60,
    TableLabel.class, 35,
    DrawableDomElement.class, 64
  );

  private final Configuration myConfiguration;
  private final Module myModule;
  private StyleResolver myStyleResolver;
  private AndroidThemePreviewPanel myPreviewPanel;
  private final StylePropertiesFilter myPropertiesFilter;
  private String myPreviousSelectedTheme;
  // Points to the current selected substyle within the theme.
  private ThemeEditorStyle myCurrentSubStyle;
  // Points to the attribute that original pointed to the substyle.
  private EditedStyleItem mySubStyleSourceAttribute;
  private AttributesPanel myPanel = new AttributesPanel();

  private final JComboBox myThemeCombo = myPanel.getThemeCombo();
  private final JButton myParentThemeButton = myPanel.getParentThemeButton();
  private final JButton myBackButton = myPanel.getBackButton();
  private final JTable myPropertiesTable = myPanel.getPropertiesTable();
  private final JCheckBox myAdvancedFilterCheckBox = myPanel.getAdvancedFilterCheckBox();
  private final JLabel mySubStyleLabel = myPanel.getSubStyleLabel();

  private final ClickableTableCellEditor myStyleEditor;

  public ThemeEditorComponent(final Configuration configuration, final Module module) {
    this.myConfiguration = configuration;
    this.myModule = module;
    this.myStyleResolver = new StyleResolver(myConfiguration);

    ConfigurationListener myConfigListener = new ConfigurationListener() {
      @Override
      public boolean changed(int flags) {

        //reloads the theme editor preview when device is modified
        if ((flags & CFG_DEVICE) != 0) {
          loadStyleProperties();
          myConfiguration.save();
        }

        return true;
      }
    };

    myConfiguration.addListener(myConfigListener);

    myPreviewPanel = new AndroidThemePreviewPanel(myConfiguration);

    myPropertiesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myPropertiesTable.setTableHeader(null);

    // TODO: TableSpeedSearch does not really support filtered tables since it incorrectly uses the model to calculate the number
    // of available cells. Fix this.
    new TableSpeedSearch(myPropertiesTable) {
      @Override
      protected int getElementCount() {
        return myComponent.getRowCount() * myComponent.getColumnCount();
      }
    };

    myStyleEditor = new ClickableTableCellEditor(new ClickableTableCellEditor.ClickListener() {
      @Override
      public void clicked(EditedStyleItem value) {
        if (value.isAttr()) {
          // We need to resolve the theme attribute.
          // TODO: Do we need a full resolution or can we just try to get it from the StyleWrapper?
          ItemResourceValue resourceValue = (ItemResourceValue) myConfiguration.getResourceResolver().findResValue(value.getValue(), false);
          if (resourceValue == null ) {
            LOG.error("Unable to resolve " + value.getValue());
            return;
          }

          EditedStyleItem editedStyleItem = new EditedStyleItem(resourceValue, getSelectedStyle());
          myCurrentSubStyle = myStyleResolver.getStyle(editedStyleItem.getValue());
        } else {
          myCurrentSubStyle = myStyleResolver.getStyle(value.getValue());
        }
        mySubStyleSourceAttribute = value;
        loadStyleProperties();
      }
    });

    myPropertiesTable.setDefaultRenderer(Color.class, new DelegatingCellRenderer(myModule, myConfiguration, false, new ColorRenderer(myConfiguration, myPropertiesTable)));
    myPropertiesTable.setDefaultRenderer(String.class, new DelegatingCellRenderer(myModule, myConfiguration,
                                                                                  myPropertiesTable.getDefaultRenderer(String.class)));
    myPropertiesTable.setDefaultRenderer(Integer.class, new DelegatingCellRenderer(myModule, myConfiguration,
                                                                                   myPropertiesTable.getDefaultRenderer(Integer.class)));
    myPropertiesTable.setDefaultRenderer(Boolean.class, new DelegatingCellRenderer(myModule, myConfiguration,
                                                                                   myPropertiesTable.getDefaultRenderer(Boolean.class)));
    myPropertiesTable.setDefaultRenderer(ThemeEditorStyle.class,
                                         new DelegatingCellRenderer(myModule, myConfiguration, false, myStyleEditor));
    myPropertiesTable.setDefaultRenderer(DrawableDomElement.class,
                                         new DelegatingCellRenderer(myModule, myConfiguration, false, new DrawableRenderer(myConfiguration, myPropertiesTable)));

    myPropertiesTable.setDefaultRenderer(TableLabel.class, new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        this.setFont(HEADER_FONT);
        return this;
      }
    });

    myPropertiesTable.setDefaultEditor(Color.class, new DelegatingCellEditor(false, new ColorEditor(myModule, myConfiguration, myPropertiesTable), module, configuration));
    myPropertiesTable.setDefaultEditor(String.class, new DelegatingCellEditor(myPropertiesTable.getDefaultEditor(String.class), module, configuration));
    myPropertiesTable.setDefaultEditor(Integer.class, new DelegatingCellEditor(myPropertiesTable.getDefaultEditor(Integer.class), module, configuration));
    myPropertiesTable.setDefaultEditor(Boolean.class, new DelegatingCellEditor(myPropertiesTable.getDefaultEditor(Boolean.class), module, configuration));
    // We allow to edit style pointers as Strings.
    myPropertiesTable.setDefaultEditor(ThemeEditorStyle.class, new DelegatingCellEditor(false, myStyleEditor, module, configuration));
    myPropertiesTable.setDefaultEditor(DrawableDomElement.class, new DelegatingCellEditor(false, new DrawableEditor(myModule, myConfiguration, myPropertiesTable), module, configuration));

    myPropertiesFilter = new StylePropertiesFilter();

    // Button to go to the parent theme (if available).
    myBackButton.setToolTipText("Back to the theme");
    myBackButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myCurrentSubStyle = null;
        loadStyleProperties();
      }
    });

    // We have our own custom renderer that it's not based on the default one.
    //noinspection GtkPreferredJComboBoxRenderer
    myThemeCombo.setRenderer(new StyleListCellRenderer(myThemeCombo));
    new ComboboxSpeedSearch(myThemeCombo);
    myParentThemeButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ThemeEditorStyle selectedStyle = getSelectedStyle();
        if (selectedStyle == null) {
          LOG.error("No style selected.");
          return;
        }

        ThemeEditorStyle parent = getSelectedStyle().getParent();
        assert parent != null;

        // TODO: This seems like it could be confusing for users, we might want to differentiate parent navigation depending if it's
        // substyle or theme navigation.
        if (isSubStyleSelected()) {
          myCurrentSubStyle = parent;
          loadStyleProperties();
        }
        else {
          myThemeCombo.setSelectedItem(parent);
        }
      }
    });

    myAdvancedFilterCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (myPropertiesTable.isEditing()) {
          myPropertiesTable.getCellEditor().cancelCellEditing();
        }
        myPropertiesTable.clearSelection();
        myPropertiesFilter.setAdvancedMode(myAdvancedFilterCheckBox.isSelected());
        ((TableRowSorter)myPropertiesTable.getRowSorter()).sort();
      }
    });

    JButton newThemeButton = myPanel.getNewThemeButton();
    newThemeButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ThemeEditorStyle selectedTheme = getSelectedStyle();
        String selectedThemeName = selectedTheme == null ? null : selectedTheme.getName();

        String newThemeName = createNewStyle(selectedThemeName, null/*message*/, null/*newAttributeName*/, null/*newAttributeValue*/);
        if (newThemeName != null) {
          reload(newThemeName);
        }
      }
    });

    myThemeCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        saveCurrentSelectedTheme();
        myCurrentSubStyle = null;
        mySubStyleSourceAttribute = null;

        loadStyleProperties();
      }
    });

    // Adds the Device selection button
    DefaultActionGroup group = new DefaultActionGroup();
    DeviceMenuAction deviceAction = new DeviceMenuAction(myPreviewPanel);
    group.add(deviceAction);
    ActionManager actionManager = ActionManager.getInstance();
    ActionToolbar actionToolbar = actionManager.createActionToolbar("ThemeToolbar", group, true);
    actionToolbar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);
    JPanel myConfigToolbar = myPanel.getConfigToolbar();
    myConfigToolbar.add(actionToolbar.getComponent());

    final JScrollPane scroll = myPanel.getPropertiesScrollPane();
    scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)); // the scroll pane should fill all available space

    mySubStyleLabel.setVisible(false);
    mySubStyleLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    JBScrollPane scrollPanel = new JBScrollPane(myPreviewPanel,
                                                ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                                                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    /*
     * Set a preferred size for the preview panel. Since we are using HORIZONTAL_SCROLLBAR_NEVER, the width will be ignored and the panel
     * size used.
     * The height should be set according to a reasonable space to display the preview layout.
     *
     * TODO: Check the height value.
     */
    myPreviewPanel.setPreferredSize(new Dimension(64, 2000));

    setFirstComponent(scrollPanel);
    setSecondComponent(myPanel.getRightPanel());
    setShowDividerControls(false);
  }

  /**
   * Creates a new theme by displaying the {@link NewStyleDialog}. If newAttributeName is not null, a new attribute will be added to the
   * style with the value specified in newAttributeValue.
   * An optional message can be displayed as hint to the user of why the theme is being created.
   * @return the new style name or null if the style wasn't created.
   */
  @Nullable
  private String createNewStyle(@Nullable String defaultParentStyleName,
                                @Nullable String message,
                                @Nullable final String newAttributeName,
                                @Nullable final String newAttributeValue) {
    final NewStyleDialog dialog = new NewStyleDialog(!isSubStyleSelected() /*isTheme*/,
                                                     myConfiguration,
                                                     defaultParentStyleName,
                                                     getSelectedTheme() != null ? getSelectedTheme().getSimpleName() : null,
                                                     message);
    boolean createStyle = dialog.showAndGet();
    if (!createStyle) {
      return null;
    }

    final String fileName = AndroidResourceUtil.getDefaultResourceFileName(ResourceType.STYLE);
    final List<String> dirNames = Arrays.asList(ResourceFolderType.VALUES.getName());

    if (fileName == null) {
      LOG.error("Couldn't find a default filename for ResourceType.STYLE");
      return null;
    }
    boolean isCreated = AndroidResourceUtil
      .createValueResource(myModule, dialog.getStyleName(), ResourceType.STYLE, fileName, dirNames, new Processor<ResourceElement>() {
        @Override
        public boolean process(ResourceElement element) {
          assert element instanceof Style;
          final Style style = (Style)element;

          style.getParentStyle().setStringValue(dialog.getStyleParentName());

          if (!Strings.isNullOrEmpty(newAttributeName)) {
            StyleItem newItem = style.addItem();
            newItem.getName().setStringValue(newAttributeName);

            if (!Strings.isNullOrEmpty(newAttributeValue)) {
              newItem.setStringValue(newAttributeValue);
            }
          }

          return true;
        }
      });

    if (isCreated) {
      AndroidFacet facet = AndroidFacet.getInstance(myModule);
      if (facet != null) {
        facet.refreshResources();
      }
    }

    return isCreated ? SdkConstants.STYLE_RESOURCE_PREFIX + dialog.getStyleName() : null;
  }

  /**
   * Save the current selected theme so we can restore it if we need to refresh the data.
   * If the theme does not exist anymore, the first available theme will be selected.
   */
  private void saveCurrentSelectedTheme() {
    ThemeEditorStyle selectedTheme = getSelectedStyle();
    myPreviousSelectedTheme = selectedTheme == null ? null : selectedTheme.getName();
  }

  public String getPreviousSelectedTheme() {
    return myPreviousSelectedTheme;
  }

  @Nullable
  private ThemeEditorStyle getSelectedTheme() {
    return (ThemeEditorStyle)myThemeCombo.getSelectedItem();
  }

  @Nullable
  private ThemeEditorStyle getSelectedStyle() {
    if (myCurrentSubStyle != null) {
      return myCurrentSubStyle;
    }

    return getSelectedTheme();
  }

  private boolean isSubStyleSelected() {
    return myCurrentSubStyle != null;
  }

  /**
   * Sets a new value to the passed attribute. It will also trigger the reload if a change it's done.
   * @param rv The attribute to set, including the current value.
   * @param strValue The new value.
   */
  private void createNewThemeWithAttributeValue(@NotNull EditedStyleItem rv, @NotNull String strValue) {
    if (strValue.equals(rv.getRawXmlValue())) {
      // No modification required.
      return;
    }

    ThemeEditorStyle selectedStyle = getSelectedStyle();
    if (selectedStyle == null) {
      LOG.error("No style/theme selected.");
      return;
    }

    // The current style is R/O so we need to propagate this change a new style.
    String newStyleName = createNewStyle(selectedStyle.getName(), String
      .format("<html>The %1$s '<code>%2$s</code>' is Read-Only.<br/>A new %1$s will be created to modify '<code>%3$s</code>'.<br/></html>",
              isSubStyleSelected() ? "style" : "theme",
              selectedStyle.getName(),
              rv.getName()), rv.getQualifiedName(), strValue);

    if (newStyleName == null) {
      return;
    }

    if (!isSubStyleSelected()) {
      // We changed a theme, so we are done.
      reload(newStyleName);

      return;
    }

    ThemeEditorStyle selectedTheme = getSelectedTheme();
    if (selectedTheme == null) {
      LOG.error("No theme selected.");
      return;
    }

    // Decide what property we need to modify.
    // If the modified style was pointed by a theme attribute, we need to use that theme attribute value
    // as property. Otherwise, just update the original property name with the new style.
    String sourcePropertyName = mySubStyleSourceAttribute.isAttr() ?
                                mySubStyleSourceAttribute.getAttrPropertyName():
                                mySubStyleSourceAttribute.getQualifiedName();

    // We've modified a sub-style so we need to modify the attribute that was originally pointing to this.
    if (selectedTheme.isReadOnly()) {
      // The theme pointing to the new style is r/o so create a new theme and then write the value.
      String newThemeName = createNewStyle(selectedTheme.getName(), String.format(
        "<html>The style '%1$s' which references to '%2$s' is also Read-Only.<br/>" +
        "A new theme will be created to point to the modified style '%3$s'.<br/></html>", selectedTheme.getName(), rv.getName(),
        newStyleName), sourcePropertyName, newStyleName);

      if (newThemeName != null) {
        reload(newThemeName);
      }
    } else {
      // The theme pointing to the new style is writable, so go ahead.
      selectedTheme.setValue(sourcePropertyName, newStyleName);
      reload(selectedTheme.getName());
    }
  }

  /**
   * Reloads the properties editor.
   * @param defaultThemeName The name to select from the themes list.
   */
  public void reload(@Nullable final String defaultThemeName) {
    // This is required since the configuration could have a link to a non existent theme (if it was removed).
    // If the configuration is pointing to a theme that does not exist anymore, the local resource resolution breaks so ThemeResolver
    // fails to find the local themes.
    myConfiguration.setTheme(null);
    myCurrentSubStyle = null;
    mySubStyleSourceAttribute = null;

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        myStyleResolver = new StyleResolver(myConfiguration);
        final ThemeResolver themeResolver = new ThemeResolver(myConfiguration, myStyleResolver);
        myThemeCombo.setModel(new ThemesListModel(themeResolver, defaultThemeName));

        loadStyleProperties();
      }
    });

    saveCurrentSelectedTheme();
  }

  /**
   * Loads the theme properties table for the current selected theme or substyle.
   */
  private void loadStyleProperties() {
    final ThemeEditorStyle selectedTheme = getSelectedTheme();
    final ThemeEditorStyle selectedStyle = getSelectedStyle();

    if (selectedTheme == null || selectedStyle == null) {
      LOG.error("No style/theme selected");
      return;
    }

    final ThemeEditorStyle parentStyle = selectedStyle.getParent();

    if (myCurrentSubStyle != null) {
      mySubStyleLabel.setText("\u27A5 " + myCurrentSubStyle.getSimpleName());
      mySubStyleLabel.setVisible(true);

      // Editing substyle of a substyle is disabled, because it's not clear how to do it properly
      myStyleEditor.setDetailsActive(false);
    } else {
      mySubStyleLabel.setVisible(false);
      myStyleEditor.setDetailsActive(true);
    }

    // Setting advanced to true here is a required workaround until we fix the hack to set the cell height below.
    myPropertiesFilter.setAdvancedMode(true);
    myParentThemeButton.setVisible(parentStyle != null);
    myParentThemeButton.setToolTipText(parentStyle != null ? parentStyle.getName() : "");
    myBackButton.setVisible(myCurrentSubStyle != null);
    myConfiguration.setTheme(selectedTheme.getName());


    final AttributesTableModel model = new AttributesTableModel(selectedStyle);

    model.addThemePropertyChangedListener(new AttributesTableModel.ThemePropertyChangedListener() {
      @Override
      public void attributeChangedOnReadOnlyTheme(final EditedStyleItem attribute, final String newValue) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            createNewThemeWithAttributeValue(attribute, newValue);
          }
        });
      }
    });

    model.addTableModelListener(new TableModelListener() {
      @Override
      public void tableChanged(TableModelEvent e) {

        if (e.getType() == TableModelEvent.UPDATE && e.getLastRow() == TableModelEvent.HEADER_ROW) {
          myPropertiesTable.setRowHeight(PROPERTIES_DEFAULT_ROW_HEIGHT);
          for (int row = 0; row < model.getRowCount(); row++) {
            final Class<?> cellClass = model.getCellClass(row, 0);
            final Integer rowHeight = ROW_HEIGHTS.get(cellClass);
            if (rowHeight != null) {
              // TODO important colors should be taller then less important colors.
              int viewRow = myPropertiesTable.convertRowIndexToView(row);

              if (viewRow != -1) {
                myPropertiesTable.setRowHeight(viewRow, rowHeight);
              }
            }
          }
        }

        if (myPreviewPanel != null) {
          // We ran this with invokeLater to allow any PSI rescans to run and update the modification count.
          // If we don't use invokeLater, the repaint will still see the previous cached PSI file value.
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              myPreviewPanel.updateConfiguration(myConfiguration);
              myPreviewPanel.repaint();
              myPropertiesTable.repaint();
            }
          });
        }
      }
    });

    myPropertiesTable.setRowSorter(null); // Clean any previous row sorters.
    TableRowSorter<AttributesTableModel> sorter = new TableRowSorter<AttributesTableModel>(model);
    sorter.setRowFilter(myPropertiesFilter);
    myPropertiesTable.setRowSorter(sorter);
    myAdvancedFilterCheckBox.setSelected(myPropertiesFilter.myAdvancedMode);

    myPropertiesTable.setModel(model);
    //We calling this to trigger tableChanged, which will calculate row heights and rePaint myPreviewPanel
    model.fireTableStructureChanged();
  }

  class StylePropertiesFilter extends RowFilter<AttributesTableModel, Integer> {
    // TODO: This is just a random list of properties. Replace with a possibly dynamic list of simple properties.
    private final Set<String> SIMPLE_PROPERTIES = ImmutableSet
      .of("android:background", "android:colorAccent", "android:colorBackground", "android:colorForegroundInverse", "android:colorPrimary",
          "android:editTextColor", "spinnerStyle", "android:textColorHighlight", "android:textColorLinkInverse", "android:textColorPrimary",
          "windowTitleStyle");
    private boolean myAdvancedMode = true;
    private boolean myLocallyDefinedMode = false;

    public void setOnlyLocallyDefinedMode(boolean local) {
      this.myLocallyDefinedMode = local;
    }
    public void setAdvancedMode(boolean advanced) {
      this.myAdvancedMode = advanced;
    }

    @Override
    public boolean include(Entry<? extends AttributesTableModel, ? extends Integer> entry) {
      // We use the column 1 because it's the one that contains the ItemResourceValueWrapper.
      Object value = entry.getModel().getValueAt(entry.getIdentifier().intValue(), 1);
      String propertyName;

      if (value instanceof TableLabel) {
        return myAdvancedMode;
      }
      if (value instanceof EditedStyleItem) {
        propertyName = ((EditedStyleItem)value).getQualifiedName();
      }
      else {
        propertyName = value.toString();
      }

      ThemeEditorStyle selectedTheme = getSelectedStyle();
      if (selectedTheme == null) {
        LOG.error("No theme selected.");
        return false;
      }
      if (myLocallyDefinedMode && !selectedTheme.isAttributeDefined(propertyName)) {
        return false;
      }

      if (myAdvancedMode) {
        // All properties shown.
        return true;
      }

      return SIMPLE_PROPERTIES.contains(propertyName);
    }
  }
}
