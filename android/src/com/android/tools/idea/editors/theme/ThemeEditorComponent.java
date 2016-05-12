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
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.*;
import com.android.tools.idea.editors.theme.attributes.AttributesGrouper;
import com.android.tools.idea.editors.theme.attributes.AttributesModelColorPaletteModel;
import com.android.tools.idea.editors.theme.attributes.AttributesTableModel;
import com.android.tools.idea.editors.theme.attributes.TableLabel;
import com.android.tools.idea.editors.theme.attributes.editors.ParentRendererEditor;
import com.android.tools.idea.editors.theme.attributes.editors.StyleListPaletteCellRenderer;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.editors.theme.preview.AndroidThemePreviewPanel;
import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.res.ResourceNotificationManager;
import com.android.tools.idea.res.ResourceNotificationManager.ResourceChangeListener;
import com.google.common.collect.*;
import com.intellij.find.FindManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.ui.*;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.dom.drawable.DrawableDomElement;
import org.jetbrains.android.facet.AndroidFacet;
import com.android.tools.idea.editors.theme.preview.ThemePreviewComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.plaf.PanelUI;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;
import java.util.List;

public class ThemeEditorComponent extends Splitter implements Disposable {
  private static final Logger LOG = Logger.getInstance(ThemeEditorComponent.class);
  private static final DefaultTableModel EMPTY_TABLE_MODEL = new DefaultTableModel();

  public static final JBColor PREVIEW_BACKGROUND = new JBColor(new Color(0xFFFFFF), new Color(0x343739));
  /** Color to use for the preview background when the background color of the theme being displayed is too similar to PREVIEW_BACKGROUND */
  public static final JBColor ALT_PREVIEW_BACKGROUND = new JBColor(new Color(0xFAFAFA), new Color(0x282828));

  private static final ImmutableMap<String, Integer> SORTING_MAP =
    ImmutableMap.<String, Integer>builder()
      .put(MaterialColors.PRIMARY_MATERIAL_ATTR, 1)
      .put(MaterialColors.PRIMARY_DARK_MATERIAL_ATTR, 2)
      .put(MaterialColors.ACCENT_MATERIAL_ATTR, 3)
      .build();

  /**
   * Comparator used for simple mode attribute sorting
   */
  public static final Comparator SIMPLE_MODE_COMPARATOR = new Comparator() {
    @Override
    public int compare(Object o1, Object o2) {
      // The parent attribute goes always first
      if (o1 instanceof ConfiguredThemeEditorStyle) {
        return -1;
      } else if (o2 instanceof ConfiguredThemeEditorStyle) {
        return 1;
      }

      if (o1 instanceof EditedStyleItem && o2 instanceof EditedStyleItem) {
        Integer pos1 = SORTING_MAP.get(((EditedStyleItem)o1).getName());
        Integer pos2 = SORTING_MAP.get(((EditedStyleItem)o2).getName());
        if (pos1 != null && pos2 != null) {
          return pos1 - pos2;
        }
        if (pos1 != null) {
          return -1;
        }
        if (pos2 != null) {
          return 1;
        }
        return ((EditedStyleItem)o1).compareTo((EditedStyleItem)o2);
      }

      // Fall-back for other comparisons
      return Ordering.usingToString().compare(o1, o2);
    }
  };

  public static final int REGULAR_CELL_PADDING = 4;
  public static final int LARGE_CELL_PADDING = 10;
  /**
   * Distance to PREVIEW_BACKGROUND under which the contrast becomes too small to see the cards on screen well enough.
   * As a reference, the distance between the light versions of PREVIEW_BACKGROUND and ALT_PREVIEW_BACKGROUND is 15.0
   */
  public static final float COLOR_DISTANCE_THRESHOLD = 8.5f;
  private final Project myProject;

  private EditedStyleItem mySubStyleSourceAttribute;

  // Name of current selected Theme
  @Nullable private String myThemeName;
  // Name of current selected subStyle within the theme
  @Nullable private String mySubStyleName;

  // Subcomponents
  private final ThemeEditorContext myThemeEditorContext;
  private final ThemePreviewComponent myPreviewComponent;

  private final StyleAttributesFilter myAttributesFilter;
  private TableRowSorter<AttributesTableModel> myAttributesSorter;
  private final SimpleModeFilter mySimpleModeFilter;

  private final AttributesPanel myPanel;
  private final ThemeEditorTable myAttributesTable;

  private final ResourceChangeListener myResourceChangeListener;
  private boolean myIsSubscribedResourceNotification;

  private final ThemeSelectionPanel.ThemeChangedListener myThemeChangedListener = new ThemeSelectionPanel.ThemeChangedListener() {
    @Override
    public void themeChanged(@NotNull String name) {
      refreshPreviewPanel(name);
    }
  };

  private MutableCollectionComboBoxModel<Module> myModuleComboModel;

  private String myPreviewThemeName;

  public interface GoToListener {
    void goTo(@NotNull EditedStyleItem value);
    void goToParent();
  }

  private AttributesTableModel myModel;
  private SwingWorker<Boolean, Object> mySwingWorker;

  public ThemeEditorComponent(@NotNull final Project project) {
    myProject = project;
    myPanel = new AttributesPanel();

    initializeModulesCombo(null);

    myPanel.addModuleChangedActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        reload(myThemeName, mySubStyleName, getSelectedModule().getName());
      }
    });

    final Module selectedModule = myModuleComboModel.getSelected();
    assert selectedModule != null && !selectedModule.isDisposed();

    final Configuration configuration = ThemeEditorUtils.getConfigurationForModule(selectedModule);

    myThemeEditorContext = new ThemeEditorContext(configuration);
    Disposer.register(this, myThemeEditorContext);
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

    myPreviewComponent = new ThemePreviewComponent(myThemeEditorContext);
    Disposer.register(this, myPreviewComponent);

    GoToListener goToListener = new GoToListener() {
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

    myAttributesTable = myPanel.getAttributesTable();
    ParentRendererEditor.ThemeParentChangedListener themeParentChangedListener = new ParentRendererEditor.ThemeParentChangedListener() {
      /** Stores all the {@link ItemResourceValue} items of a theme
       *  so that it can be restored to its original state after having been modified */
      private final List<ItemResourceValue> myOriginalItems = Lists.newArrayList();
      private String myModifiedParent;

      /**
       * Restores a modified theme with its original content
       */
      private void restoreOriginalTheme(@NotNull ConfiguredThemeEditorStyle modifiedTheme, @NotNull List<ItemResourceValue> originalItems) {
        StyleResourceValue modifiedResourceValue = modifiedTheme.getStyleResourceValue();
        StyleResourceValue restoredResourceValue =
          new StyleResourceValue(ResourceType.STYLE, modifiedResourceValue.getName(), modifiedResourceValue.getParentStyle(),
                                 modifiedResourceValue.isFramework());
        for (ItemResourceValue item : originalItems) {
          restoredResourceValue.addItem(item);
        }
        modifiedResourceValue.replaceWith(restoredResourceValue);
      }

      @Override
      public void themeChanged(@NotNull final String name) {
        ThemeResolver themeResolver = myThemeEditorContext.getThemeResolver();
        if (myModifiedParent != null) {
          ConfiguredThemeEditorStyle modifiedTheme = themeResolver.getTheme(myModifiedParent);
          assert modifiedTheme != null;
          restoreOriginalTheme(modifiedTheme, myOriginalItems);
        }

        myModifiedParent = name;
        ConfiguredThemeEditorStyle newParent = themeResolver.getTheme(name);
        assert newParent != null;
        StyleResourceValue newParentStyleResourceValue = newParent.getStyleResourceValue();

        // Store the content of the theme newParent so that it can be restored later
        myOriginalItems.clear();
        myOriginalItems.addAll(newParentStyleResourceValue.getValues());

        assert myThemeName != null; // theme changed, so there was a previous theme in myThemeName
        ConfiguredThemeEditorStyle myCurrentTheme = themeResolver.getTheme(myThemeName);
        assert myCurrentTheme != null;
        // Add myCurrentTheme attributes to newParent, so that newParent becomes equivalent to having changed the parent of myCurrentTheme
        for (ItemResourceValue item : myCurrentTheme.getStyleResourceValue().getValues()) {
          newParentStyleResourceValue.addItem(item);
        }

        myPreviewThemeName = null;
        refreshPreviewPanel(name);
      }

      /**
       * Restores the last modified theme to its original content, and reloads the preview with the currently selected theme
       */
      @Override
      public void reset() {
        ThemeResolver themeResolver = myThemeEditorContext.getThemeResolver();
        if (myModifiedParent != null) {
          ConfiguredThemeEditorStyle modifiedTheme = themeResolver.getTheme(myModifiedParent);
          assert modifiedTheme != null;
          restoreOriginalTheme(modifiedTheme, myOriginalItems);
        }
        myModifiedParent = null;
        myOriginalItems.clear();
        if (myThemeName != null) {
          refreshPreviewPanel(myThemeName);
        }
      }
    };
    myAttributesTable.customizeTable(myThemeEditorContext, myPreviewComponent.getPreviewPanel(), themeParentChangedListener);
    myAttributesTable.setGoToListener(goToListener);

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

    myPanel.getThemeCombo()
      .setRenderer(new StyleListPaletteCellRenderer(myThemeEditorContext, myThemeChangedListener, myPanel.getThemeCombo()));

    myPanel.getThemeCombo().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String selectedItem = (String)myPanel.getThemeCombo().getSelectedItem();
        if (!ThemesListModel.isSpecialOption(selectedItem)) {
          myThemeName = selectedItem;
          myPreviewThemeName = null;
          mySubStyleName = null;
          mySubStyleSourceAttribute = null;

          loadStyleAttributes();
        }
        else {
          // Keep current theme name in combo box
          myPanel.setSelectedTheme(myThemeName);

          if (ThemesListModel.CREATE_NEW_THEME.equals(selectedItem)) {
            createNewTheme();
          }
          else if (ThemesListModel.SHOW_ALL_THEMES.equals(selectedItem)) {
            selectNewTheme();
          }
          else {
            renameTheme();
          }
        }
      }
    });

    myPanel.getThemeCombo().addPopupMenuListener(new PopupMenuListenerAdapter() {
      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent popupMenuEvent) {
        // myThemeName could be null if no theme is available. This will happen only in some edge cases like no SDK available.
        if (myThemeName != null) {
          refreshPreviewPanel(myThemeName);
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

    setFirstComponent(myPreviewComponent);
    setSecondComponent(myPanel.getRightPanel());
    setShowDividerControls(false);
    setProportion(0.67f);

    myResourceChangeListener = new ResourceChangeListener() {
      @Override
      public void resourcesChanged(@NotNull Set<ResourceNotificationManager.Reason> reason) {
        myThemeEditorContext.updateThemeResolver();
        reload(myThemeName, mySubStyleName);
      }
    };

    JPopupMenu themeNamePopupMenu = new JPopupMenu();
    themeNamePopupMenu.add(new JMenuItem(new AbstractAction("Find Usages") {
      @Override
      public void actionPerformed(ActionEvent e) {
        ConfiguredThemeEditorStyle style = getUsedStyle();
        assert style != null; // we always have something selected
        if (style.isProjectStyle()) {
          PsiElement name = style.getNamePsiElement();
          assert name != null; // name is never null for project styles
          FindManager.getInstance(myProject).findUsages(name);
        }
        // TODO this should also work for framework styles, but currently does not work even from xml files.
      }
    }));
    myPanel.setThemeNamePopupMenu(themeNamePopupMenu);

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
      assert !module.isDisposed();
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
    myPanel.setModuleModel(myModuleComboModel);
  }

  /**
   * Unsubscribe {@link #myResourceChangeListener} from the from the {@link ResourceNotificationManager} for the given {@link AndroidFacet}
   */
  private void unsubscribeResourceNotification(@NotNull ResourceNotificationManager manager, @NotNull AndroidFacet facet) {
    if (myIsSubscribedResourceNotification) {
      manager.removeListener(myResourceChangeListener, facet, null, null);
      myIsSubscribedResourceNotification = false;
    }
  }

  /**
   * Unsubscribe {@link #myResourceChangeListener}] from ResourceNotificationManager with current AndroidFacet.
   */
  private void unsubscribeResourceNotification() {
    if (myThemeEditorContext.getCurrentContextModule().isDisposed()) {
      // The subscription is automatically removed when the module is disposed.
      return;
    }

    ResourceNotificationManager manager = ResourceNotificationManager.getInstance(myThemeEditorContext.getProject());
    AndroidFacet facet = AndroidFacet.getInstance(myThemeEditorContext.getCurrentContextModule());
    assert facet != null : myThemeEditorContext.getCurrentContextModule().getName() + " module doesn't have an AndroidFacet";
    unsubscribeResourceNotification(manager, facet);
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

    final ResourceNotificationManager manager = ResourceNotificationManager.getInstance(myThemeEditorContext.getProject());
    final AndroidFacet facet = AndroidFacet.getInstance(myThemeEditorContext.getCurrentContextModule());
    assert facet != null : myThemeEditorContext.getCurrentContextModule().getName() + " module doesn't have an AndroidFacet";
    manager.addListener(myResourceChangeListener, facet, null, null);
    myIsSubscribedResourceNotification = true;

    Disposer.register(facet, new Disposable() {
      @Override
      public void dispose() {
        // If the module is disposed, remove subscription
        unsubscribeResourceNotification(manager, facet);
      }
    });
  }

  /**
   * @see FileEditor#selectNotify().
   */
  public void selectNotify() {
    // The call to selectNotify might be holding the write lock. Call reload later to avoid rendering while holding the lock
    ApplicationManager.getApplication().invokeLater(() -> {
      // Since it is called inside an invokeLater, check first that the module has not been disposed before calling reload
      if (!myThemeEditorContext.getCurrentContextModule().isDisposed()) {
        reload(myThemeName, mySubStyleName);
      }
    });
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
      myAttributesSorter.setSortKeys(null);
    } else {
      mySimpleModeFilter
        .configure(myModel.getDefinedAttributes(), ThemeEditorUtils.isSelectedAppCompatTheme(myThemeEditorContext));
      myAttributesSorter.setRowFilter(mySimpleModeFilter);
      myAttributesSorter.setSortKeys(ImmutableList.of(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
    }
  }

  /**
   * Launches dialog to create a new theme based on selected one.
   */
  private void createNewTheme() {
    String newThemeName = ThemeEditorUtils
      .showCreateNewStyleDialog(getSelectedTheme(), myThemeEditorContext, !isSubStyleSelected(), true, null, myThemeChangedListener);
    if (newThemeName != null) {
      // We don't need to call reload here, because myResourceChangeListener will take care of it
      myThemeName = newThemeName;
      mySubStyleName = null;
    }
    else if (myThemeName != null) {
      // No new theme was created, we restore the preview to its original state
      refreshPreviewPanel(myThemeName);
    }
  }

  /**
   * Launches dialog to choose a theme among all existing ones
   */
  private void selectNewTheme() {
    ThemeSelectionDialog dialog = new ThemeSelectionDialog(myThemeEditorContext.getConfiguration());
    dialog.setThemeChangedListener(myThemeChangedListener);
    boolean themeSelected = dialog.showAndGet();

    if (themeSelected) {
      String newThemeName = dialog.getTheme();
      if (newThemeName != null) {
        // TODO: call loadStyleProperties instead
        reload(newThemeName);
      }
    }
    if (myThemeName != null) {
      refreshPreviewPanel(myThemeName);
    }
  }

  /**
   * Uses Android Studio refactoring to rename the current theme
   */
  private void renameTheme() {
    ConfiguredThemeEditorStyle selectedTheme = getSelectedTheme();
    assert selectedTheme != null;
    assert selectedTheme.isProjectStyle();
    PsiElement namePsiElement = selectedTheme.getNamePsiElement();
    if (namePsiElement == null) {
      return;
    }
    RenameDialog renameDialog = new RenameDialog(myThemeEditorContext.getProject(), namePsiElement, null, null);
    renameDialog.show();
    if (renameDialog.isOK()) {
      String newName = renameDialog.getNewName();
      // We don't need to call reload here, because myResourceChangeListener will take care of it
      myThemeName = selectedTheme.getQualifiedName().replace(selectedTheme.getName(), newName);
      mySubStyleName = null;
    }
  }

  public void goToParent() {
    ConfiguredThemeEditorStyle selectedStyle = getUsedStyle();
    if (selectedStyle == null) {
      LOG.error("No style selected.");
      return;
    }

    ConfiguredThemeEditorStyle parent = getUsedStyle().getParent(myThemeEditorContext.getThemeResolver());
    assert parent != null;

    // TODO: This seems like it could be confusing for users, we might want to differentiate parent navigation depending if it's
    // substyle or theme navigation.
    if (isSubStyleSelected()) {
      mySubStyleName = parent.getQualifiedName();
      loadStyleAttributes();
    }
    else {
      myPanel.setSelectedTheme(parent.getQualifiedName());
    }
  }

  @Nullable
  ConfiguredThemeEditorStyle getPreviewTheme() {
    if (myPreviewThemeName != null) {
      return myThemeEditorContext.getThemeResolver().getTheme(myPreviewThemeName);
    }
    return null;
  }

  @Nullable
  ConfiguredThemeEditorStyle getSelectedTheme() {
    if (myThemeName != null) {
      return myThemeEditorContext.getThemeResolver().getTheme(myThemeName);
    }
    return null;
  }

  @Nullable
  private ConfiguredThemeEditorStyle getUsedStyle() {
    if (mySubStyleName != null) {
      return getCurrentSubStyle();
    }

    return getSelectedTheme();
  }

  @Nullable
  ConfiguredThemeEditorStyle getCurrentSubStyle() {
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
  private void createNewThemeWithAttributeValue(@NotNull final EditedStyleItem rv, @NotNull final String strValue) {
    if (strValue.equals(rv.getValue())) {
      // No modification required.
      return;
    }

    ConfiguredThemeEditorStyle selectedStyle = getUsedStyle();
    if (selectedStyle == null) {
      LOG.error("No style/theme selected.");
      return;
    }

    // The current style is R/O so we need to propagate this change a new style.
    boolean isSubStyleSelected = isSubStyleSelected();
    String message = String
      .format("<html>The %1$s '<code>%2$s</code>' is Read-Only.<br/>A new %1$s will be created to modify '<code>%3$s</code>'.<br/></html>",
              isSubStyleSelected ? "style" : "theme", selectedStyle.getQualifiedName(), rv.getName());

    final ItemResourceValue originalValue = rv.getSelectedValue();
    ParentRendererEditor.ThemeParentChangedListener themeListener = new ParentRendererEditor.ThemeParentChangedListener() {
      private ConfiguredThemeEditorStyle myModifiedTheme;

      @Override
      public void themeChanged(@NotNull String name) {
        if (myModifiedTheme != null) {
          myModifiedTheme.getStyleResourceValue().addItem(originalValue);
        }

        myModifiedTheme = myThemeEditorContext.getThemeResolver().getTheme(name);
        assert myModifiedTheme != null;
        ItemResourceValue newSelectedValue =
          new ItemResourceValue(originalValue.getName(), originalValue.isFrameworkAttr(), strValue, false);
        myModifiedTheme.getStyleResourceValue().addItem(newSelectedValue);
        myPreviewThemeName = null;
        refreshPreviewPanel(name);
      }

      @Override
      public void reset() {
        myModifiedTheme.getStyleResourceValue().addItem(originalValue);
        reload(myThemeName);
      }
    };

    final String newStyleName = ThemeEditorUtils
      .showCreateNewStyleDialog(selectedStyle, myThemeEditorContext, !isSubStyleSelected, false, message,
                                isSubStyleSelected ? null : themeListener);

    if (!isSubStyleSelected) {
      themeListener.reset();
    }

    if (newStyleName == null) {
      return;
    }

    // Need invokeLater to wait for the theme resolver to be aware of the newly created style through the resource change listener
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        myThemeEditorContext.updateThemeResolver();
        ConfiguredThemeEditorStyle newStyle = myThemeEditorContext.getThemeResolver().getTheme(newStyleName);
        assert newStyle != null;
        newStyle.setValue(rv.getQualifiedName(), strValue);
      }
    });

    if (!isSubStyleSelected) {
      // We changed a theme, so we are done.
      // We don't need to call reload, because myResourceChangeListener will take care of it
      myThemeName = newStyleName;
      mySubStyleName = null;
      return;
    }

    ConfiguredThemeEditorStyle selectedTheme = getSelectedTheme();
    if (selectedTheme == null) {
      LOG.error("No theme selected.");
      return;
    }

    // Decide what property we need to modify.
    // If the modified style was pointed by a theme attribute, we need to use that theme attribute value
    // as property. Otherwise, just update the original property name with the new style.
    final String sourcePropertyName = mySubStyleSourceAttribute.isAttr() ?
                                      mySubStyleSourceAttribute.getAttrPropertyName():
                                      mySubStyleSourceAttribute.getQualifiedName();

    // We've modified a sub-style so we need to modify the attribute that was originally pointing to this.
    if (selectedTheme.isReadOnly()) {

      // The theme pointing to the new style is r/o so create a new theme and then write the value.
      message = String.format("<html>The style '%1$s' which references to '%2$s' is also Read-Only.<br/>" +
                              "A new theme will be created to point to the modified style '%3$s'.<br/></html>",
                              selectedTheme.getQualifiedName(), rv.getName(), newStyleName);

      final String newThemeName =
        ThemeEditorUtils.showCreateNewStyleDialog(selectedTheme, myThemeEditorContext, true, false, message, themeListener);
      themeListener.reset();
      if (newThemeName != null) {
        // We don't need to call reload, because myResourceChangeListener will take care of it
        myThemeName = newThemeName;
        mySubStyleName = newStyleName;

        // Need invokeLater to wait for the theme resolver to be aware of the newly created theme through the resource change listener
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            myThemeEditorContext.updateThemeResolver();
            ConfiguredThemeEditorStyle newTheme = myThemeEditorContext.getThemeResolver().getTheme(newThemeName);
            assert newTheme != null;
            newTheme.setValue(sourcePropertyName, newStyleName);
          }
        });
      }
    }
    else {
      selectedTheme.setValue(sourcePropertyName, newStyleName);
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

  public void reload(@Nullable final String defaultThemeName,
                     @Nullable final String defaultSubStyleName,
                     @Nullable final String defaultModuleName) {
    // Unsubscribing from ResourceNotificationManager, because Module might be changed
    unsubscribeResourceNotification();

    if (!ThemeEditorUtils.isThemeEditorSelected(myProject)) {
      return;
    }

    // Need to clean myPreviewTheme, because we now want to display the theme selected in the attributes panel
    myPreviewThemeName = null;

    initializeModulesCombo(defaultModuleName);
    myThemeEditorContext.setCurrentContextModule(getSelectedModule());

    // Subscribes to ResourceNotificationManager with new facet
    subscribeResourceNotification();

    mySubStyleSourceAttribute = null;

    final ThemeResolver themeResolver = myThemeEditorContext.getThemeResolver();
    myPanel.getThemeCombo().setModel(
      new ThemesListModel(myThemeEditorContext, ThemeEditorUtils.getDefaultThemeNames(themeResolver), defaultThemeName));
    myThemeName = (String)myPanel.getThemeCombo().getSelectedItem();
    mySubStyleName = (StringUtil.equals(myThemeName, defaultThemeName)) ? defaultSubStyleName : null;
    loadStyleAttributes();
  }

  /**
   * Loads the theme attributes table for the current selected theme or substyle.
   */
  private void loadStyleAttributes() {
    ConfiguredThemeEditorStyle selectedTheme = getPreviewTheme();
    ConfiguredThemeEditorStyle selectedStyle = null;

    if (selectedTheme == null) {
      selectedTheme = getSelectedTheme();
      selectedStyle = getCurrentSubStyle();
    }

    myAttributesTable.setRowSorter(null); // Clean any previous row sorters.
    myPanel.setSubstyleName(mySubStyleName);
    myPanel.getBackButton().setVisible(mySubStyleName != null);

    AndroidThemePreviewPanel previewPanel = myPreviewComponent.getPreviewPanel();
    if (selectedTheme == null) {
      if (myThemeName != null) {
        previewPanel.setErrorMessage("The theme " + myThemeName + " cannot be rendered in the current configuration");
      } else {
        previewPanel.setErrorMessage("No theme selected");
      }
      myAttributesTable.setModel(EMPTY_TABLE_MODEL);
      return;
    }

    previewPanel.setErrorMessage(null);

    myPanel.setShowThemeNotUsedWarning(false);
    if (selectedTheme.isProjectStyle()) {
      // Check whenever we reload the theme as any external file could have been changed that would affect this.
      // e.g. change to the manifest to use a theme.
      final PsiElement name = selectedTheme.getNamePsiElement();
      mySwingWorker = new SwingWorker<Boolean, Object>() {
        @Override
        protected Boolean doInBackground() throws Exception {
          assert name != null; // it's a project theme, so we should always have a name.
          return ReferencesSearch.search(name).findFirst() == null;
        }

        @Override
        protected void done() {
          if (isCancelled()) {
            return;
          }
          try {
            myPanel.setShowThemeNotUsedWarning(get());
          }
          catch (Exception ex) {
            // should never happen, as we are calling get from done.
            throw new RuntimeException(ex);
          }
        }
      };
      mySwingWorker.execute();
    }

    myThemeEditorContext.setCurrentTheme(selectedTheme);
    final Configuration configuration = myThemeEditorContext.getConfiguration();
    configuration.setTheme(selectedTheme.getStyleResourceUrl());

    myModel = new AttributesTableModel(selectedStyle != null ? selectedStyle : selectedTheme, getSelectedAttrGroup(), myThemeEditorContext);

    myModel.addThemePropertyChangedListener(new AttributesTableModel.ThemePropertyChangedListener() {
      @Override
      public void attributeChangedOnReadOnlyTheme(final EditedStyleItem attribute, final String newValue) {
        createNewThemeWithAttributeValue(attribute, newValue);
      }
    });

    myAttributesSorter = new TableRowSorter<AttributesTableModel>(myModel);
    // This is only used when the sort keys are set (only set in simple mode).
    myAttributesSorter.setComparator(0, SIMPLE_MODE_COMPARATOR);

    configureFilter();

    int row = myAttributesTable.getEditingRow();
    int column = myAttributesTable.getEditingColumn();
    // If an editor is present, remove it to update the entire table, do nothing otherwise
    myAttributesTable.removeEditor();

    // update the table
    myAttributesTable.setModel(myModel);
    myAttributesTable.setRowSorter(myAttributesSorter);
    myAttributesTable.updateRowHeights();

    // as a refresh can happen at any point, we want to restore the editor if one was present, do nothing otherwise
    myAttributesTable.editCellAt(row, column);

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

    ResourceResolver resourceResolver = myThemeEditorContext.getResourceResolver();
    assert resourceResolver != null;
    myPreviewComponent.setPreviewBackground(ThemeEditorUtils.getGoodContrastPreviewBackground(selectedTheme, resourceResolver));

    myPreviewComponent.reloadPreviewContents();
    myAttributesTable.repaint();
    myPanel.getThemeCombo().repaint();
  }

  /**
   * Refreshes the preview panel for theme previews
   */
  private void refreshPreviewPanel(@NotNull String previewThemeName) {
    if (!previewThemeName.equals(myPreviewThemeName)) {
      myPreviewThemeName = previewThemeName;
      // Only refresh when we select a different theme
      ConfiguredThemeEditorStyle previewTheme = getPreviewTheme();

      if (previewTheme == null) {
        // previewTheme is not a valid theme in the current configuration
        return;
      }

      myThemeEditorContext.setCurrentTheme(previewTheme);
      final Configuration configuration = myThemeEditorContext.getConfiguration();
      configuration.setTheme(previewTheme.getStyleResourceUrl());

      ResourceResolver resourceResolver = myThemeEditorContext.getResourceResolver();
      assert resourceResolver != null;
      myPreviewComponent.setPreviewBackground(ThemeEditorUtils.getGoodContrastPreviewBackground(previewTheme, resourceResolver));

      myPreviewComponent.reloadPreviewContents();
    }
  }

  @Override
  public void dispose() {
    // First remove the table editor so that it won't be called after
    // objects it relies on, like the module, have themselves been disposed
    myAttributesTable.removeEditor();
    if (mySwingWorker != null) {
      mySwingWorker.cancel(true);
    }
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

    public void configure(final Set<String> availableAttributes, boolean appCompat) {
      filterAttributes.clear();

      for (final String candidate : ATTRIBUTES_DEFAULT_FILTER) {
        if (appCompat && availableAttributes.contains(candidate)) {
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

      ConfiguredThemeEditorStyle selectedTheme = getUsedStyle();
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
    // The condition below isn't constant, because updateUiParameters() is triggered during
    // construction: constructor of ThemeEditorComponent calls constructor of Splitter, which
    // calls setUI at some point. If this condition is removed, theme editor would fail with
    // NPE during its startup.
    //noinspection ConstantConditions
    if (myAttributesTable == null) {
      return;
    }

    Font regularFont = UIUtil.getLabelFont();

    int regularFontSize = getFontMetrics(regularFont).getHeight();
    Font headerFont = regularFont.deriveFont(regularFontSize * ThemeEditorConstants.ATTRIBUTES_HEADER_FONT_SCALE);

    int headerFontSize = getFontMetrics(headerFont).getHeight();

    // We calculate the size of the resource cell (drawable and color cells) by creating a ResourceComponent that
    // we use to measure the preferred size.
    ResourceComponent sampleComponent = new ResourceComponent(myProject, false);
    int bigCellSize = sampleComponent.getPreferredSize().height;
    myAttributesTable.setClassHeights(ImmutableMap.of(
      Object.class, regularFontSize + REGULAR_CELL_PADDING,
      Color.class, bigCellSize,
      DrawableDomElement.class, bigCellSize,
      TableLabel.class, headerFontSize + LARGE_CELL_PADDING,
      AttributesTableModel.ParentAttribute.class, bigCellSize
    ));
  }
}
