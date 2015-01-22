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
package com.android.tools.idea.editors.theme;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.sdklib.devices.Device;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.swing.layoutlib.AndroidThemePreviewPanel;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.ProjectTopics;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.JBColor;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.util.Processor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Style;
import org.jetbrains.android.dom.resources.StyleItem;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import spantable.CellSpanTable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

public class ThemeEditor extends UserDataHolderBase implements FileEditor {
  private static final Logger LOG = Logger.getInstance(ThemeEditor.class);

  private static final int PROPERTIES_DEFAULT_ROW_HEIGHT = 20;
  private static final int PROPERTIES_COLOR_ROW_HEIGHT = 60;
  private static final Font HEADER_FONT = UIUtil.getTitledBorderFont().deriveFont(20.0f);

  private static final Map<Class<?>, Integer> ROW_HEIGHTS = ImmutableMap.of(
    Color.class, PROPERTIES_COLOR_ROW_HEIGHT,
    TableLabel.class, 35
  );

  private final Configuration myConfiguration;
  private final Module myModule;
  private StyleResolver myStyleResolver;
  private AndroidThemePreviewPanel myPreviewPanel;
  private VirtualFile myFile;
  private final JComponent myComponent;
  private final JComboBox myThemeCombo;
  private final JButton myParentThemeButton;
  private final JButton myBackButton;
  private final StylePropertiesFilter myPropertiesFilter;
  private final JTable myPropertiesTable;
  private final JCheckBox myAdvancedFilterCheckBox;
  private final JLabel mySubStyleLabel;
  private long myModificationCount;
  private String myPreviousSelectedTheme;
  // Points to the current selected substyle within the theme.
  private ThemeEditorStyle myCurrentSubStyle;
  // Points to the attribute that original pointed to the substyle.
  private EditedStyleItem mySubStyleSourceAttribute;

  public ThemeEditor(@NotNull Project project, @NotNull VirtualFile file) {
    myFile = file;
    myModule = ((ThemeEditorVirtualFile) file).getModule();

    final ThemeEditorReopener reopener = project.getComponent(ThemeEditorReopener.class);
    reopener.notifyOpened(myModule);

    final AndroidFacet facet = AndroidFacet.getInstance(myModule);
    myConfiguration = facet.getConfigurationManager().getConfiguration(myFile);
    myModificationCount = getModificationCount();
    myStyleResolver = new StyleResolver(myConfiguration);

    // We currently use the default device. We will dynamically adjust the width and height depending on the size of the window.
    // TODO: Add configuration chooser to allow changing parameters of the configuration.
    final Device device = new Device.Builder(myConfiguration.getDevice()).build();
    PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(project, myFile);
    myConfiguration.setDevice(device, false);
    myPreviewPanel = new AndroidThemePreviewPanel(psiFile, myConfiguration);

    myPropertiesTable = new CellSpanTable();
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

    ClickableTableCellEditor styleEditor = new ClickableTableCellEditor(new ClickableTableCellEditor.ClickListener() {
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

    myPropertiesTable.setDefaultRenderer(Color.class, new ColorRendererEditor(myModule, myConfiguration, myPropertiesTable));
    myPropertiesTable.setDefaultRenderer(String.class, new DelegatingCellRenderer(myModule, myConfiguration,
                                                                                  myPropertiesTable.getDefaultRenderer(String.class)));
    myPropertiesTable.setDefaultRenderer(Integer.class, new DelegatingCellRenderer(myModule, myConfiguration,
                                                                                   myPropertiesTable.getDefaultRenderer(Integer.class)));
    myPropertiesTable.setDefaultRenderer(Boolean.class, new DelegatingCellRenderer(myModule, myConfiguration,
                                                                                   myPropertiesTable.getDefaultRenderer(Boolean.class)));
    myPropertiesTable.setDefaultRenderer(ThemeEditorStyle.class, new DelegatingCellRenderer(myModule, myConfiguration, false, styleEditor));

    myPropertiesTable.setDefaultRenderer(TableLabel.class, new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        this.setFont(HEADER_FONT);
        return this;
      }
    });

    final ColorRendererEditor editor = new ColorRendererEditor(myModule, myConfiguration, myPropertiesTable);
    myPropertiesTable.setDefaultEditor(Color.class, editor);
    myPropertiesTable.setDefaultEditor(String.class, new DelegatingCellEditor(myPropertiesTable.getDefaultEditor(String.class)));
    myPropertiesTable.setDefaultEditor(Integer.class, new DelegatingCellEditor(myPropertiesTable.getDefaultEditor(Integer.class)));
    myPropertiesTable.setDefaultEditor(Boolean.class, new DelegatingCellEditor(myPropertiesTable.getDefaultEditor(Boolean.class)));
    // We allow to edit style pointers as Strings.
    myPropertiesTable.setDefaultEditor(ThemeEditorStyle.class, new DelegatingCellEditor(false, styleEditor));

    myPropertiesFilter = new StylePropertiesFilter();

    Border toolBarElementsBorder = BorderFactory.createEmptyBorder(10, 10, 10, 10);
    // Button to go to the parent theme (if available).
    myParentThemeButton = new JButton(AllIcons.Actions.MoveUp);
    myParentThemeButton.setBorder(toolBarElementsBorder);
    myBackButton = new JButton(AllIcons.Actions.Back);
    myBackButton.setBorder(toolBarElementsBorder);
    myBackButton.setToolTipText("Back to the theme");
    myBackButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myCurrentSubStyle = null;
        loadStyleProperties();
      }
    });

    myThemeCombo = new ComboBox();
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

    myAdvancedFilterCheckBox = new JCheckBox("Advanced");
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

    JButton newThemeButton = new JButton(AllIcons.General.Add);
    newThemeButton.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
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

    final JPanel toolbar = new JPanel();
    toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.LINE_AXIS));

    myBackButton.setAlignmentX(Component.LEFT_ALIGNMENT);
    myAdvancedFilterCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
    newThemeButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
    myParentThemeButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
    toolbar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.GRAY));
    toolbar.add(myBackButton);
    toolbar.add(myAdvancedFilterCheckBox);
    toolbar.add(Box.createHorizontalGlue());
    toolbar.add(newThemeButton);
    toolbar.add(myParentThemeButton);
    final JScrollPane scroll = new JScrollPane(myPropertiesTable);
    scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)); // the scroll pane should fill all available space

    mySubStyleLabel = new JLabel();
    mySubStyleLabel.setVisible(false);
    mySubStyleLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    toolbar.setAlignmentX(Component.LEFT_ALIGNMENT);
    mySubStyleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    myThemeCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
    scroll.setAlignmentX(Component.LEFT_ALIGNMENT);


    JPanel rightPanel = new JPanel();
    rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.PAGE_AXIS));
    rightPanel.add(myThemeCombo);
    rightPanel.add(mySubStyleLabel);
    rightPanel.add(toolbar);
    rightPanel.add(scroll);

    Splitter split = new Splitter();
    split.setFirstComponent(myPreviewPanel);
    split.setSecondComponent(rightPanel);
    split.setShowDividerControls(false);
    myComponent = split;

    // If project roots change, reload the themes. This happens for example once the libraries have finished loading.
    project.getMessageBus().connect(this).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        long newModificationCount = getModificationCount();
        if (myModificationCount != newModificationCount) {
          myModificationCount = newModificationCount;
          reload(myPreviousSelectedTheme);
        }
      }
    });

    // a theme can contain theme attributes (listed in attrs.xml) and also global defaults (all of attrs.xml)
    reload(null/*defaultThemeName*/);
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
    } else {
      mySubStyleLabel.setVisible(false);
    }

    // Setting advanced to true here is a required workaround until we fix the hack to set the cell height below.
    myPropertiesFilter.setAdvancedMode(true);
    myParentThemeButton.setVisible(parentStyle != null);
    myParentThemeButton.setToolTipText(parentStyle != null ? parentStyle.getName() : "");
    myBackButton.setVisible(myCurrentSubStyle != null);
    myConfiguration.setTheme(selectedTheme.getName());

    final List<EditedStyleItem> rawAttributes = ThemeEditorUtils.resolveAllAttributes(selectedStyle);
    final List<EditedStyleItem> attributes = new ArrayList<EditedStyleItem>();
    final List<TableLabel> labels = AttributesSorter.generateLabels(rawAttributes, attributes);

    AttributesTableModel rawModel = new AttributesTableModel(selectedStyle, attributes) {
      @Override
      protected boolean isReadOnly() {
        // No theme is R/O since we will create a new one if it is.
        return false;
      }

      @Override
      public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
        if (aValue == null) {
          return;
        }

        if (!super.isReadOnly()) {
          // Not R/O, just issue the modification to the parent.
          super.setValueAt(aValue, rowIndex, columnIndex);
          return;
        }

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            setAttributeValue(myAttributes.get(rowIndex), aValue.toString());
          }
        });
      }
    };
    rawModel.addTableModelListener(new TableModelListener() {
      @Override
      public void tableChanged(TableModelEvent e) {
        if (myPreviewPanel != null) {
          // We ran this with invokeLater to allow any PSI rescans to run and update the modification count.
          // If we don't use invokeLater, the repaint will still see the previous cached PSI file value.
          ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                myPreviewPanel.updateConfiguration(myConfiguration);
                myPreviewPanel.repaint();
              }
            }
          );
        }
      }
    });

    final LabelledModel model = new LabelledModel(
      rawModel,
      labels
    );
    myPropertiesTable.setRowSorter(null); // Clean any previous row sorters.
    myPropertiesTable.setModel(model);

    TableRowSorter<LabelledModel> sorter = new TableRowSorter<LabelledModel>(model);
    sorter.setRowFilter(myPropertiesFilter);
    myPropertiesTable.setRowSorter(sorter);

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

    myAdvancedFilterCheckBox.setSelected(myPropertiesFilter.myAdvancedMode);

    if (myPreviewPanel != null) {
      myPreviewPanel.updateConfiguration(myConfiguration);
      myPreviewPanel.repaint();
    }
  }

  /**
   * Sets a new value to the passed attribute. It will also trigger the reload if a change it's done.
   * @param rv The attribute to set, including the current value.
   * @param strValue The new value.
   */
  private void setAttributeValue(@NotNull EditedStyleItem rv, @NotNull String strValue) {
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
      .format("<html>The '%1$s' style is Read-Only.<br/>A new style will be created to modify '%2$s'.<br/></html>",
              selectedStyle.getName(), rv.getName()), rv.getQualifiedName(), strValue);

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
   * Returns the modification count of the app resources repository or -1 if it fails to get the count.
   */
  private long getModificationCount() {
    AppResourceRepository resourceRepository = AppResourceRepository.getAppResources(myConfiguration.getModule(), true);
    return resourceRepository != null ? resourceRepository.getModificationCount() : -1;
  }

  /**
   * Save the current selected theme so we can restore it if we need to refresh the data.
   * If the theme does not exist anymore, the first available theme will be selected.
   */
  private void saveCurrentSelectedTheme() {
    ThemeEditorStyle selectedTheme = getSelectedStyle();
    myPreviousSelectedTheme = selectedTheme == null ? null : selectedTheme.getName();
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
    final NewStyleDialog dialog = new NewStyleDialog(myConfiguration, defaultParentStyleName, message);
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

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  /**
   * Displayed in the IDE on the tab at the bottom of the editor.
   */
  @NotNull
  @Override
  public String getName() {
    return "Theme Editor";
  }

  @NotNull
  @Override
  public FileEditorState getState(@NotNull FileEditorStateLevel fileEditorStateLevel) {
    return FileEditorState.INSTANCE;
  }

  @Override
  public void setState(@NotNull FileEditorState fileEditorState) {
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  @Override
  public void selectNotify() {
    long newModificationCount = getModificationCount();
    if (myModificationCount != newModificationCount) {
      myModificationCount = newModificationCount;
      reload(myPreviousSelectedTheme);
    }
  }

  @Override
  public void deselectNotify() {
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener propertyChangeListener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener propertyChangeListener) {
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  @Override
  public void dispose() {
    // TODO what should go here?
  }

  class StylePropertiesFilter extends RowFilter<LabelledModel, Integer> {
    // TODO: This is just a random list of properties. Replace with a possibly dynamic list of simple properties.
    private final Set<String> SIMPLE_PROPERTIES = ImmutableSet
      .of("android:background", "android:colorAccent", "android:colorBackground", "android:colorForegroundInverse", "android:colorPrimary",
          "android:editTextColor", "spinnerStyle", "android:textColorHighlight", "android:textColorLinkInverse",
          "android:textColorPrimary", "windowTitleStyle");
    private boolean myAdvancedMode = true;
    private boolean myLocallyDefinedMode = false;

    public void setOnlyLocallyDefinedMode(boolean local) {
      this.myLocallyDefinedMode = local;
    }
    public void setAdvancedMode(boolean advanced) {
      this.myAdvancedMode = advanced;
    }

    @Override
    public boolean include(Entry<? extends LabelledModel, ? extends Integer> entry) {
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

}
