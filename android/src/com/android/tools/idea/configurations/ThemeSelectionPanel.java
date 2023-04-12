/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.configurations;

import static com.android.SdkConstants.PREFIX_ANDROID;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;

import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.android.tools.idea.editors.theme.ThemeResolver;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.FilterComponent;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.SortedListModel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.treeStructure.Tree;
import icons.StudioIcons;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Theme selection dialog.
 * <p/>
 * TODO: In the future, make it easy to create new themes here, as well as assigning a theme
 * to an activity.
 */
public class ThemeSelectionPanel implements TreeSelectionListener, ListSelectionListener, Disposable {
  private static final String DEVICE_LIGHT_PREFIX = PREFIX_ANDROID + "Theme.DeviceDefault.Light";
  private static final String DEVICE_PREFIX = PREFIX_ANDROID + "Theme.DeviceDefault";
  private static final String HOLO_LIGHT_PREFIX = PREFIX_ANDROID + "Theme.Holo.Light";
  private static final String HOLO_PREFIX = PREFIX_ANDROID + "Theme.Holo";
  private static final String MATERIAL_LIGHT_PREFIX = PREFIX_ANDROID + "Theme.Material.Light";
  private static final String MATERIAL_PREFIX = PREFIX_ANDROID + "Theme.Material";
  private static final String LIGHT_PREFIX = PREFIX_ANDROID + "Theme.Light";
  private static final String DIALOG_SUFFIX = ".Dialog";
  private static final String DIALOG_PART = ".Dialog.";
  private static final SimpleTextAttributes SEARCH_HIGHLIGHT_ATTRIBUTES =
    new SimpleTextAttributes(null, JBColor.MAGENTA, null, SimpleTextAttributes.STYLE_BOLD);

  @NotNull private final Configuration myConfiguration;
  @NotNull private final ThemeSelectionDialog myDialog;
  private JBList<String> myThemeList;
  private Tree myCategoryTree;
  private JPanel myContentPanel;
  private ThemeFilterComponent myFilter;
  @NotNull private final List<String> myFrameworkThemes;
  @NotNull private final List<String> myProjectThemes;
  @NotNull private final List<String> myLibraryThemes;
  @Nullable private static Deque<String> ourRecent;
  @Nullable private ThemeCategory myCategory = ThemeCategory.ALL;
  @NotNull private Map<ThemeCategory, List<String>> myThemeMap = Maps.newEnumMap(ThemeCategory.class);
  @NotNull private final Set<String> myExcludedThemes;
  private boolean myIgnore;

  private ThemeChangedListener myThemeChangedListener;

  /**
   * @param excludedThemes Themes not to be shown in the selection dialog
   */
  public ThemeSelectionPanel(@NotNull ThemeSelectionDialog dialog,
                             @NotNull Configuration configuration,
                             @NotNull Set<String> excludedThemes) {
    myDialog = dialog;
    myConfiguration = configuration;
    myExcludedThemes = excludedThemes;

    ThemeResolver themeResolver = new ThemeResolver(configuration);
    StyleResourceValue[] baseThemes = themeResolver.requiredBaseThemes();
    Function1<ConfiguredThemeEditorStyle, Boolean> filter = ThemeUtils.createFilter(themeResolver, myExcludedThemes, baseThemes);
    myFrameworkThemes = baseThemes.length == 0
                        ? ThemeUtils.getFrameworkThemeNames(themeResolver, filter)
                        : Collections.emptyList();
    myProjectThemes = ThemeUtils.getProjectThemeNames(themeResolver, filter);
    myLibraryThemes = ThemeUtils.getLibraryThemeNames(themeResolver, filter);

    String currentTheme = ResolutionUtils.getQualifiedNameFromResourceUrl(configuration.getTheme());
    touchTheme(currentTheme, myExcludedThemes);

    myCategoryTree.setModel(new CategoryModel());
    myCategoryTree.setRootVisible(false);
    myCategoryTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myCategoryTree.addTreeSelectionListener(this);
    setInitialSelection(currentTheme);
    myThemeList.addListSelectionListener(this);
    myThemeList.setCellRenderer(new ColoredListCellRenderer<String>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, String style, int index, boolean selected, boolean hasFocus) {
        setIcon(StudioIcons.Shell.Menu.THEME_EDITOR);

        String filter = myFilter.getFilter();
        style = ThemeUtils.getPreferredThemeName(style);

        if (!filter.isEmpty()) {
          int matchIndex = StringUtil.indexOfIgnoreCase(style, filter, index + 1);
          if (matchIndex != -1) {
            if (matchIndex > 0) {
              append(style.substring(0, matchIndex), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
            int matchEnd = matchIndex + filter.length();
            append(style.substring(matchIndex, matchEnd), SEARCH_HIGHLIGHT_ATTRIBUTES);
            if (matchEnd < style.length()) {

              append(style.substring(matchEnd), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
            return;
          }
        }

        int lastDot = style.lastIndexOf('.');
        if (lastDot > 0) {
          append(style.substring(0, lastDot + 1), SimpleTextAttributes.GRAY_ATTRIBUTES);
          append(style.substring(lastDot + 1), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
        else {
          append(style, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }
    });
  }

  public void installDoubleClickListener(@NotNull DoubleClickListener listener) {
    listener.installOn(myThemeList);
  }

  public void setThemeChangedListener(@NotNull ThemeChangedListener themeChangedListener) {
    myThemeChangedListener = themeChangedListener;
  }

  private void setInitialSelection(@Nullable String currentTheme) {
    if (currentTheme == null) {
      myCategoryTree.setSelectionRow(0);
      return;
    }

    if (currentTheme.startsWith(HOLO_LIGHT_PREFIX)) {
      selectCategory(ThemeCategory.HOLO_LIGHT, true);
    }
    else if (currentTheme.startsWith(HOLO_PREFIX)) {
      selectCategory(ThemeCategory.HOLO, true);
    }
    if (currentTheme.startsWith(MATERIAL_LIGHT_PREFIX)) {
      selectCategory(ThemeCategory.MATERIAL_LIGHT, true);
    }
    else if (currentTheme.startsWith(MATERIAL_PREFIX)) {
      selectCategory(ThemeCategory.MATERIAL, true);
    }
    else if (currentTheme.startsWith(DEVICE_PREFIX)) {
      selectCategory(ThemeCategory.DEVICE, true);
    }
    else if (currentTheme.startsWith(STYLE_RESOURCE_PREFIX)) {
      selectCategory(ThemeCategory.PROJECT, true);
    }
    else {
      selectCategory(ThemeCategory.ALL, true);
    }
    updateThemeList();
    myThemeList.setSelectedValue(currentTheme, true);
  }

  private void selectCategory(ThemeCategory category, boolean updateList) {
    try {
      myIgnore = true;
      myCategoryTree.setSelectionPath(new TreePath(new ThemeCategory[]{ThemeCategory.ROOT, category}));
      myCategory = category;
    }
    finally {
      myIgnore = false;
    }

    if (updateList) {
      updateThemeList();
    }
  }

  @NotNull
  public JPanel getContentPanel() {
    return myContentPanel;
  }

  @NotNull
  private List<String> getThemes(@Nullable ThemeCategory category) {
    if (category == null) {
      return Collections.emptyList();
    }

    List<String> themes = myThemeMap.get(category);
    if (themes != null) {
      return themes;
    }

    themes = new ArrayList<>(50);

    switch (category) {
      case RECENT:
        if (ourRecent != null) {
          themes.addAll(ourRecent);
        }
        break;
      case HOLO:
        for (String theme : myFrameworkThemes) {
          if (theme.startsWith(HOLO_PREFIX) && !theme.startsWith(HOLO_LIGHT_PREFIX)) {
            themes.add(theme);
          }
        }
        break;
      case HOLO_LIGHT:
        for (String theme : myFrameworkThemes) {
          if (theme.startsWith(HOLO_LIGHT_PREFIX)) {
            themes.add(theme);
          }
        }
        break;
      case MATERIAL:
        for (String theme : myFrameworkThemes) {
          if (theme.startsWith(MATERIAL_PREFIX) && !theme.startsWith(MATERIAL_LIGHT_PREFIX)) {
            themes.add(theme);
          }
        }
        break;
      case MATERIAL_LIGHT:
        for (String theme : myFrameworkThemes) {
          if (theme.startsWith(MATERIAL_LIGHT_PREFIX)) {
            themes.add(theme);
          }
        }
        break;
      case PROJECT:
        themes.addAll(myProjectThemes);
        break;
      case CLASSIC:
        for (String theme : myFrameworkThemes) {
          if (!theme.startsWith(HOLO_PREFIX) && !theme.startsWith(DEVICE_PREFIX)) {
            themes.add(theme);
          }
        }
        break;
      case CLASSIC_LIGHT:
        for (String theme : myFrameworkThemes) {
          if (theme.startsWith(LIGHT_PREFIX)) {
            themes.add(theme);
          }
        }
        break;
      case LIGHT:
        for (String theme : myFrameworkThemes) {
          if (theme.startsWith(HOLO_LIGHT_PREFIX) || theme.startsWith(LIGHT_PREFIX) || theme.startsWith(DEVICE_LIGHT_PREFIX)
              || theme.startsWith(MATERIAL_LIGHT_PREFIX)) {
            themes.add(theme);
          }
        }
        for (String theme : myLibraryThemes) {
          if (theme.startsWith(HOLO_LIGHT_PREFIX) || theme.startsWith(LIGHT_PREFIX) || theme.startsWith(DEVICE_LIGHT_PREFIX)
              || theme.startsWith(MATERIAL_LIGHT_PREFIX)) {
            themes.add(theme);
          }
        }
        break;
      case DEVICE:
        for (String theme : myFrameworkThemes) {
          if (theme.startsWith(DEVICE_PREFIX)) {
            themes.add(theme);
          }
        }
        break;
      case DIALOGS:
        for (String theme : myProjectThemes) {
          if (theme.endsWith(DIALOG_SUFFIX) || theme.contains(DIALOG_PART)) {
            themes.add(theme);
          }
        }
        for (String theme : myFrameworkThemes) {
          if (theme.endsWith(DIALOG_SUFFIX) || theme.contains(DIALOG_PART)) {
            themes.add(theme);
          }
        }
        for (String theme : myLibraryThemes) {
          if (theme.endsWith(DIALOG_SUFFIX) || theme.contains(DIALOG_PART)) {
            themes.add(theme);
          }
        }
        break;
      case MANIFEST: {
        collectThemesFromManifest(myConfiguration.getConfigModule())
          .sorted()
          .map(ResolutionUtils::getQualifiedNameFromResourceUrl)
          .forEach(themes::add);
        break;
      }
      case ALL:
        themes.addAll(myProjectThemes);
        themes.addAll(myFrameworkThemes);
        themes.addAll(myLibraryThemes);
        break;
      case ROOT:
      default:
        assert false : category;
        break;
    }

    myThemeMap.put(category, themes);
    return themes;
  }

  /** Collect all distinct themes from the module's manifest (i.e. application and activity themes) */
  @NotNull
  private static Stream<String> collectThemesFromManifest(@NotNull ConfigurationModelModule module) {
    String appTheme = module.getThemeInfoProvider().getAppThemeName();
    Set<String> activityThemes = module.getThemeInfoProvider().getAllActivityThemeNames();
    if (appTheme != null && !activityThemes.contains(appTheme)) {
      return Streams.concat(Stream.of(appTheme), activityThemes.stream());
    }
    return activityThemes.stream();
  }

  private void updateThemeList() {
    if (myCategory == null) {
      return;
    }

    String selected = myThemeList.getSelectedValue();

    SortedListModel<String> model = new SortedListModel<>(String.CASE_INSENSITIVE_ORDER);
    String filter = myFilter.getFilter();

    List<String> themes = getThemes(myCategory);
    for (String theme : themes) {
      if (matchesFilter(theme, filter)) {
        model.add(theme);
      }
    }

    myThemeList.setModel(model);
    if (selected != null) {
      myThemeList.setSelectedValue(selected, true /*shouldScroll*/);
    }
    else if (model.getSize() > 0) {
      myThemeList.setSelectedIndex(0);
    }
  }

  private static boolean matchesFilter(String theme, String filter) {
    int index = theme.lastIndexOf('/');
    return filter.isEmpty() || StringUtil.indexOfIgnoreCase(theme, filter, index + 1) != -1;
  }

  // ---- Implements ListSelectionListener ----
  @Override
  public void valueChanged(ListSelectionEvent listSelectionEvent) {
    if (myIgnore) {
      return;
    }
    if (myThemeChangedListener != null) {
      String themeName = getTheme();
      if (themeName != null) {
        myThemeChangedListener.themeChanged(themeName);
      }
    }

    myDialog.checkValidation();

    // TODO: Perhaps show the full theme somewhere, perhaps list the theme definitions, perhaps
    // enable/disable actions related to the theme: assign to activity, open related definition,
    // create new theme, etc.
    //
    // Perhaps even perform render preview?
  }

  // ---- Implements TreeSelectionListener ----
  @Override
  public void valueChanged(TreeSelectionEvent treeSelectionEvent) {
    if (myIgnore) {
      return;
    }

    TreePath path = treeSelectionEvent.getPath();
    if (path == null) {
      return;
    }

    myCategory = (ThemeCategory)path.getLastPathComponent();
    updateThemeList();
    if (myThemeList.getModel().getSize() > 0) {
      myThemeList.setSelectedIndex(0);
    }
  }

  @Nullable
  public String getTheme() {
    String selected = myThemeList.getSelectedValue();
    touchTheme(selected, myExcludedThemes);
    return selected;
  }

  private static void touchTheme(@Nullable String selected, Set<String> excludedThemes) {
    if (selected != null) {
      if (ourRecent == null || !ourRecent.contains(selected)) {
        if (ourRecent == null) {
          ourRecent = new LinkedList<>();
        }
        if (!excludedThemes.contains(selected)) {
          ourRecent.addFirst(selected);
        }
      }
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myCategoryTree;
  }

  @Override
  public void dispose() {
    myFilter.dispose();
  }

  private class CategoryModel implements TreeModel {
    @NotNull private final Map<ThemeCategory, List<ThemeCategory>> myLabels;

    CategoryModel() {
      myLabels = Maps.newHashMap();
      List<ThemeCategory> topLevel = new ArrayList<>();

      if (ourRecent != null) {
        topLevel.add(ThemeCategory.RECENT);
      }

      addCategory(topLevel, ThemeCategory.MANIFEST);
      addCategory(topLevel, ThemeCategory.PROJECT);
      AndroidModuleInfo info = myConfiguration.getConfigModule().getAndroidModuleInfo();
      if (info != null && info.getBuildSdkVersion() != null && info.getBuildSdkVersion().getFeatureLevel() >= 21) {
        addCategory(topLevel, ThemeCategory.MATERIAL);
        addCategory(topLevel, ThemeCategory.MATERIAL_LIGHT);
      }

      addCategory(topLevel, ThemeCategory.HOLO);
      addCategory(topLevel, ThemeCategory.HOLO_LIGHT);

      if (info == null || info.getMinSdkVersion().getFeatureLevel() <= 14) {
        addCategory(topLevel, ThemeCategory.CLASSIC);
        addCategory(topLevel, ThemeCategory.CLASSIC_LIGHT);
      }
      addCategory(topLevel, ThemeCategory.DEVICE);
      addCategory(topLevel, ThemeCategory.DIALOGS);
      addCategory(topLevel, ThemeCategory.LIGHT);
      addCategory(topLevel, ThemeCategory.ALL);
      myLabels.put(ThemeCategory.ROOT, topLevel);

      // TODO: Use tree to add nesting; e.g. add holo light as a category under holo?
      //myLabels.put(ThemeCategory.LIGHT, Arrays.asList(ThemeCategory.ALL, ThemeCategory.DIALOGS));
    }

    private void addCategory(@NotNull List<ThemeCategory> categories, @NotNull ThemeCategory category) {
      if (!getThemes(category).isEmpty()) {
        categories.add(category);
      }
    }

    @Override
    public Object getRoot() {
      return ThemeCategory.ROOT;
    }

    @Override
    public Object getChild(Object parent, int index) {
      assert parent instanceof ThemeCategory;
      return myLabels.get(parent).get(index);
    }

    @Override
    public int getChildCount(Object parent) {
      assert parent instanceof ThemeCategory;
      List<ThemeCategory> list = myLabels.get(parent);
      return list == null ? 0 : list.size();
    }

    @Override
    public boolean isLeaf(Object node) {
      assert node instanceof ThemeCategory;
      return myLabels.get(node) == null;
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
      assert parent instanceof ThemeCategory;
      assert child instanceof ThemeCategory;
      List<ThemeCategory> list = myLabels.get(parent);
      return list == null ? -1 : list.indexOf(child);
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
    }
  }

  private enum ThemeCategory {
    ROOT(""),
    RECENT("Recent"),
    MANIFEST("Manifest Themes"),
    PROJECT("Project Themes"),
    MATERIAL_LIGHT("Material Light"),
    MATERIAL("Material Dark"),
    HOLO_LIGHT("Holo Light"),
    HOLO("Holo Dark"),
    CLASSIC("Classic"),
    CLASSIC_LIGHT("Classic Light"),
    DEVICE("Device Default"),
    DIALOGS("Dialogs"),
    LIGHT("Light"),
    ALL("All");
    // TODO: Add other logical types here, e.g. Wallpaper, Alert, etc?

    ThemeCategory(String name) {
      myName = name;
    }

    private final String myName;

    @Override
    public String toString() {
      return myName;
    }
  }

  public void focus() {
    final Project project = myConfiguration.getConfigModule().getProject();
    final IdeFocusManager focusManager = project.isDefault() ? IdeFocusManager.getGlobalInstance() : IdeFocusManager.getInstance(project);
    focusManager.doWhenFocusSettlesDown(() -> focusManager.requestFocus(myThemeList, true));
  }

  private static boolean haveMatches(String filter, List<String> themes) {
    for (String theme : themes) {
      if (matchesFilter(theme, filter)) {
        return true;
      }
    }
    return false;
  }

  private boolean haveAnyMatches(String filter) {
    return haveMatches(filter, myFrameworkThemes) || haveMatches(filter, myProjectThemes);
  }

  private void createUIComponents() {
    myFilter = new ThemeFilterComponent("ANDROID_THEME_HISTORY", 10, true);
    // Allow arrow up/down to navigate the filtered matches
    myFilter.getTextEditor().addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(final KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_UP) {
          myThemeList.dispatchEvent(e);
          e.consume();
        }
      }
    });
  }

  private class ThemeFilterComponent extends FilterComponent {
    private ThemeFilterComponent(@NonNls String propertyName, int historySize, boolean onTheFlyUpdate) {
      super(propertyName, historySize, onTheFlyUpdate);
    }

    @Override
    public void filter() {
      String filter = getFilter();
      assert filter != null;

      if (myCategory != ThemeCategory.ALL && !haveMatches(filter, getThemes(myCategory)) && haveAnyMatches(filter)) {
        // Switch to the All category
        selectCategory(ThemeCategory.ALL, false);
      }
      updateThemeList();
    }

    @Override
    protected void onEscape(@NotNull KeyEvent event) {
      focus();
      event.consume();
    }
  }

  public interface ThemeChangedListener {
    /**
     * Called when the theme has changed
     * @param name qualified name of the new theme
     */
    void themeChanged(@NotNull String name);
  }
}
