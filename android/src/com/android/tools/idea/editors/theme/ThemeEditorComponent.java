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
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.configurations.DeviceMenuAction;
import com.android.tools.idea.configurations.LocaleMenuAction;
import com.android.tools.idea.configurations.OrientationMenuAction;
import com.android.tools.idea.configurations.TargetMenuAction;
import com.android.tools.idea.configurations.ThemeSelectionDialog;
import com.android.tools.idea.editors.theme.attributes.AttributesGrouper;
import com.android.tools.idea.editors.theme.attributes.AttributesModelColorPaletteModel;
import com.android.tools.idea.editors.theme.attributes.AttributesTableModel;
import com.android.tools.idea.editors.theme.attributes.TableLabel;
import com.android.tools.idea.editors.theme.attributes.editors.AttributeReferenceRendererEditor;
import com.android.tools.idea.editors.theme.attributes.editors.BooleanRendererEditor;
import com.android.tools.idea.editors.theme.attributes.editors.ColorRendererEditor;
import com.android.tools.idea.editors.theme.attributes.editors.DelegatingCellEditor;
import com.android.tools.idea.editors.theme.attributes.editors.DelegatingCellRenderer;
import com.android.tools.idea.editors.theme.attributes.editors.DrawableRendererEditor;
import com.android.tools.idea.editors.theme.attributes.editors.EnumRendererEditor;
import com.android.tools.idea.editors.theme.attributes.editors.FlagRendererEditor;
import com.android.tools.idea.editors.theme.attributes.editors.IntegerRenderer;
import com.android.tools.idea.editors.theme.attributes.editors.ParentRendererEditor;
import com.android.tools.idea.editors.theme.attributes.editors.StyleListCellRenderer;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.android.tools.idea.editors.theme.preview.AndroidThemePreviewPanel;
import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.rendering.ResourceNotificationManager;
import com.android.tools.idea.rendering.ResourceNotificationManager.ResourceChangeListener;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.ui.JBColor;
import com.intellij.ui.MutableCollectionComboBoxModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.dom.drawable.DrawableDomElement;
import org.jetbrains.android.dom.resources.Flag;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.plaf.PanelUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class ThemeEditorComponent extends Splitter {
  private static final Logger LOG = Logger.getInstance(ThemeEditorComponent.class);

  private static final JBColor PREVIEW_BACKGROUND = new JBColor(new Color(0xFAFAFA), new Color(0x606162));

  public static final float HEADER_FONT_SCALE = 1.3f;
  public static final int REGULAR_CELL_PADDING = 4;
  public static final int LARGE_CELL_PADDING = 10;
  private final Project myProject;

  private Font myHeaderFont;

  private EditedStyleItem mySubStyleSourceAttribute;

  // Name of current selected Theme
  private String myThemeName;
  // Name of current selected subStyle within the theme
  private String mySubStyleName;

  // Subcomponents
  private final ThemeEditorContext myThemeEditorContext;
  private final AndroidThemePreviewPanel myPreviewPanel;

  private final StyleAttributesFilter myAttributesFilter;
  private TableRowSorter<AttributesTableModel> myAttributesSorter;
  private final SimpleModeFilter mySimpleModeFilter;

  private final AttributesPanel myPanel;
  private final ThemeEditorTable myAttributesTable;

  private final ResourceChangeListener myResourceChangeListener;
  private boolean myIsSubscribedResourceNotification;
  private final GoToListener myGoToListener;
  private MutableCollectionComboBoxModel<Module> myModuleComboModel;

  public interface GoToListener {
    void goTo(@NotNull EditedStyleItem value);
    void goToParent();
  }

  private AttributesTableModel myModel;

  public ThemeEditorComponent(@NotNull final Project project) {
    myProject = project;
    myPanel = new AttributesPanel();
    myAttributesTable = myPanel.getAttributesTable();

    initializeModulesCombo(null);

    final JComboBox moduleCombo = myPanel.getModuleCombo();
    moduleCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        reload(myThemeName, mySubStyleName, getSelectedModule().getName());
      }
    });

    final Module selectedModule = myModuleComboModel.getSelected();
    assert selectedModule != null;

    final Configuration configuration = ThemeEditorUtils.getConfigurationForModule(selectedModule);

    myThemeEditorContext = new ThemeEditorContext(configuration);
    myThemeEditorContext.addConfigurationListener(new ConfigurationListener() {
      @Override
      public boolean changed(int flags) {
        // reloads the theme editor preview when the configuration folder is updated
        if ((flags & MASK_FOLDERCONFIG) != 0) {
          loadStyleAttributes();
          myThemeEditorContext.getConfiguration().save();
        }

        return true;
      }
    });
    myAttributesTable.setContext(myThemeEditorContext);

    myPreviewPanel = new AndroidThemePreviewPanel(myThemeEditorContext, PREVIEW_BACKGROUND);
    myPreviewPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

    ResourcesCompletionProvider completionProvider = new ResourcesCompletionProvider(myThemeEditorContext);
    myGoToListener = new GoToListener() {
      @Override
      public void goTo(@NotNull EditedStyleItem value) {
        ResourceResolver resolver = myThemeEditorContext.getResourceResolver();
        if (value.isAttr() && getUsedStyle() != null && resolver != null) {
          // We need to resolve the theme attribute.
          // TODO: Do we need a full resolution or can we just try to get it from the StyleWrapper?
          ItemResourceValue resourceValue = (ItemResourceValue)resolver.findResValue(value.getValue(), false);
          if (resourceValue == null) {
            LOG.error("Unable to resolve " + value.getValue());
            return;
          }

          mySubStyleName = ResolutionUtils.getQualifiedValue(resourceValue);
        }
        else {
          mySubStyleName = value.getValue();
        }
        mySubStyleSourceAttribute = value;
        loadStyleAttributes();
      }

      @Override
      public void goToParent() {
        ThemeEditorComponent.this.goToParent();
      }
    };
    myAttributesTable.setGoToListener(myGoToListener);
    final AttributeReferenceRendererEditor styleEditor = new AttributeReferenceRendererEditor(project, completionProvider);

    final JScrollPane scroll = myPanel.getAttributesScrollPane();
    scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)); // the scroll pane should fill all available space

    myAttributesTable.setBackground(null); // Get rid of default white background of the table.
    scroll.setBackground(null); // needed for OS X, as by default is set to white
    scroll.getViewport().setBackground(null); // needed for OS X, as by default is set to white

    myAttributesTable.setDefaultRenderer(Color.class, new DelegatingCellRenderer(new ColorRendererEditor(myThemeEditorContext, myPreviewPanel, false)));
    myAttributesTable.setDefaultRenderer(EditedStyleItem.class, new DelegatingCellRenderer(new AttributeReferenceRendererEditor(project, completionProvider)));
    myAttributesTable.setDefaultRenderer(ThemeEditorStyle.class, new DelegatingCellRenderer(new AttributeReferenceRendererEditor(project, completionProvider)));
    myAttributesTable.setDefaultRenderer(String.class, new DelegatingCellRenderer(myAttributesTable.getDefaultRenderer(String.class)));
    myAttributesTable.setDefaultRenderer(Integer.class, new DelegatingCellRenderer(new IntegerRenderer()));
    myAttributesTable.setDefaultRenderer(Boolean.class, new DelegatingCellRenderer(new BooleanRendererEditor(myThemeEditorContext)));
    myAttributesTable.setDefaultRenderer(Enum.class, new DelegatingCellRenderer(new EnumRendererEditor()));
    myAttributesTable.setDefaultRenderer(Flag.class, new DelegatingCellRenderer(new FlagRendererEditor()));
    myAttributesTable.setDefaultRenderer(AttributesTableModel.ParentAttribute.class, new DelegatingCellRenderer(new ParentRendererEditor(myThemeEditorContext)));
    myAttributesTable.setDefaultRenderer(DrawableDomElement.class, new DelegatingCellRenderer(new DrawableRendererEditor(myThemeEditorContext, myPreviewPanel, false)));
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

    myAttributesTable.setDefaultEditor(Color.class, new DelegatingCellEditor(false, new ColorRendererEditor(myThemeEditorContext, myPreviewPanel, true)));
    myAttributesTable.setDefaultEditor(EditedStyleItem.class, new DelegatingCellEditor(false, new AttributeReferenceRendererEditor(project, completionProvider)));
    myAttributesTable.setDefaultEditor(String.class, new DelegatingCellEditor(false, myAttributesTable.getDefaultEditor(String.class)));
    myAttributesTable.setDefaultEditor(Integer.class, new DelegatingCellEditor(myAttributesTable.getDefaultEditor(Integer.class)));
    myAttributesTable.setDefaultEditor(Boolean.class, new DelegatingCellEditor(false, new BooleanRendererEditor(myThemeEditorContext)));
    myAttributesTable.setDefaultEditor(Enum.class, new DelegatingCellEditor(false, new EnumRendererEditor()));
    myAttributesTable.setDefaultEditor(Flag.class, new DelegatingCellEditor(false, new FlagRendererEditor()));
    myAttributesTable.setDefaultEditor(AttributesTableModel.ParentAttribute.class, new DelegatingCellEditor(false, new ParentRendererEditor(myThemeEditorContext)));

    // We allow to edit style pointers as Strings.
    myAttributesTable.setDefaultEditor(ThemeEditorStyle.class, new DelegatingCellEditor(false, styleEditor));
    myAttributesTable.setDefaultEditor(DrawableDomElement.class, new DelegatingCellEditor(false, new DrawableRendererEditor(myThemeEditorContext, myPreviewPanel, true)));

    // We shouldn't allow autoCreateColumnsFromModel, because when setModel() will be invoked, it removes
    // existing listeners to cell editors.
    myAttributesTable.setAutoCreateColumnsFromModel(false);
    for (int c = 0; c < AttributesTableModel.COL_COUNT; ++c) {
      myAttributesTable.addColumn(new TableColumn(c));
    }

    updateUiParameters();

    myAttributesFilter = new StyleAttributesFilter();
    mySimpleModeFilter = new SimpleModeFilter();

    myPanel.getBackButton().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        mySubStyleName = null;
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

        configureFilter();

        ((TableRowSorter)myAttributesTable.getRowSorter()).sort();
        myAttributesTable.updateRowHeights();
      }
    });

    // We have our own custom renderer that it's not based on the default one.
    //noinspection GtkPreferredJComboBoxRenderer
    myPanel.getThemeCombo().setRenderer(new StyleListCellRenderer(myThemeEditorContext));
    myPanel.getThemeCombo().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myPanel.isCreateNewThemeSelected()) {
          if (!createNewTheme()) {
            // User clicked "cancel", restore previously selected item in themes combo.
            myPanel.setSelectedTheme(getSelectedTheme());
          }
        }
        else if (myPanel.isShowAllThemesSelected()) {
          if (!selectNewTheme()) {
            myPanel.setSelectedTheme(getSelectedTheme());
          }
        }
        else if (myPanel.isRenameSelected()) {
          if (!renameTheme()) {
            myPanel.setSelectedTheme(getSelectedTheme());
          }
        }
        else {
          Object item = myPanel.getThemeCombo().getSelectedItem();
          final ThemeEditorStyle theme = (ThemeEditorStyle)item;
          assert theme != null;

          myThemeName = theme.getQualifiedName();
          mySubStyleName = null;
          mySubStyleSourceAttribute = null;

          loadStyleAttributes();
        }
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
    group.add(new OrientationMenuAction(myPreviewPanel, false));
    group.add(new DeviceMenuAction(myPreviewPanel, false));
    group.add(new TargetMenuAction(myPreviewPanel, true, false));
    group.add(new LocaleMenuAction(myPreviewPanel, false));

    ActionManager actionManager = ActionManager.getInstance();
    ActionToolbar actionToolbar = actionManager.createActionToolbar("ThemeToolbar", group, true);
    actionToolbar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);
    JPanel myConfigToolbar = myPanel.getConfigToolbar();
    myConfigToolbar.add(actionToolbar.getComponent());

    setFirstComponent(myPreviewPanel);
    setSecondComponent(myPanel.getRightPanel());
    setShowDividerControls(false);

    myResourceChangeListener = new ResourceChangeListener() {
      @Override
      public void resourcesChanged(@NotNull Set<ResourceNotificationManager.Reason> reason) {
        myThemeEditorContext.updateThemeResolver();
        reload(myThemeName, mySubStyleName);
      }
    };

    // Set an initial state in case that the editor didn't have a previously saved state
    // TODO: Try to be smarter about this and get the ThemeEditor to set a default state where there is no previous state
    reload(null);
  }

  @NotNull
  public Module getSelectedModule() {
    final Module module = myModuleComboModel.getSelected();
    assert module != null;

    return module;
  }

  private void initializeModulesCombo(@Nullable String defaultModuleName) {
    final ImmutableList<Module> modules = ThemeEditorUtils.findAndroidModules(myProject);
    assert modules.size() > 0 : "Theme Editor shouldn't be launched in a project with no Android modules";

    Module defaultModule = null;
    for (Module module : modules) {
      if (module.getName().equals(defaultModuleName)) {
        defaultModule = module;
        break;
      }
    }

    if (defaultModule == null) {
      myModuleComboModel = new MutableCollectionComboBoxModel<Module>(modules);
    }
    else {
      myModuleComboModel = new MutableCollectionComboBoxModel<Module>(modules, defaultModule);
    }

    final JComboBox moduleCombo = myPanel.getModuleCombo();
    moduleCombo.setModel(myModuleComboModel);
  }

  /**
   * Subscribes myResourceChangeListener to ResourceNotificationManager with current AndroidFacet.
   * By subscribing, myResourceChangeListener can track all internal and external changes in resources.
   */
  private void subscribeResourceNotification() {
    // Already subscribed, we check this, because sometimes selectNotify can be called twice
    if (myIsSubscribedResourceNotification) {
      return;
    }
    ResourceNotificationManager manager = ResourceNotificationManager.getInstance(myThemeEditorContext.getProject());
    AndroidFacet facet = AndroidFacet.getInstance(myThemeEditorContext.getCurrentContextModule());
    assert facet != null : myThemeEditorContext.getCurrentContextModule().getName() + " module doesn't have an AndroidFacet";
    manager.addListener(myResourceChangeListener, facet, null, null);
    myIsSubscribedResourceNotification = true;
  }

  /**
   * Unsubscribes myResourceChangeListener from ResourceNotificationManager with current AndroidFacet.
   */
  private void unsubscribeResourceNotification() {
    if (myIsSubscribedResourceNotification) {
      ResourceNotificationManager manager = ResourceNotificationManager.getInstance(myThemeEditorContext.getProject());
      AndroidFacet facet = AndroidFacet.getInstance(myThemeEditorContext.getCurrentContextModule());
      assert facet != null : myThemeEditorContext.getCurrentContextModule().getName() + " module doesn't have an AndroidFacet";
      manager.removeListener(myResourceChangeListener, facet, null, null);
      myIsSubscribedResourceNotification = false;
    }
  }

  /**
   * @see FileEditor#selectNotify().
   */
  public void selectNotify() {
    reload(myThemeName, mySubStyleName);
    subscribeResourceNotification();
  }

  /**
   * @see FileEditor#deselectNotify().
   */
  public void deselectNotify() {
    unsubscribeResourceNotification();
  }

  private void configureFilter() {
    if (myPanel.isAdvancedMode()) {
      myAttributesFilter.setFilterEnabled(false);
      myAttributesSorter.setRowFilter(myAttributesFilter);
    } else {
      mySimpleModeFilter.configure(myModel.getDefinedAttributes());
      myAttributesSorter.setRowFilter(mySimpleModeFilter);
    }
  }

  /**
   * Launches dialog to create a new theme based on selected one.
   * @return whether creation of new theme succeeded.
   */
  private boolean createNewTheme() {
    String newThemeName = ThemeEditorUtils.createNewStyle(getSelectedTheme(), null, null, myThemeEditorContext, !isSubStyleSelected(), null);
    if (newThemeName != null) {
      // We don't need to call reload here, because myResourceChangeListener will take care of it
      myThemeName = newThemeName;
      mySubStyleName = null;
      return true;
    }
    return false;
  }

  /**
   * Launches dialog to choose a theme among all existing ones
   * @return whether the choice is valid
   */
  private boolean selectNewTheme() {
    ThemeSelectionDialog dialog = new ThemeSelectionDialog(myThemeEditorContext.getConfiguration());
    if (dialog.showAndGet()) {
      String newThemeName = dialog.getTheme();
      if (newThemeName != null) {
        // TODO: call loadStyleProperties instead
        reload(newThemeName);
        return true;
      }
    }
    return false;
  }

  /**
   * Uses Android Studio refactoring to rename the current theme
   * @return Whether the renaming is successful
   */
  private boolean renameTheme() {
    ThemeEditorStyle selectedTheme = getSelectedTheme();
    assert selectedTheme != null;
    assert selectedTheme.isProjectStyle();
    PsiElement namePsiElement = selectedTheme.getNamePsiElement();
    if (namePsiElement == null) {
      return false;
    }
    RenameDialog renameDialog = new RenameDialog(myThemeEditorContext.getProject(), namePsiElement, null, null);
    renameDialog.show();
    if (renameDialog.isOK()) {
      String newName = renameDialog.getNewName();
      String newQualifiedName = selectedTheme.getQualifiedName().replace(selectedTheme.getName(), newName);
      // We don't need to call reload here, because myResourceChangeListener will take care of it
      myThemeName = newQualifiedName;
      mySubStyleName = null;
      return true;
    }
    return false;
  }

  public void goToParent() {
    ThemeEditorStyle selectedStyle = getUsedStyle();
    if (selectedStyle == null) {
      LOG.error("No style selected.");
      return;
    }

    ThemeEditorStyle parent = getUsedStyle().getParent(myThemeEditorContext.getThemeResolver());
    assert parent != null;

    // TODO: This seems like it could be confusing for users, we might want to differentiate parent navigation depending if it's
    // substyle or theme navigation.
    if (isSubStyleSelected()) {
      mySubStyleName = parent.getQualifiedName();
      loadStyleAttributes();
    }
    else {
      myPanel.setSelectedTheme(parent);
    }
  }

  @Nullable
  ThemeEditorStyle getSelectedTheme() {
    if (myThemeName == null) {
      return null;
    }
    return myThemeEditorContext.getThemeResolver().getTheme(myThemeName);
  }

  @Nullable
  private ThemeEditorStyle getUsedStyle() {
    if (mySubStyleName != null) {
      return getCurrentSubStyle();
    }

    return getSelectedTheme();
  }

  @Nullable
  ThemeEditorStyle getCurrentSubStyle() {
    if (mySubStyleName == null) {
      return null;
    }
    return myThemeEditorContext.getThemeResolver().getTheme(mySubStyleName);
  }

  private boolean isSubStyleSelected() {
    return mySubStyleName != null;
  }

  // Never null, because the list of elements of attGroup is constant and never changed
  @NotNull
  private AttributesGrouper.GroupBy getSelectedAttrGroup() {
    return (AttributesGrouper.GroupBy)myPanel.getAttrGroupCombo().getSelectedItem();
  }

  /**
   * Sets a new value to the passed attribute. It will also trigger the reload if a change happened.
   * @param rv The attribute to set, including the current value.
   * @param strValue The new value.
   */
  private void createNewThemeWithAttributeValue(@NotNull EditedStyleItem rv, @NotNull String strValue) {
    if (strValue.equals(rv.getValue())) {
      // No modification required.
      return;
    }

    ThemeEditorStyle selectedStyle = getUsedStyle();
    if (selectedStyle == null) {
      LOG.error("No style/theme selected.");
      return;
    }

    // The current style is R/O so we need to propagate this change a new style.
    String message = String
      .format("<html>The %1$s '<code>%2$s</code>' is Read-Only.<br/>A new %1$s will be created to modify '<code>%3$s</code>'.<br/></html>",
              isSubStyleSelected() ? "style" : "theme", selectedStyle.getQualifiedName(), rv.getName());

    String newStyleName = ThemeEditorUtils.createNewStyle(selectedStyle, rv.getQualifiedName(), strValue, myThemeEditorContext, !isSubStyleSelected(), message);

    if (newStyleName == null) {
      return;
    }

    if (!isSubStyleSelected()) {
      // We changed a theme, so we are done.
      // We don't need to call reload, because myResourceChangeListener will take care of it
      myThemeName = newStyleName;
      mySubStyleName = null;
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
      message = String.format("<html>The style '%1$s' which references to '%2$s' is also Read-Only.<br/>" +
                              "A new theme will be created to point to the modified style '%3$s'.<br/></html>",
                              selectedTheme.getQualifiedName(), rv.getName(), newStyleName);

      String newThemeName = ThemeEditorUtils.createNewStyle(selectedTheme, sourcePropertyName, newStyleName, myThemeEditorContext, true, message);
      if (newThemeName != null) {
        // We don't need to call reload, because myResourceChangeListener will take care of it
        myThemeName = newThemeName;
        mySubStyleName = newStyleName;
      }
    }
    else {
      // The theme pointing to the new style is writable, so go ahead.
      selectedTheme.setValue(rv.getSelectedValueConfiguration(), sourcePropertyName, newStyleName);
      // We don't need to call reload, because myResourceChangeListener will take care of it
      mySubStyleName = newStyleName;
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
    reload(defaultThemeName, defaultSubStyleName, getSelectedModule().getName());
  }

  public void reload(@Nullable final String defaultThemeName, @Nullable final String defaultSubStyleName, @Nullable final String defaultModuleName) {
    // Unsubscribing from ResourceNotificationManager, because Module might be changed
    unsubscribeResourceNotification();

    initializeModulesCombo(defaultModuleName);
    myThemeEditorContext.setCurrentContextModule(getSelectedModule());

    // Subscribes to ResourceNotificationManager with new facet
    subscribeResourceNotification();

    mySubStyleSourceAttribute = null;

    final ThemeResolver themeResolver = myThemeEditorContext.getThemeResolver();
    final ThemeEditorStyle defaultTheme = defaultThemeName == null ? null : themeResolver.getTheme(defaultThemeName);
    myPanel.getThemeCombo().setModel(new ThemesListModel(myThemeEditorContext, ThemeEditorUtils.getDefaultThemes(themeResolver), defaultTheme));
    myThemeName = (myPanel.getSelectedTheme() == null) ? null : myPanel.getSelectedTheme().getQualifiedName();
    mySubStyleName = (StringUtil.equals(myThemeName,defaultThemeName)) ? defaultSubStyleName : null;
    loadStyleAttributes();
  }

  /**
   * Loads the theme attributes table for the current selected theme or substyle.
   */
  private void loadStyleAttributes() {

    final ThemeEditorStyle selectedTheme = getSelectedTheme();
    final ThemeEditorStyle selectedStyle = getUsedStyle();

    if (selectedTheme == null || selectedStyle == null) {
      LOG.error("No style/theme selected");
      return;
    }

    myThemeEditorContext.setSelectedStyleSourceModule(selectedTheme.getSourceModule());
    myPanel.setSubstyleName(mySubStyleName);
    myPanel.getBackButton().setVisible(mySubStyleName != null);
    final Configuration configuration = myThemeEditorContext.getConfiguration();
    configuration.setTheme(selectedTheme.getQualifiedName());

    assert configuration.getResourceResolver() != null; // ResourceResolver is only null if no theme was set.
    myModel = new AttributesTableModel(selectedStyle, getSelectedAttrGroup());

    myModel.addThemePropertyChangedListener(new AttributesTableModel.ThemePropertyChangedListener() {
      @Override
      public void attributeChangedOnReadOnlyTheme(final EditedStyleItem attribute, final String newValue) {
        createNewThemeWithAttributeValue(attribute, newValue);
      }
    });

    myAttributesTable.setRowSorter(null); // Clean any previous row sorters.
    myAttributesSorter = new TableRowSorter<AttributesTableModel>(myModel);
    configureFilter();

    myAttributesTable.setModel(myModel);
    myAttributesTable.setRowSorter(myAttributesSorter);
    myAttributesTable.updateRowHeights();

    myPanel.getPalette().setModel(new AttributesModelColorPaletteModel(configuration, myModel));
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
          for (EditedStyleItem item : references) {
            attributeNames.add(item.getQualifiedName());
          }
          myAttributesFilter.setAttributesFilter(attributeNames);
          myAttributesFilter.setFilterEnabled(true);
        }
        else {
          myAttributesFilter.setFilterEnabled(false);
        }

        if (myAttributesTable.isEditing()) {
          myAttributesTable.getCellEditor().cancelCellEditing();
        }
        ((TableRowSorter)myAttributesTable.getRowSorter()).sort();
        myPanel.getAdvancedFilterCheckBox().getModel().setSelected(!myAttributesFilter.myIsFilterEnabled);
      }
    });

    myAttributesTable.updateRowHeights();
    myPreviewPanel.invalidateGraphicsRenderer();
    myPreviewPanel.revalidate();
    myAttributesTable.repaint();
  }

  @Override
  public void dispose() {
    // First remove the table editor so that it won't be called after
    // objects it relies on, like the module, have themselves been disposed
    myAttributesTable.removeEditor();
    myThemeEditorContext.dispose();
    super.dispose();
  }

  class SimpleModeFilter extends AttributesFilter {
    public final Set<String> ATTRIBUTES_DEFAULT_FILTER = ImmutableSet
      .of("colorPrimary",
          "colorPrimaryDark",
          "colorAccent",
          "colorForeground",
          "textColorPrimary",
          "textColorSecondary",
          "textColorPrimaryInverse",
          "textColorSecondaryInverse",
          "colorBackground",
          "windowBackground",
          "navigationBarColor",
          "statusBarColor");

    public SimpleModeFilter() {
      myIsFilterEnabled = true;
      filterAttributes = new HashSet<String>();
    }

    public void configure(final Set<String> availableAttributes) {
      filterAttributes.clear();

      for (final String candidate : ATTRIBUTES_DEFAULT_FILTER) {
        if (availableAttributes.contains(candidate)) {
          filterAttributes.add(candidate);
        } else {
          filterAttributes.add(SdkConstants.ANDROID_NS_NAME_PREFIX + candidate);
        }
      }
    }
  }

  abstract class AttributesFilter extends RowFilter<AttributesTableModel, Integer> {
    boolean myIsFilterEnabled;
    Set<String> filterAttributes;

    @Override
    public boolean include(Entry<? extends AttributesTableModel, ? extends Integer> entry) {
      if (!myIsFilterEnabled) {
        return true;
      }
      int row = entry.getIdentifier().intValue();
      if (entry.getModel().isThemeParentRow(row)) {
        return true;
      }

      // We use the column 1 because it's the one that contains the ItemResourceValueWrapper.
      Object value = entry.getModel().getValueAt(row, 1);
      if (value instanceof TableLabel) {
        return false;
      }

      String attributeName;
      if (value instanceof EditedStyleItem) {
        attributeName = ((EditedStyleItem)value).getQualifiedName();
      }
      else {
        attributeName = value.toString();
      }

      ThemeEditorStyle selectedTheme = getUsedStyle();
      if (selectedTheme == null) {
        LOG.error("No theme selected.");
        return false;
      }

      return filterAttributes.contains(attributeName);
    }
  }

  class StyleAttributesFilter extends AttributesFilter {
    public StyleAttributesFilter() {
      myIsFilterEnabled = true;
      filterAttributes = Collections.emptySet();
    }

    public void setFilterEnabled(boolean enabled) {
      this.myIsFilterEnabled = enabled;
    }

    /**
     * Set the attribute names we want to display.
     */
    public void setAttributesFilter(@NotNull Set<String> attributeNames) {
      filterAttributes = ImmutableSet.copyOf(attributeNames);
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

    // The condition below isn't constant, because updateUiParameters() is triggered during
    // construction: constructor of ThemeEditorComponent calls constructor of Splitter, which
    // calls setUI at some point. If this condition is removed, theme editor would fail with
    // NPE during its startup.
    //noinspection ConstantConditions
    if (myAttributesTable == null) {
      return;
    }

    int headerFontSize = getFontMetrics(myHeaderFont).getHeight();

    // We calculate the size of the resource cell (drawable and color cells) by creating a ResourceComponent that
    // we use to measure the preferred size.
    ResourceComponent sampleComponent = new ResourceComponent();
    int bigCellSize = sampleComponent.getPreferredSize().height;
    myAttributesTable.setClassHeights(ImmutableMap.of(
      Object.class, regularFontSize + REGULAR_CELL_PADDING,
      Color.class, bigCellSize,
      DrawableDomElement.class, bigCellSize,
      TableLabel.class, headerFontSize + LARGE_CELL_PADDING
    ));
  }
}
