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

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.ManifestInfo;
import com.android.tools.idea.rendering.ProjectResources;
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

import static com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.ide.common.resources.ResourceResolver.THEME_NAME;
import static com.android.ide.common.resources.ResourceResolver.THEME_NAME_DOT;

/**
 * Theme selection dialog.
 * <p/>
 * TODO: In the future, make it easy to create new themes here, as well as assigning a theme
 * to an activity.
 */
public class ThemeSelectionPanel implements TreeSelectionListener, ListSelectionListener, Disposable {
  private static final String DEVICE_LIGHT_PREFIX = ANDROID_STYLE_RESOURCE_PREFIX + "Theme.DeviceDefault.Light";
  private static final String HOLO_LIGHT_PREFIX = ANDROID_STYLE_RESOURCE_PREFIX + "Theme.Holo.Light";
  private static final String DEVICE_PREFIX = ANDROID_STYLE_RESOURCE_PREFIX + "Theme.DeviceDefault";
  private static final String HOLO_PREFIX = ANDROID_STYLE_RESOURCE_PREFIX + "Theme.Holo";
  private static final String LIGHT_PREFIX = ANDROID_STYLE_RESOURCE_PREFIX + "Theme.Light";
  private static final String ANDROID_THEME = ANDROID_STYLE_RESOURCE_PREFIX + "Theme";
  private static final String ANDROID_THEME_PREFIX = ANDROID_STYLE_RESOURCE_PREFIX + "Theme.";
  private static final String PROJECT_THEME = STYLE_RESOURCE_PREFIX + "Theme";
  private static final String PROJECT_THEME_PREFIX = STYLE_RESOURCE_PREFIX + "Theme.";
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
  @Nullable private static Deque<String> ourRecent;
  @Nullable private ThemeCategory myCategory = ThemeCategory.ALL;
  @NotNull private Map<ThemeCategory, List<String>> myThemeMap = Maps.newEnumMap(ThemeCategory.class);
  private boolean myIgnore;

  public ThemeSelectionPanel(@NotNull ThemeSelectionDialog dialog, @NotNull Configuration configuration) {
    myDialog = dialog;
    myConfiguration = configuration;
    String currentTheme = configuration.getTheme();
    touchTheme(currentTheme);

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
          if (theme.startsWith(HOLO_LIGHT_PREFIX) || theme.startsWith(LIGHT_PREFIX) || theme.startsWith(DEVICE_LIGHT_PREFIX)) {
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
        Map<String, String> activityThemes = manifest.getActivityThemes();
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
        if (activityThemes.size() > 0 || manifestTheme != null) {
          Set<String> allThemes = new HashSet<String>(activityThemes.values());
          if (manifestTheme != null) {
            allThemes.add(manifestTheme);
          }
          List<String> sorted = new ArrayList<String>(allThemes);
          Collections.sort(sorted);
          for (String theme : sorted) {
            themes.add(theme);
          }
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
      myFrameworkThemes = getThemes(myConfiguration.getFrameworkResources(), true);
    }
    return myFrameworkThemes;
  }

  private List<String> getProjectThemes() {
    if (myProjectThemes == null) {
      ProjectResources repository = ProjectResources.get(myConfiguration.getModule(), true);
      Map<ResourceType, Map<String, ResourceValue>> resources = repository.getConfiguredResources(myConfiguration.getFullConfig());
      myProjectThemes = getThemes(myConfiguration, resources, false /*isFramework*/);
    }

    return myProjectThemes;
  }

  // ---- Implements ListSelectionListener ----
  @Override
  public void valueChanged(ListSelectionEvent listSelectionEvent) {
    if (myIgnore) {
      return;
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
    touchTheme(selected);
    return selected;
  }

  private static void touchTheme(@Nullable String selected) {
    if (selected != null) {
      if (ourRecent == null || !ourRecent.contains(selected)) {
        if (ourRecent == null) {
          ourRecent = new LinkedList<String>();
        }
        ourRecent.addFirst(selected);
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

      // TODO: Only offer Holo if build target >= 11?
      topLevel.add(ThemeCategory.HOLO);
      topLevel.add(ThemeCategory.HOLO_LIGHT);
      topLevel.add(ThemeCategory.CLASSIC);
      topLevel.add(ThemeCategory.CLASSIC_LIGHT);
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

  private List<String> getThemes(@Nullable ResourceRepository repository, boolean isFramework) {
    if (repository == null) {
      return Collections.emptyList();
    }

    Map<ResourceType, Map<String, ResourceValue>> resources = repository.getConfiguredResources(myConfiguration.getFullConfig());
    return getThemes(myConfiguration, resources, isFramework);
  }

  private static List<String> getThemes(Configuration configuration, Map<ResourceType, Map<String, ResourceValue>> resources, boolean isFramework) {
    String prefix = isFramework ? ANDROID_STYLE_RESOURCE_PREFIX : STYLE_RESOURCE_PREFIX;
    // get the styles.
    Map<String, ResourceValue> styles = resources.get(ResourceType.STYLE);

    // Collect the themes out of all the styles.
    Collection<ResourceValue> values = styles.values();
    List<String> themes = new ArrayList<String>(values.size());

    if (!isFramework) {
      // Try a little harder to see if the user has themes that don't have the normal naming convention
      ResourceResolver resolver = configuration.getResourceResolver();
      if (resolver != null) {
        Map<ResourceValue, Boolean> cache = Maps.newHashMapWithExpectedSize(values.size());
        for (ResourceValue value : values) {
          if (value instanceof StyleResourceValue) {
            StyleResourceValue styleValue = (StyleResourceValue)value;
            boolean isTheme = resolver.isTheme(styleValue, cache);
            if (isTheme) {
              String name = value.getName();
              themes.add(prefix + name);
            }
          }
        }

        Collections.sort(themes);
        return themes;
      }
    }

    // For the framework (and projects if resolver can't be computed) the computation is easier
    for (ResourceValue value : values) {
      String name = value.getName();
      if (name.startsWith(THEME_NAME_DOT) || name.equals(THEME_NAME)) {
        themes.add(prefix + name);
      }
    }
    Collections.sort(themes);
    return themes;
  }

  private enum ThemeCategory {
    ROOT(""),
    RECENT("Recent"),
    MANIFEST("Manifest Themes"),
    PROJECT("Project Themes"),
    HOLO("Holo"),
    HOLO_LIGHT("Holo Light"),
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
}
