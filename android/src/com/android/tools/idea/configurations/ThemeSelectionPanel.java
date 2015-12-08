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

import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.android.tools.idea.editors.theme.ThemeResolver;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.ManifestInfo;
import com.android.tools.idea.model.ManifestInfo.ActivityAttributes;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.treeStructure.Tree;
import icons.AndroidIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.*;

import static com.android.SdkConstants.PREFIX_ANDROID;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.ide.common.resources.ResourceResolver.THEME_NAME;

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
  private static final String ANDROID_THEME = PREFIX_ANDROID + "Theme";
  private static final String ANDROID_THEME_PREFIX = PREFIX_ANDROID + "Theme.";
  private static final String PROJECT_THEME = "Theme";
  private static final String PROJECT_THEME_PREFIX = "Theme.";
  private static final String DIALOG_SUFFIX = ".Dialog";
  private static final String DIALOG_PART = ".Dialog.";
  private static final SimpleTextAttributes SEARCH_HIGHLIGHT_ATTRIBUTES =
    new SimpleTextAttributes(null, JBColor.MAGENTA, null, SimpleTextAttributes.STYLE_BOLD);

  @NotNull private final Configuration myConfiguration;
  @NotNull private final ThemeSelectionDialog myDialog;
  @NotNull private JBList myThemeList;
  @NotNull private Tree myCategoryTree;
  @NotNull private JPanel myContentPanel;
  @NotNull private ThemeFilterComponent myFilter;
  @Nullable private List<String> myFrameworkThemes;
  @Nullable private List<String> myProjectThemes;
  @Nullable private List<String> myLibraryThemes;
  @Nullable private static Deque<String> ourRecent;
  @Nullable private ThemeCategory myCategory = ThemeCategory.ALL;
  @NotNull private Map<ThemeCategory, List<String>> myThemeMap = Maps.newEnumMap(ThemeCategory.class);
  @NotNull private ThemeResolver myThemeResolver;
  @NotNull private Set<String> myExcludedThemes;
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
    myThemeResolver = new ThemeResolver(configuration);
    String currentTheme = configuration.getTheme();
    if (currentTheme != null) {
      currentTheme = ResolutionUtils.getQualifiedNameFromResourceUrl(currentTheme);
    }
    touchTheme(currentTheme, myExcludedThemes);

    myCategoryTree.setModel(new CategoryModel());
    myCategoryTree.setRootVisible(false);
    myCategoryTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myCategoryTree.addTreeSelectionListener(this);
    setInitialSelection(currentTheme);
    myThemeList.addListSelectionListener(this);
    myThemeList.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        setIcon(AndroidIcons.Themes);

        String style = (String)value;
        String filter = myFilter.getFilter();
        if (style.startsWith(ANDROID_THEME_PREFIX)) {
          style = style.substring(ANDROID_THEME_PREFIX.length());
        }
        else if (style.startsWith(PROJECT_THEME_PREFIX)) {
          style = style.substring(PROJECT_THEME_PREFIX.length());
        }
        else if (style.startsWith(STYLE_RESOURCE_PREFIX)) {
          style = style.substring(STYLE_RESOURCE_PREFIX.length());
        }
        else if (style.equals(ANDROID_THEME) || style.equals(PROJECT_THEME)) {
          style = THEME_NAME;
        }

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

    themes = new ArrayList<String>(50);

    switch (category) {
      case RECENT:
        if (ourRecent != null) {
          for (String theme : ourRecent) {
            themes.add(theme);
          }
        }
        break;
      case HOLO:
        for (String theme : getFrameworkThemes()) {
          if (theme.startsWith(HOLO_PREFIX) && !theme.startsWith(HOLO_LIGHT_PREFIX)) {
            themes.add(theme);
          }
        }
        break;
      case HOLO_LIGHT:
        for (String theme : getFrameworkThemes()) {
          if (theme.startsWith(HOLO_LIGHT_PREFIX)) {
            themes.add(theme);
          }
        }
        break;
      case MATERIAL:
        for (String theme : getFrameworkThemes()) {
          if (theme.startsWith(MATERIAL_PREFIX) && !theme.startsWith(MATERIAL_LIGHT_PREFIX)) {
            themes.add(theme);
          }
        }
        break;
      case MATERIAL_LIGHT:
        for (String theme : getFrameworkThemes()) {
          if (theme.startsWith(MATERIAL_LIGHT_PREFIX)) {
            themes.add(theme);
          }
        }
        break;
      case PROJECT:
        for (String theme : getProjectThemes()) {
          themes.add(theme);
        }
        break;
      case CLASSIC:
        for (String theme : getFrameworkThemes()) {
          if (!theme.startsWith(HOLO_PREFIX) && !theme.startsWith(DEVICE_PREFIX)) {
            themes.add(theme);
          }
        }
        break;
      case CLASSIC_LIGHT:
        for (String theme : getFrameworkThemes()) {
          if (theme.startsWith(LIGHT_PREFIX)) {
            themes.add(theme);
          }
        }
        break;
      case LIGHT:
        for (String theme : getFrameworkThemes()) {
          if (theme.startsWith(HOLO_LIGHT_PREFIX) || theme.startsWith(LIGHT_PREFIX) || theme.startsWith(DEVICE_LIGHT_PREFIX)
              || theme.startsWith(MATERIAL_LIGHT_PREFIX)) {
            themes.add(theme);
          }
        }
        break;
      case DEVICE:
        for (String theme : getFrameworkThemes()) {
          if (theme.startsWith(DEVICE_PREFIX)) {
            themes.add(theme);
          }
        }
        break;
      case DIALOGS:
        for (String theme : getProjectThemes()) {
          if (theme.endsWith(DIALOG_SUFFIX) || theme.contains(DIALOG_PART)) {
            themes.add(theme);
          }
        }
        for (String theme : getFrameworkThemes()) {
          if (theme.endsWith(DIALOG_SUFFIX) || theme.contains(DIALOG_PART)) {
            themes.add(theme);
          }
        }
        break;
      case MANIFEST: {
        ManifestInfo manifest = ManifestInfo.get(myConfiguration.getModule());
        Map<String, ActivityAttributes> activityAttributesMap = manifest.getActivityAttributesMap();
        /*
        TODO: Until we don't sort the theme lists automatically, no need to call out the preferred one first
        String activity = myConfiguration.getActivity();
        if (activity != null) {
          String theme = activityThemes.get(activity);
          if (theme != null) {
            themes.add(theme);
          }
        }
        */

        String manifestTheme = manifest.getManifestTheme();
        Set<String> allThemes = new HashSet<String>();
        if (manifestTheme != null) {
          allThemes.add(manifestTheme);
        }
        for (ActivityAttributes info : activityAttributesMap.values()) {
          if (info.getTheme() != null) {
            allThemes.add(info.getTheme());
          }
        }
        List<String> sorted = new ArrayList<String>(allThemes);
        Collections.sort(sorted);

        for (String theme : sorted) {
          themes.add(ResolutionUtils.getQualifiedNameFromResourceUrl(theme));
        }

        break;
      }
      case ALL:
        for (String theme : getProjectThemes()) {
          themes.add(theme);
        }
        for (String theme : getFrameworkThemes()) {
          themes.add(theme);
        }
        for (String theme : getLibraryThemes()) {
          themes.add(theme);
        }
        break;
      case ROOT:
      default:
        assert false : category;
        break;
    }

    myThemeMap.put(category, themes);
    return themes;
  }

  private void updateThemeList() {
    if (myCategory == null) {
      return;
    }

    String selected = (String)myThemeList.getSelectedValue();

    SortedListModel<String> model = new SortedListModel<String>(String.CASE_INSENSITIVE_ORDER);
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

  private List<String> getFrameworkThemes() {
    if (myFrameworkThemes == null) {
      myFrameworkThemes = getFilteredSortedNames(getPublicThemes(myThemeResolver.getFrameworkThemes()), myExcludedThemes);
    }
    return myFrameworkThemes;
  }

  private List<String> getProjectThemes() {
    if (myProjectThemes == null) {
      myProjectThemes = getFilteredSortedNames(getPublicThemes(myThemeResolver.getLocalThemes()), myExcludedThemes);
    }

    return myProjectThemes;
  }

  private List<String> getLibraryThemes() {
    if (myLibraryThemes == null) {
      myLibraryThemes = getFilteredPrefixesSortedNames(getPublicThemes(myThemeResolver.getExternalLibraryThemes()), myExcludedThemes,
                                                       Collections.singleton("Base."));
    }

    return myLibraryThemes;
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
    String selected = (String)myThemeList.getSelectedValue();
    touchTheme(selected, myExcludedThemes);
    return selected;
  }

  private static void touchTheme(@Nullable String selected, Set<String> excludedThemes) {
    if (selected != null) {
      if (ourRecent == null || !ourRecent.contains(selected)) {
        if (ourRecent == null) {
          ourRecent = new LinkedList<String>();
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
      List<ThemeCategory> topLevel = Lists.newArrayList();

      if (ourRecent != null) {
        topLevel.add(ThemeCategory.RECENT);
      }

      if (!getThemes(ThemeCategory.MANIFEST).isEmpty()) {
        topLevel.add(ThemeCategory.MANIFEST);
      }

      if (!getThemes(ThemeCategory.PROJECT).isEmpty()) {
        topLevel.add(ThemeCategory.PROJECT);
      }

      AndroidModuleInfo info = AndroidModuleInfo.get(myConfiguration.getConfigurationManager().getModule());
      if (info != null && info.getBuildSdkVersion() != null && info.getBuildSdkVersion().getFeatureLevel() >= 21) {
        topLevel.add(ThemeCategory.MATERIAL);
        topLevel.add(ThemeCategory.MATERIAL_LIGHT);
      }

      topLevel.add(ThemeCategory.HOLO);
      topLevel.add(ThemeCategory.HOLO_LIGHT);
      if (info == null || info.getMinSdkVersion().getFeatureLevel() <= 14) {
        topLevel.add(ThemeCategory.CLASSIC);
        topLevel.add(ThemeCategory.CLASSIC_LIGHT);
      }
      topLevel.add(ThemeCategory.DEVICE);
      topLevel.add(ThemeCategory.DIALOGS);
      topLevel.add(ThemeCategory.LIGHT);
      topLevel.add(ThemeCategory.ALL);
      myLabels.put(ThemeCategory.ROOT, topLevel);

      // TODO: Use tree to add nesting; e.g. add holo light as a category under holo?
      //myLabels.put(ThemeCategory.LIGHT, Arrays.asList(ThemeCategory.ALL, ThemeCategory.DIALOGS));
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

  /**
   * Sorts the themes in themesRaw excluding those in excludedThemes
   *
   * @param themesRaw      themes to process
   * @param excludedThemes themes to filter out
   * @return the sorted themes excluding those in excludedThemes
   */
  private static List<String> getFilteredSortedNames(Collection<ConfiguredThemeEditorStyle> themesRaw, Set<String> excludedThemes) {
    return getFilteredPrefixesSortedNames(themesRaw, excludedThemes, Collections.<String>emptySet());
  }

  /**
   * Sorts the themes in themesRaw excluding those in excludedThemes and those starting with prefixes in excludedPrefixes
   *
   * @param themesRaw themes to process
   * @param excludedThemes themes to filter out
   * @param excludedPrefixes set of prefixes of the themes to filter
   * @return the sorted themes excluding those in excludedThemes or starting with a prefix in excludedPrefixes
   */
  private static List<String> getFilteredPrefixesSortedNames(Collection<ConfiguredThemeEditorStyle> themesRaw,
                                                             Set<String> excludedThemes,
                                                             Set<String> excludedPrefixes) {
    List<String> themes = new ArrayList<String>(themesRaw.size());
    for (ConfiguredThemeEditorStyle theme : themesRaw) {
      String qualifiedName = theme.getQualifiedName();
      if (!excludedThemes.contains(qualifiedName)) {
        boolean startWithPrefix = false;
        String themeName = theme.getName();
        for (String prefix : excludedPrefixes) {
          if (themeName.startsWith(prefix)) {
            startWithPrefix = true;
            break;
          }
        }
        if (!startWithPrefix) {
          themes.add(qualifiedName);
        }
      }
    }
    Collections.sort(themes);
    return themes;
  }

  /**
   * Filters a collection of themes to return a new collection with only the public ones.
   */
  private static Collection<ConfiguredThemeEditorStyle> getPublicThemes(Collection<ConfiguredThemeEditorStyle> themes) {
    HashSet<ConfiguredThemeEditorStyle> publicThemes = new HashSet<ConfiguredThemeEditorStyle>();
    for (ConfiguredThemeEditorStyle theme : themes) {
      if (theme.isPublic()) {
        publicThemes.add(theme);
      }
    }
    return publicThemes;
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
    final Project project = myConfiguration.getModule().getProject();
    final IdeFocusManager focusManager = project.isDefault() ? IdeFocusManager.getGlobalInstance() : IdeFocusManager.getInstance(project);
    focusManager.doWhenFocusSettlesDown(new Runnable() {
      @Override
      public void run() {
        focusManager.requestFocus(myThemeList, true);
      }
    });
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
    return haveMatches(filter, getFrameworkThemes()) || haveMatches(filter, getProjectThemes());
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
    protected void onEscape(KeyEvent e) {
      focus();
      e.consume();
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
