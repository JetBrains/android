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
import com.android.tools.idea.editors.theme.attributes.AttributesGrouper;
import com.android.tools.idea.editors.theme.attributes.AttributesModelColorPaletteModel;
import com.android.tools.idea.editors.theme.attributes.AttributesTableModel;
import com.android.tools.idea.editors.theme.attributes.ShowJavadocAction;
import com.android.tools.idea.editors.theme.attributes.TableLabel;
import com.android.tools.idea.editors.theme.attributes.editors.*;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.dom.drawable.DrawableDomElement;
import org.jetbrains.android.dom.resources.Flag;
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
import javax.swing.plaf.PanelUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class ThemeEditorComponent extends Splitter {
  private static final Logger LOG = Logger.getInstance(ThemeEditorComponent.class);

  public static final float HEADER_FONT_SCALE = 1.3f;
  public static final int REGULAR_CELL_PADDING = 4;
  public static final int LARGE_CELL_PADDING = 10;

  private Font myHeaderFont;

  private StyleResolver myStyleResolver;
  private String myPreviousSelectedTheme;

  // Points to the current selected substyle within the theme.
  private ThemeEditorStyle myCurrentSubStyle;

  // Points to the attribute that original pointed to the substyle.
  private EditedStyleItem mySubStyleSourceAttribute;

  // Subcomponents
  private final Configuration myConfiguration;
  private final Module myModule;
  private final AndroidThemePreviewPanel myPreviewPanel;
  private final StyleAttributesFilter myAttributesFilter;
  private final AttributesPanel myPanel = new AttributesPanel();
  private final ThemeEditorTable myAttributesTable = myPanel.getAttributesTable();

  private final AttributeReferenceRendererEditor myStyleEditor;
  private final AttributeReferenceRendererEditor.ClickListener myClickListener;
  private final ConfigurationListener myConfigListener = new ConfigurationListener() {
    @Override
    public boolean changed(int flags) {

      //reloads the theme editor preview when device is modified
      if ((flags & CFG_DEVICE) != 0) {
        loadStyleAttributes();
        myConfiguration.save();
      }

      return true;
    }
  };
  private ThemeEditorStyle mySelectedTheme;

  public ThemeEditorComponent(final Configuration configuration, final Module module) {
    this.myConfiguration = configuration;
    this.myModule = module;

    myConfiguration.addListener(myConfigListener);
    myStyleResolver = new StyleResolver(myConfiguration);

    myPreviewPanel = new AndroidThemePreviewPanel(myConfiguration);
    myPreviewPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

    // Setup Javadoc handler.
    ActionManager actionManager = ActionManager.getInstance();
    ShowJavadocAction showJavadoc = new ShowJavadocAction(myAttributesTable, myModule, myConfiguration);
    showJavadoc.registerCustomShortcutSet(actionManager.getAction(IdeActions.ACTION_QUICK_JAVADOC).getShortcutSet(), myAttributesTable);

    Project project = myModule.getProject();
    ResourcesCompletionProvider completionProvider = new ResourcesCompletionProvider(myConfiguration.getResourceResolver());
    myClickListener = new AttributeReferenceRendererEditor.ClickListener() {
      @Override
      public void clicked(@NotNull EditedStyleItem value) {
        if (value.isAttr() && getSelectedStyle() != null && myConfiguration.getResourceResolver() != null) {
          // We need to resolve the theme attribute.
          // TODO: Do we need a full resolution or can we just try to get it from the StyleWrapper?
          ItemResourceValue resourceValue = (ItemResourceValue)myConfiguration.getResourceResolver().findResValue(value.getValue(), false);
          if (resourceValue == null) {
            LOG.error("Unable to resolve " + value.getValue());
            return;
          }

          EditedStyleItem editedStyleItem = new EditedStyleItem(resourceValue, getSelectedStyle());

          assert editedStyleItem.getValue() != null;
          myCurrentSubStyle = myStyleResolver.getStyle(editedStyleItem.getValue());
        }
        else {
          if (value.getValue() == null) {
            LOG.error("null value for " + value.getName());
            return;
          }

          myCurrentSubStyle = myStyleResolver.getStyle(value.getValue());
        }
        mySubStyleSourceAttribute = value;
        loadStyleAttributes();
      }
    };
    myStyleEditor = new AttributeReferenceRendererEditor(project, completionProvider);

    final AndroidFacet facet = AndroidFacet.getInstance(module);
    RenderTask renderTask = null;
    if (facet != null) {
      final RenderService service = RenderService.get(facet);
      renderTask = service.createTask(null, configuration, new RenderLogger("ThemeEditorLogger", module), null);
    }

    myAttributesTable.setDefaultRenderer(Color.class, new DelegatingCellRenderer(new ColorRenderer(myConfiguration)));
    myAttributesTable.setDefaultRenderer(EditedStyleItem.class, new DelegatingCellRenderer(new AttributeReferenceRendererEditor(project, completionProvider)));
    myAttributesTable.setDefaultRenderer(ThemeEditorStyle.class, new DelegatingCellRenderer(new AttributeReferenceRendererEditor(project, completionProvider)));
    myAttributesTable.setDefaultRenderer(String.class, new DelegatingCellRenderer(myAttributesTable.getDefaultRenderer(String.class)));
    myAttributesTable.setDefaultRenderer(Integer.class, new DelegatingCellRenderer(new IntegerRenderer()));
    myAttributesTable.setDefaultRenderer(Boolean.class, new DelegatingCellRenderer(new BooleanRendererEditor(myModule)));
    myAttributesTable.setDefaultRenderer(Enum.class, new DelegatingCellRenderer(new EnumRendererEditor()));
    myAttributesTable.setDefaultRenderer(Flag.class, new DelegatingCellRenderer(new FlagRendererEditor()));
    myAttributesTable.setDefaultRenderer(AttributesTableModel.ParentAttribute.class, new DelegatingCellRenderer(new ParentRendererEditor(myConfiguration)));
    myAttributesTable.setDefaultRenderer(DrawableDomElement.class, new DelegatingCellRenderer(new DrawableRenderer(myAttributesTable, renderTask)));
    myAttributesTable.setDefaultRenderer(TableLabel.class, new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        this.setFont(myHeaderFont);
        return this;
      }
    });

    myAttributesTable.setDefaultEditor(Color.class, new DelegatingCellEditor(false, new ColorEditor(myModule, myConfiguration), module, configuration));
    myAttributesTable.setDefaultEditor(EditedStyleItem.class, new DelegatingCellEditor(false, new AttributeReferenceRendererEditor(project, completionProvider), module, configuration));
    myAttributesTable.setDefaultEditor(String.class, new DelegatingCellEditor(false, myAttributesTable.getDefaultEditor(String.class), module, configuration));
    myAttributesTable.setDefaultEditor(Integer.class, new DelegatingCellEditor(myAttributesTable.getDefaultEditor(Integer.class), module, configuration));
    myAttributesTable.setDefaultEditor(Boolean.class, new DelegatingCellEditor(false, new BooleanRendererEditor(myModule), module, configuration));
    myAttributesTable.setDefaultEditor(Enum.class, new DelegatingCellEditor(false, new EnumRendererEditor(), module, configuration));
    myAttributesTable.setDefaultEditor(Flag.class, new DelegatingCellEditor(false, new FlagRendererEditor(), module, configuration));
    myAttributesTable.setDefaultEditor(AttributesTableModel.ParentAttribute.class, new DelegatingCellEditor(false, new ParentRendererEditor(myConfiguration), module, configuration));

    // We allow to edit style pointers as Strings.
    myAttributesTable.setDefaultEditor(ThemeEditorStyle.class, new DelegatingCellEditor(false, myStyleEditor, module, configuration));
    myAttributesTable.setDefaultEditor(DrawableDomElement.class, new DelegatingCellEditor(false, new DrawableEditor(myModule, myAttributesTable, renderTask), module, configuration));
    updateUiParameters();

    myAttributesFilter = new StyleAttributesFilter();

    myPanel.getBackButton().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myCurrentSubStyle = null;
        loadStyleAttributes();
      }
    });

    myPanel.getAdvancedFilterCheckBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myAttributesTable.isEditing()) {
          myAttributesTable.getCellEditor().cancelCellEditing();
        }

        myAttributesTable.clearSelection();
        myPanel.getPalette().clearSelection();
        myAttributesFilter.setFilterEnabled(!myPanel.isAdvancedMode());

        myAttributesFilter.setAttributesFilter(myAttributesFilter.ATTRIBUTES_DEFAULT_FILTER);

        ((TableRowSorter)myAttributesTable.getRowSorter()).sort();
        myAttributesTable.updateRowHeights();
      }
    });

    myPanel.getThemeCombo().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myPanel.isCreateNewThemeSelected()) {
          if (!createNewTheme()) {
            // User clicked "cancel", restore previously selected item in themes combo.
            myPanel.getThemeCombo().setSelectedItem(mySelectedTheme);
          }
          return;
        }
        saveCurrentSelectedTheme();
        myCurrentSubStyle = null;
        mySubStyleSourceAttribute = null;

        loadStyleAttributes();
      }
    });

    myPanel.getAttrGroupCombo().setModel(new DefaultComboBoxModel(AttributesGrouper.GroupBy.values()));
    myPanel.getAttrGroupCombo().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        loadStyleAttributes();
      }
    });

    // Adds the Device selection button
    DefaultActionGroup group = new DefaultActionGroup();
    DeviceMenuAction deviceAction = new DeviceMenuAction(myPreviewPanel);
    group.add(deviceAction);
    ActionToolbar actionToolbar = actionManager.createActionToolbar("ThemeToolbar", group, true);
    actionToolbar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);
    JPanel myConfigToolbar = myPanel.getConfigToolbar();
    myConfigToolbar.add(actionToolbar.getComponent());

    final JScrollPane scroll = myPanel.getAttributesScrollPane();
    scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)); // the scroll pane should fill all available space

    setFirstComponent(myPreviewPanel);
    setSecondComponent(myPanel.getRightPanel());
    setShowDividerControls(false);
  }

  /**
   * Launches dialog to create a new theme based on selected one.
   * @return whether creation of new theme succeeded.
   */
  private boolean createNewTheme() {
    ThemeEditorStyle selectedTheme = getSelectedStyle();
    String selectedThemeName = selectedTheme == null ? null : selectedTheme.getName();

    String newThemeName = createNewStyle(selectedThemeName, null/*message*/, null/*newAttributeName*/, null/*newAttributeValue*/);
    if (newThemeName != null) {
      reload(newThemeName);
      return true;
    }
    return false;
  }

  public void goToParent() {
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
      loadStyleAttributes();
    }
    else {
      myPanel.setSelectedTheme(parent);
    }
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
    final List<String> dirNames = Collections.singletonList(ResourceFolderType.VALUES.getName());

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

  @Nullable
  public String getPreviousSelectedTheme() {
    return myPreviousSelectedTheme;
  }

  @Nullable
  ThemeEditorStyle getSelectedTheme() {
    return mySelectedTheme;
  }

  //Never null, because DefaultComboBoxModel and fixed list of items rendered
  @NotNull
  private AttributesGrouper.GroupBy getSelectedAttrGroup() {
    return (AttributesGrouper.GroupBy)myPanel.getAttrGroupCombo().getSelectedItem();
  }

  @Nullable
  private ThemeEditorStyle getSelectedStyle() {
    if (myCurrentSubStyle != null) {
      return myCurrentSubStyle;
    }

    return getSelectedTheme();
  }

  @Nullable
  ThemeEditorStyle getCurrentSubStyle() {
    return myCurrentSubStyle;
  }

  private boolean isSubStyleSelected() {
    return myCurrentSubStyle != null;
  }

  /**
   * Sets a new value to the passed attribute. It will also trigger the reload if a change happened.
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
   * Reloads the attributes editor.
   * @param defaultThemeName The name to select from the themes list.
   */
  public void reload(@Nullable final String defaultThemeName) {
    reload(defaultThemeName, null);
  }

  public void reload(@Nullable final String defaultThemeName, @Nullable final String defaultSubStyleName) {
    // This is required since the configuration could have a link to a non existent theme (if it was removed).
    // If the configuration is pointing to a theme that does not exist anymore, the local resource resolution breaks so ThemeResolver
    // fails to find the local themes.
    myConfiguration.setTheme(null);
    myCurrentSubStyle = defaultSubStyleName == null ? null : myStyleResolver.getStyle(defaultSubStyleName);
    mySubStyleSourceAttribute = null;

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        myStyleResolver = new StyleResolver(myConfiguration);
        final ThemeResolver themeResolver = new ThemeResolver(myConfiguration, myStyleResolver);
        myPanel.getThemeCombo().setModel(new ThemesListModel(themeResolver, defaultThemeName));

        loadStyleAttributes();
      }
    });

    mySelectedTheme = myPanel.getSelectedTheme();
    saveCurrentSelectedTheme();
  }

  /**
   * Loads the theme attributes table for the current selected theme or substyle.
   */
  private void loadStyleAttributes() {
    mySelectedTheme = myPanel.getSelectedTheme();
    final ThemeEditorStyle selectedTheme = getSelectedTheme();
    final ThemeEditorStyle selectedStyle = getSelectedStyle();

    if (selectedTheme == null || selectedStyle == null) {
      LOG.error("No style/theme selected");
      return;
    }

    myPanel.setSubstyleName(myCurrentSubStyle == null ? null : myCurrentSubStyle.getName());

    myPanel.getBackButton().setVisible(myCurrentSubStyle != null);
    myPanel.getPalette().setVisible(myCurrentSubStyle == null);
    myConfiguration.setTheme(selectedTheme.getName());

    assert myConfiguration.getResourceResolver() != null; // ResourceResolver is only null if no theme was set.
    final AttributesTableModel model = new AttributesTableModel(selectedStyle, getSelectedAttrGroup(), myConfiguration.getResourceResolver(), myModule.getProject());
    model.setGoToDefinitionListener(myClickListener);

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
        if (e.getType() == TableModelEvent.UPDATE) {

          AndroidFacet facet = AndroidFacet.getInstance(myModule);
          if (facet != null) {
            facet.refreshResources();
          }

          if (e.getLastRow() == 0) { // Indicates a change in the theme name
            reload(model.getThemeNameInXml());
          }
          else if (e.getLastRow() == TableModelEvent.HEADER_ROW) {
            myAttributesTable.updateRowHeights();
          }
          else {
            reload(myPreviousSelectedTheme);
          }
        }

        if (myPreviewPanel != null) {
          // We ran this with invokeLater to allow any PSI rescans to run and update the modification count.
          // If we don't use invokeLater, the repaint will still see the previous cached PSI file value.
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              myPreviewPanel.updateConfiguration(myConfiguration);
              myPreviewPanel.revalidate();
              myPreviewPanel.repaint();
              myAttributesTable.repaint();
            }
          });
        }
      }
    });

    myAttributesTable.setRowSorter(null); // Clean any previous row sorters.
    TableRowSorter<AttributesTableModel> sorter = new TableRowSorter<AttributesTableModel>(model);
    sorter.setRowFilter(myAttributesFilter);
    myAttributesTable.setRowSorter(sorter);
    myPanel.setAdvancedMode(!myAttributesFilter.myIsFilterEnabled);

    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        goToParent();
      }
    };

    myAttributesTable.setModel(model);
    myAttributesTable.updateRowHeights();
    model.parentAttribute.setGotoDefinitionCallback(listener);

    myPanel.getPalette().setModel(new AttributesModelColorPaletteModel(myConfiguration, model));
    myPanel.getPalette().addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          AttributesModelColorPaletteModel model = (AttributesModelColorPaletteModel)myPanel.getPalette().getModel();
          List<EditedStyleItem> references = model.getReferences((Color)e.getItem());
          if (references.isEmpty()) {
            return;
          }

          HashSet<String> attributeNames = new HashSet<String>(references.size());
          for(EditedStyleItem item : references) {
            attributeNames.add(item.getQualifiedName());
          }
          myAttributesFilter.setAttributesFilter(attributeNames);
          myAttributesFilter.setFilterEnabled(true);
        } else {
          myAttributesFilter.setFilterEnabled(false);
        }

        if (myAttributesTable.isEditing()) {
          myAttributesTable.getCellEditor().cancelCellEditing();
        }
        ((TableRowSorter)myAttributesTable.getRowSorter()).sort();
        myPanel.getAdvancedFilterCheckBox().getModel().setSelected(!myAttributesFilter.myIsFilterEnabled);
      }
    });

    //We calling this to trigger tableChanged, which will calculate row heights and rePaint myPreviewPanel
    model.fireTableStructureChanged();
  }

  @Override
  public void dispose() {
    myConfiguration.removeListener(myConfigListener);
    super.dispose();
  }

  class StyleAttributesFilter extends RowFilter<AttributesTableModel, Integer> {
    // TODO: This is just a random list of attributes. Replace with a possibly dynamic list of simple attributes.
    public final Set<String> ATTRIBUTES_DEFAULT_FILTER = ImmutableSet
      .of("android:colorPrimary",
          "android:colorPrimaryDark",
          "android:colorAccent",
          "android:colorForeground",
          "android:textColorPrimary",
          "android:textColorSecondary",
          "android:textColorPrimaryInverse",
          "android:textColorSecondaryInverse",
          "android:colorBackground",
          "android:windowBackground",
          "android:navigationBarColor");
    private boolean myIsFilterEnabled = true;
    private Set<String> filterAttributes = ATTRIBUTES_DEFAULT_FILTER;

    public void setFilterEnabled(boolean enabled) {
      this.myIsFilterEnabled = enabled;
    }

    /**
     * Set the attribute names we want to display.
     */
    public void setAttributesFilter(@NotNull Set<String> attributeNames) {
      filterAttributes = ImmutableSet.copyOf(attributeNames);
    }

    @Override
    public boolean include(Entry<? extends AttributesTableModel, ? extends Integer> entry) {
      if (!myIsFilterEnabled) {
        return true;
      }

      // We use the column 1 because it's the one that contains the ItemResourceValueWrapper.
      Object value = entry.getModel().getValueAt(entry.getIdentifier().intValue(), 1);
      String attributeName;

      if (value instanceof TableLabel) {
        return false;
      }
      if (value instanceof EditedStyleItem) {
        attributeName = ((EditedStyleItem)value).getQualifiedName();
      }
      else {
       attributeName = value.toString();
      }

      ThemeEditorStyle selectedTheme = getSelectedStyle();
      if (selectedTheme == null) {
        LOG.error("No theme selected.");
        return false;
      }

      return filterAttributes.contains(attributeName);
    }
  }

  @Override
  public void setUI(PanelUI ui) {
    super.setUI(ui);
    updateUiParameters();
  }

  private void updateUiParameters() {
    Font regularFont = UIUtil.getLabelFont();

    int regularFontSize = getFontMetrics(regularFont).getHeight();
    myHeaderFont = regularFont.deriveFont(regularFontSize * HEADER_FONT_SCALE);
    if (myAttributesTable == null) {
      return;
    }

    int headerFontSize = getFontMetrics(myHeaderFont).getHeight();

    // Big cells contain two lines of text, and we want some space between them
    // (thus multiplier is 2.8 rather than 2). Also, we need some padding on top and bottom.
    int bigCellSize = (int) Math.floor(2.8f * regularFontSize) + LARGE_CELL_PADDING;

    myAttributesTable.setClassHeights(ImmutableMap.of(
      Object.class, regularFontSize + REGULAR_CELL_PADDING,
      Color.class, bigCellSize,
      DrawableDomElement.class, bigCellSize,
      TableLabel.class, headerFontSize + LARGE_CELL_PADDING
    ));
  }
}
