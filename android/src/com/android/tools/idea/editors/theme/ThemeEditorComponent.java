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
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.configurations.DeviceMenuAction;
import com.android.tools.idea.configurations.ThemeSelectionDialog;
import com.android.tools.idea.editors.theme.attributes.AttributesGrouper;
import com.android.tools.idea.editors.theme.attributes.AttributesModelColorPaletteModel;
import com.android.tools.idea.editors.theme.attributes.AttributesTableModel;
import com.android.tools.idea.editors.theme.attributes.ShowJavadocAction;
import com.android.tools.idea.editors.theme.attributes.TableLabel;
import com.android.tools.idea.editors.theme.attributes.editors.AttributeReferenceRendererEditor;
import com.android.tools.idea.editors.theme.attributes.editors.BooleanRendererEditor;
import com.android.tools.idea.editors.theme.attributes.editors.ColorComponent;
import com.android.tools.idea.editors.theme.attributes.editors.ColorEditor;
import com.android.tools.idea.editors.theme.attributes.editors.ColorRenderer;
import com.android.tools.idea.editors.theme.attributes.editors.DelegatingCellEditor;
import com.android.tools.idea.editors.theme.attributes.editors.DelegatingCellRenderer;
import com.android.tools.idea.editors.theme.attributes.editors.DrawableEditor;
import com.android.tools.idea.editors.theme.attributes.editors.DrawableRenderer;
import com.android.tools.idea.editors.theme.attributes.editors.EnumRendererEditor;
import com.android.tools.idea.editors.theme.attributes.editors.FlagRendererEditor;
import com.android.tools.idea.editors.theme.attributes.editors.IntegerRenderer;
import com.android.tools.idea.editors.theme.attributes.editors.ParentRendererEditor;
import com.android.tools.idea.editors.theme.attributes.editors.StyleListCellRenderer;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.android.tools.idea.editors.theme.preview.AndroidThemePreviewPanel;
import com.android.tools.idea.gradle.compiler.PostProjectBuildTasksExecutor;
import com.android.tools.idea.gradle.project.GradleBuildListener;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.rendering.ResourceNotificationManager;
import com.android.tools.idea.rendering.ResourceNotificationManager.ResourceChangeListener;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.ui.JBColor;
import com.intellij.util.Processor;
import com.intellij.util.messages.MessageBusConnection;
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
import java.util.Set;


public class ThemeEditorComponent extends Splitter {
  private static final Logger LOG = Logger.getInstance(ThemeEditorComponent.class);

  private static final JBColor PREVIEW_BACKGROUND = new JBColor(new Color(0xFAFAFA), new Color(0x606162));

  public static final float HEADER_FONT_SCALE = 1.3f;
  public static final int REGULAR_CELL_PADDING = 4;
  public static final int LARGE_CELL_PADDING = 10;
  private final Project myProject;

  private Font myHeaderFont;

  private StyleResolver myStyleResolver;
  private String myPreviousSelectedTheme;

  // Points to the current selected substyle within the theme.
  private ThemeEditorStyle myCurrentSubStyle;

  // Points to the attribute that original pointed to the substyle.
  private EditedStyleItem mySubStyleSourceAttribute;

  // Subcomponents
  private final ThemeEditorContext myThemeEditorContext;
  private final AndroidThemePreviewPanel myPreviewPanel;

  private final StyleAttributesFilter myAttributesFilter;
  private TableRowSorter<AttributesTableModel> myAttributesSorter;
  private final SimpleModeFilter mySimpleModeFilter;

  private final AttributesPanel myPanel = new AttributesPanel();
  private final ThemeEditorTable myAttributesTable = myPanel.getAttributesTable();

  private final ResourceChangeListener myResourceChangeListener;

  private final GoToListener myGoToListener;

  public interface GoToListener {
    void goTo(@NotNull EditedStyleItem value);
    void goToParent();
  }

  private ThemeEditorStyle mySelectedTheme;
  private MessageBusConnection myMessageBusConnection;
  private AttributesTableModel myModel;

  public ThemeEditorComponent(@NotNull final Project project) {
    myProject = project;

    // TODO(ddrone):
    // The expensive call is done here only to acquire initial instance of Configuration and Module
    // This should be optimized somehow, maybe setting Configuration and Module to null in the context initially?
    final ImmutableList<ProjectThemeResolver.ThemeWithSource> editableProjectThemes =
      ProjectThemeResolver.getEditableProjectThemes(project);
    ProjectThemeResolver.ThemeWithSource firstTheme = Iterables.getFirst(editableProjectThemes, null);

    // TODO(ddrone): get non-project theme (e.g. Theme.Material) here in case there are no project themes
    assert firstTheme != null : "Trying to launch Theme Editor without any themes";

    final Module module = firstTheme.getSourceModule();
    AndroidFacet facet = AndroidFacet.getInstance(module);

    // Module is a source of a theme, thus, should be Android module
    assert facet != null : String.format("Module %s is not Android module", module.getName());

    ConfigurationManager configurationManager = facet.getConfigurationManager();
    final VirtualFile projectFile = project.getProjectFile();
    assert projectFile != null;

    final Configuration configuration = configurationManager.getConfiguration(projectFile);

    myThemeEditorContext = new ThemeEditorContext(configuration, module);
    myThemeEditorContext.addConfigurationListener(new ConfigurationListener() {
      @Override
      public boolean changed(int flags) {

        //reloads the theme editor preview when device is modified
        if ((flags & CFG_DEVICE) != 0) {
          loadStyleAttributes();
          myThemeEditorContext.getConfiguration().save();
        }

        return true;
      }
    });

    myStyleResolver = new StyleResolver(configuration);

    myPreviewPanel = new AndroidThemePreviewPanel(myThemeEditorContext, PREVIEW_BACKGROUND);
    myPreviewPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

    // Setup Javadoc handler.
    ActionManager actionManager = ActionManager.getInstance();
    ShowJavadocAction showJavadoc = new ShowJavadocAction(myAttributesTable, myThemeEditorContext);
    showJavadoc.registerCustomShortcutSet(actionManager.getAction(IdeActions.ACTION_QUICK_JAVADOC).getShortcutSet(), myAttributesTable);

    ResourcesCompletionProvider completionProvider = new ResourcesCompletionProvider(myThemeEditorContext);
    myGoToListener = new GoToListener() {
      @Override
      public void goTo(@NotNull EditedStyleItem value) {
        ResourceResolver resolver = myThemeEditorContext.getResourceResolver();
        if (value.isAttr() && getSelectedStyle() != null && resolver != null) {
          // We need to resolve the theme attribute.
          // TODO: Do we need a full resolution or can we just try to get it from the StyleWrapper?
          ItemResourceValue resourceValue = (ItemResourceValue)resolver.findResValue(value.getValue(), false);
          if (resourceValue == null) {
            LOG.error("Unable to resolve " + value.getValue());
            return;
          }

          EditedStyleItem editedStyleItem = new EditedStyleItem(resourceValue, getSelectedStyle());
          myCurrentSubStyle = myStyleResolver.getStyle(editedStyleItem.getValue());
        }
        else {
          myCurrentSubStyle = myStyleResolver.getStyle(value.getValue());
        }
        mySubStyleSourceAttribute = value;
        loadStyleAttributes();
      }

      @Override
      public void goToParent() {
        ThemeEditorComponent.this.goToParent();
      }
    };
    final AttributeReferenceRendererEditor styleEditor = new AttributeReferenceRendererEditor(project, completionProvider);

    final JScrollPane scroll = myPanel.getAttributesScrollPane();
    scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)); // the scroll pane should fill all available space

    myAttributesTable.setBackground(null); // Get rid of default white background of the table.
    scroll.setBackground(null); // needed for OS X, as by default is set to white
    scroll.getViewport().setBackground(null); // needed for OS X, as by default is set to white

    myAttributesTable.setDefaultRenderer(Color.class, new DelegatingCellRenderer(new ColorRenderer(myThemeEditorContext)));
    myAttributesTable.setDefaultRenderer(EditedStyleItem.class, new DelegatingCellRenderer(new AttributeReferenceRendererEditor(project, completionProvider)));
    myAttributesTable.setDefaultRenderer(ThemeEditorStyle.class, new DelegatingCellRenderer(new AttributeReferenceRendererEditor(project, completionProvider)));
    myAttributesTable.setDefaultRenderer(String.class, new DelegatingCellRenderer(myAttributesTable.getDefaultRenderer(String.class)));
    myAttributesTable.setDefaultRenderer(Integer.class, new DelegatingCellRenderer(new IntegerRenderer()));
    myAttributesTable.setDefaultRenderer(Boolean.class, new DelegatingCellRenderer(new BooleanRendererEditor(myThemeEditorContext)));
    myAttributesTable.setDefaultRenderer(Enum.class, new DelegatingCellRenderer(new EnumRendererEditor()));
    myAttributesTable.setDefaultRenderer(Flag.class, new DelegatingCellRenderer(new FlagRendererEditor()));
    myAttributesTable.setDefaultRenderer(AttributesTableModel.ParentAttribute.class, new DelegatingCellRenderer(new ParentRendererEditor(myThemeEditorContext)));
    myAttributesTable.setDefaultRenderer(DrawableDomElement.class, new DelegatingCellRenderer(new DrawableRenderer(myThemeEditorContext)));
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

    myAttributesTable.setDefaultEditor(Color.class, new DelegatingCellEditor(false, new ColorEditor(myThemeEditorContext, myPreviewPanel), myThemeEditorContext));
    myAttributesTable.setDefaultEditor(EditedStyleItem.class, new DelegatingCellEditor(false, new AttributeReferenceRendererEditor(project, completionProvider), myThemeEditorContext));
    myAttributesTable.setDefaultEditor(String.class, new DelegatingCellEditor(false, myAttributesTable.getDefaultEditor(String.class), myThemeEditorContext));
    myAttributesTable.setDefaultEditor(Integer.class, new DelegatingCellEditor(myAttributesTable.getDefaultEditor(Integer.class), myThemeEditorContext));
    myAttributesTable.setDefaultEditor(Boolean.class, new DelegatingCellEditor(false, new BooleanRendererEditor(myThemeEditorContext), myThemeEditorContext));
    myAttributesTable.setDefaultEditor(Enum.class, new DelegatingCellEditor(false, new EnumRendererEditor(), myThemeEditorContext));
    myAttributesTable.setDefaultEditor(Flag.class, new DelegatingCellEditor(false, new FlagRendererEditor(), myThemeEditorContext));
    myAttributesTable.setDefaultEditor(AttributesTableModel.ParentAttribute.class, new DelegatingCellEditor(false, new ParentRendererEditor(myThemeEditorContext), myThemeEditorContext));

    // We allow to edit style pointers as Strings.
    myAttributesTable.setDefaultEditor(ThemeEditorStyle.class, new DelegatingCellEditor(false, styleEditor, myThemeEditorContext));
    myAttributesTable.setDefaultEditor(DrawableDomElement.class, new DelegatingCellEditor(false, new DrawableEditor(myThemeEditorContext), myThemeEditorContext));
    updateUiParameters();

    myAttributesFilter = new StyleAttributesFilter();
    mySimpleModeFilter = new SimpleModeFilter();

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
            myPanel.setSelectedTheme(mySelectedTheme);
          }
        }
        else if (myPanel.isShowAllThemesSelected()) {
          if (!selectNewTheme()) {
            myPanel.setSelectedTheme(mySelectedTheme);
          }
        }
        else if (myPanel.isRenameSelected()) {
          if (!renameTheme()) {
            myPanel.setSelectedTheme(mySelectedTheme);
          }
        }
        else {
          Object item = myPanel.getThemeCombo().getSelectedItem();
          final ThemeEditorStyle theme = (ThemeEditorStyle)item;
          assert theme != null;

          mySelectedTheme = theme;
          saveCurrentSelectedTheme();
          myCurrentSubStyle = null;
          mySubStyleSourceAttribute = null;

          // Unsubscribing from ResourceNotificationManager, because selectedTheme may come from different Module
          unsubscribeResourceNotification();

          Module selectedModule = ((ThemesListModel)(myPanel.getThemeCombo().getModel())).getSelectedModule();
          if (selectedModule != null) {
            myThemeEditorContext.setCurrentThemeModule(selectedModule);
          }

          // Subscribes to ResourceNotificationManager with new facet
          subscribeResourceNotification();
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
    DeviceMenuAction deviceAction = new DeviceMenuAction(myPreviewPanel);
    group.add(deviceAction);
    ActionToolbar actionToolbar = actionManager.createActionToolbar("ThemeToolbar", group, true);
    actionToolbar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);
    JPanel myConfigToolbar = myPanel.getConfigToolbar();
    myConfigToolbar.add(actionToolbar.getComponent());

    setFirstComponent(myPreviewPanel);
    setSecondComponent(myPanel.getRightPanel());
    setShowDividerControls(false);

    myMessageBusConnection = project.getMessageBus().connect(project);
    myMessageBusConnection.subscribe(PostProjectBuildTasksExecutor.GRADLE_BUILD_TOPIC, new GradleBuildListener() {
      @Override
      public void buildFinished(@NotNull Project project, @Nullable BuildMode mode) {
        if (project != myThemeEditorContext.getProject()) {
          return;
        }

        // Classes probably have changed so reload the custom components and support library classes.
        myPreviewPanel.reloadComponents();
        myPreviewPanel.revalidate();
        myPreviewPanel.repaint();
      }
    });

    myResourceChangeListener = new ResourceChangeListener() {
      @Override
      public void resourcesChanged(@NotNull Set<ResourceNotificationManager.Reason> reason) {
        reload(mySelectedTheme.getName(), myCurrentSubStyle != null ? myCurrentSubStyle.getName() : null);
      }
    };

    // Set an initial state in case that the editor didn't have a previously saved state
    // TODO: Try to be smarter about this and get the ThemeEditor to set a default state where there is no previous state
    reload(null);
  }

  /**
   * Subscribes myResourceChangeListener to ResourceNotificationManager with current AndroidFacet.
   * By subscribing, myResourceChangeListener can track all internal and external changes in resources.
   */
  private void subscribeResourceNotification() {
    ResourceNotificationManager manager = ResourceNotificationManager.getInstance(myThemeEditorContext.getProject());
    AndroidFacet facet = AndroidFacet.getInstance(myThemeEditorContext.getCurrentThemeModule());
    assert facet != null : myThemeEditorContext.getCurrentThemeModule().getName() + " module doesn't have an AndroidFacet";
    manager.addListener(myResourceChangeListener, facet, null, null);
  }

  /**
   * Unsubscribes myResourceChangeListener from ResourceNotificationManager with current AndroidFacet.
   */
  private void unsubscribeResourceNotification() {
    ResourceNotificationManager manager = ResourceNotificationManager.getInstance(myThemeEditorContext.getProject());
    AndroidFacet facet = AndroidFacet.getInstance(myThemeEditorContext.getCurrentThemeModule());
    assert facet != null : myThemeEditorContext.getCurrentThemeModule().getName() + " module doesn't have an AndroidFacet";
    manager.removeListener(myResourceChangeListener, facet, null, null);
  }

  /**
   * @see FileEditor#selectNotify().
   */
  public void selectNotify() {
    reload(getPreviousSelectedTheme());
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
    ThemeEditorStyle selectedTheme = getSelectedStyle();
    String selectedThemeName = selectedTheme == null ? null : selectedTheme.getName();

    String newThemeName = createNewStyle(selectedThemeName, null/*message*/, null/*newAttributeName*/, null/*newAttributeValue*/);
    if (newThemeName != null) {
      reload(newThemeName);
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
    assert mySelectedTheme.isProjectStyle();
    PsiElement namePsiElement = mySelectedTheme.getNamePsiElement();
    if (namePsiElement == null) {
      return false;
    }
    RenameDialog renameDialog = new RenameDialog(myThemeEditorContext.getProject(), namePsiElement, null, null);
    renameDialog.show();
    if (renameDialog.isOK()) {
      String newName = renameDialog.getNewName();
      assert mySelectedTheme.getName() != null;
      String newQualifiedName = mySelectedTheme.getName().replace(mySelectedTheme.getSimpleName(), newName);
      AndroidFacet facet = AndroidFacet.getInstance(myThemeEditorContext.getCurrentThemeModule());
      if (facet != null) {
        facet.refreshResources();
      }
      reload(newQualifiedName);
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
                                                     myThemeEditorContext,
                                                     defaultParentStyleName,
                                                     getSelectedTheme() != null ? getSelectedTheme().getSimpleName() : null,
                                                     message);
    boolean createStyle = dialog.showAndGet();
    if (!createStyle) {
      return null;
    }

    int minModuleApi = ThemeEditorUtils.getMinApiLevel(myThemeEditorContext.getCurrentThemeModule());
    int themeParentApiLevel = ThemeEditorUtils.getOriginalApiLevel(dialog.getStyleParentName(), myThemeEditorContext.getProject());
    int newAttributeApiLevel = ThemeEditorUtils.getOriginalApiLevel(newAttributeName, myThemeEditorContext.getProject());
    int newValueApiLevel = ThemeEditorUtils.getOriginalApiLevel(newAttributeValue, myThemeEditorContext.getProject());
    int minAcceptableApi = Math.max(Math.max(themeParentApiLevel, newAttributeApiLevel), newValueApiLevel);

    final String fileName = AndroidResourceUtil.getDefaultResourceFileName(ResourceType.STYLE);
    FolderConfiguration config = new FolderConfiguration();
    if (minModuleApi < minAcceptableApi) {
      VersionQualifier qualifier = new VersionQualifier(minAcceptableApi);
      config.setVersionQualifier(qualifier);
    }
    final List<String> dirNames = Collections.singletonList(config.getFolderName(ResourceFolderType.VALUES));

    if (fileName == null) {
      LOG.error("Couldn't find a default filename for ResourceType.STYLE");
      return null;
    }

    boolean isCreated = new WriteCommandAction<Boolean>(myThemeEditorContext.getProject(), "Create new theme " + dialog.getStyleName()) {
      @Override
      protected void run(@NotNull Result<Boolean> result) {
        result.setResult(AndroidResourceUtil.
          createValueResource(myThemeEditorContext.getCurrentThemeModule(), dialog.getStyleName(),
                              ResourceType.STYLE, fileName, dirNames, new Processor<ResourceElement>() {
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
            }));
      }
    }.execute().getResultObject();

    if (isCreated) {
      AndroidFacet facet = AndroidFacet.getInstance(myThemeEditorContext.getCurrentThemeModule());
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
    if (strValue.equals(rv.getValue())) {
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
    Configuration configuration = myThemeEditorContext.getConfiguration();
    configuration.setTheme(null);

    myStyleResolver = new StyleResolver(configuration);
    myCurrentSubStyle = defaultSubStyleName == null ? null : myStyleResolver.getStyle(defaultSubStyleName);
    mySubStyleSourceAttribute = null;

    final ThemeResolver themeResolver = new ThemeResolver(configuration, myStyleResolver);
    myPanel.getThemeCombo().setModel(new ThemesListModel(myProject, themeResolver, defaultThemeName));
    loadStyleAttributes();
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
    final Configuration configuration = myThemeEditorContext.getConfiguration();
    configuration.setTheme(selectedTheme.getName());

    assert configuration.getResourceResolver() != null; // ResourceResolver is only null if no theme was set.
    myModel = new AttributesTableModel(selectedStyle, getSelectedAttrGroup(), configuration, myThemeEditorContext.getProject());
    myModel.setGoToDefinitionListener(myGoToListener);

    myModel.addThemePropertyChangedListener(new AttributesTableModel.ThemePropertyChangedListener() {
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
    myThemeEditorContext.dispose();
    myMessageBusConnection.disconnect();
    myMessageBusConnection = null;
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

      ThemeEditorStyle selectedTheme = getSelectedStyle();
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

    // Big cells contain two lines of text, and we want some space between them
    // (thus multiplier is 2.8 rather than 2). Also, we need some padding on top and bottom.
    int bigCellSize = 2 * regularFontSize + ColorComponent.SUM_PADDINGS;
    myAttributesTable.setClassHeights(ImmutableMap.of(
      Object.class, regularFontSize + REGULAR_CELL_PADDING,
      Color.class, bigCellSize,
      DrawableDomElement.class, bigCellSize,
      TableLabel.class, headerFontSize + LARGE_CELL_PADDING
    ));
  }
}
