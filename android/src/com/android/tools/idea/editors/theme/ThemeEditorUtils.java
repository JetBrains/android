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

import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.android.tools.idea.javadoc.AndroidJavaDocRenderer;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.google.common.base.Predicate;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;


/**
 * Utility class for static methods which are used in different classes of theme editor
 */
public class ThemeEditorUtils {
  private static final Logger LOG = Logger.getInstance(ThemeEditorUtils.class);

  private static final Cache<String, String> ourTooltipCache = CacheBuilder.newBuilder().weakValues().maximumSize(30) // To be able to cache roughly one screen of attributes
    .build();
  private static final Set<String> DEFAULT_THEMES = ImmutableSet.of("Theme.AppCompat.NoActionBar", "Theme.AppCompat.Light.NoActionBar");
  private static final Set<String> DEFAULT_THEMES_FALLBACK =
    ImmutableSet.of("Theme.Material.NoActionBar", "Theme.Material.Light.NoActionBar");

  private static final String[] CUSTOM_WIDGETS_JAR_PATHS = {
    // Bundled path
    "/plugins/android/lib/androidWidgets/theme-editor-widgets.jar",
    // Development path
    "/../adt/idea/android/lib/androidWidgets/theme-editor-widgets.jar"
  };

  public static final Comparator<ThemeEditorStyle> STYLE_COMPARATOR = new Comparator<ThemeEditorStyle>() {
    @Override
    public int compare(ThemeEditorStyle o1, ThemeEditorStyle o2) {
      if (o1.isProjectStyle() == o2.isProjectStyle()) {
        return o1.getName().compareTo(o2.getName());
      }

      return o1.isProjectStyle() ? -1 : 1;
    }
  };

  private ThemeEditorUtils() {
  }

  @NotNull
  public static String generateToolTipText(@NotNull final ItemResourceValue resValue,
                                           @NotNull final Module module,
                                           @NotNull final Configuration configuration) {
    final LocalResourceRepository repository = AppResourceRepository.getAppResources(module, true);
    if (repository == null) {
      return "";
    }

    String tooltipKey = resValue.toString() + module.toString() + configuration.toString() + repository.getModificationCount();

    String cachedTooltip = ourTooltipCache.getIfPresent(tooltipKey);
    if (cachedTooltip != null) {
      return cachedTooltip;
    }

    String tooltipContents = AndroidJavaDocRenderer.renderItemResourceWithDoc(module, configuration, resValue);
    ourTooltipCache.put(tooltipKey, tooltipContents);

    return tooltipContents;
  }

  /**
   * Returns html that will be displayed in attributes table for a given item.
   * For example: deprecated attrs will be with a strike through
   */
  @NotNull
  public static String getDisplayHtml(EditedStyleItem item) {
    return item.isDeprecated() ? "<html><body><strike>" + item.getQualifiedName() + "</strike></body></html>" : item.getQualifiedName();
  }

  public static void openThemeEditor(@NotNull final Project project) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ThemeEditorVirtualFile file = null;
        final FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);

        for (final FileEditor editor : fileEditorManager.getAllEditors()) {
          if (!(editor instanceof ThemeEditor)) {
            continue;
          }

          ThemeEditor themeEditor = (ThemeEditor)editor;
          if (themeEditor.getVirtualFile().getProject() == project) {
            file = themeEditor.getVirtualFile();
            break;
          }
        }

        // If existing virtual file is found, openEditor with created descriptor is going to
        // show existing editor (without creating a new tab). If we haven't found any existing
        // virtual file, we're creating one here (new tab with theme editor will be opened).
        if (file == null) {
          file = ThemeEditorVirtualFile.getThemeEditorFile(project);
        }
        final OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file);
        fileEditorManager.openEditor(descriptor, true);
      }
    });
  }

  public static List<EditedStyleItem> resolveAllAttributes(final ThemeEditorStyle style) {
    final List<EditedStyleItem> allValues = new ArrayList<EditedStyleItem>();
    final Set<String> namesSet = new TreeSet<String>();

    ThemeEditorStyle currentStyle = style;
    while (currentStyle != null) {
      for (final ItemResourceValue value : currentStyle.getValues()) {
        String itemName = StyleResolver.getQualifiedItemName(value);
        if (!namesSet.contains(itemName)) {
          allValues.add(new EditedStyleItem(value, currentStyle));
          namesSet.add(itemName);
        }
      }

      currentStyle = currentStyle.getParent();
    }

    // Sort the list of items in alphabetical order of the name of the items
    // so that the ordering of the list is not modified by overriding attributes
    Collections.sort(allValues, new Comparator<EditedStyleItem>() {
      // Is not consistent with equals
      @Override
      public int compare(EditedStyleItem item1, EditedStyleItem item2) {
        return item1.getQualifiedName().compareTo(item2.getQualifiedName());
      }
    });
    return allValues;
  }

  @Nullable
  public static Object extractRealValue(@NotNull final EditedStyleItem item, @NotNull final Class<?> desiredClass) {
    String value = item.getValue();
    if (desiredClass == Boolean.class && ("true".equals(value) || "false".equals(value))) {
      return Boolean.valueOf(value);
    }
    if (desiredClass == Integer.class && value != null) {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException e) {
        return value;
      }
    }
    return value;
  }

  public static boolean acceptsFormat(@Nullable AttributeDefinition attrDefByName, @NotNull AttributeFormat want) {
    if (attrDefByName == null) {
      return false;
    }
    return attrDefByName.getFormats().contains(want);
  }

  public static boolean createColor(@NotNull final Module module, @NotNull final String colorName, @NotNull final String colorValue) {
    final String fileName = AndroidResourceUtil.getDefaultResourceFileName(ResourceType.COLOR);
    if (fileName == null) {
      return false;
    }
    final List<String> dirNames = Collections.singletonList(ResourceFolderType.VALUES.getName());

    return AndroidResourceUtil.createValueResource(module, colorName, ResourceType.COLOR, fileName, dirNames, colorValue);
  }

  public static boolean changeColor(@NotNull final Module module, @NotNull final String colorName, @NotNull final String colorValue) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return false;
    }

    final String fileName = AndroidResourceUtil.getDefaultResourceFileName(ResourceType.COLOR);
    if (fileName == null) {
      return false;
    }
    final List<String> dirNames = Collections.singletonList(ResourceFolderType.VALUES.getName());

    try {
      if (!AndroidResourceUtil.changeColorResource(facet, colorName, colorValue, fileName, dirNames)) {
        // Changing color resource has failed, one possible reason is that color isn't defined in the project.
        // Trying to create the color instead.
        return AndroidResourceUtil.createValueResource(module, colorName, ResourceType.COLOR, fileName, dirNames, colorValue);
      }

      return true;
    }
    catch (Exception e) {
      return false;
    }
  }

  @NotNull
  private static Collection<ThemeEditorStyle> findThemes(@NotNull Collection<ThemeEditorStyle> themes, final @NotNull Set<String> names) {
    return ImmutableSet.copyOf(Iterables.filter(themes, new Predicate<ThemeEditorStyle>() {
      @Override
      public boolean apply(@Nullable ThemeEditorStyle theme) {
        return theme != null && names.contains(theme.getSimpleName());
      }
    }));
  }

  @NotNull
  public static ImmutableList<ThemeEditorStyle> getDefaultThemes(@NotNull ThemeResolver themeResolver) {
    Collection<ThemeEditorStyle> editableThemes = themeResolver.getLocalThemes();
    Collection<ThemeEditorStyle> readOnlyLibThemes = new HashSet<ThemeEditorStyle>(themeResolver.getProjectThemes());
    readOnlyLibThemes.removeAll(editableThemes);

    Collection<ThemeEditorStyle> foundThemes = findThemes(readOnlyLibThemes, DEFAULT_THEMES);

    if (foundThemes.isEmpty()) {
      Collection<ThemeEditorStyle> readOnlyFrameworkThemes = themeResolver.getFrameworkThemes();
      foundThemes = findThemes(readOnlyFrameworkThemes, DEFAULT_THEMES_FALLBACK);
      if (foundThemes.isEmpty()) {
        foundThemes.addAll(readOnlyLibThemes);
        foundThemes.addAll(readOnlyFrameworkThemes);
      }
    }
    Set<ThemeEditorStyle> temporarySet = new TreeSet<ThemeEditorStyle>(STYLE_COMPARATOR);
    temporarySet.addAll(foundThemes);
    return ImmutableList.copyOf(temporarySet);
  }

  public static int getMinApiLevel(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return 1;
    }
    AndroidModuleInfo moduleInfo = AndroidModuleInfo.get(facet);
    return moduleInfo.getMinSdkVersion().getApiLevel();
  }

  /**
   * Returns the URL for the theme editor custom widgets jar
   */
  @Nullable
  public static URL getCustomWidgetsJarUrl() {
    String homePath = FileUtil.toSystemIndependentName(PathManager.getHomePath());

    StringBuilder notFoundPaths = new StringBuilder();
    for(String path : CUSTOM_WIDGETS_JAR_PATHS) {
      String jarPath = homePath + path;
      VirtualFile root = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(jarPath));

      if (root != null) {
        File rootFile = VfsUtilCore.virtualToIoFile(root);
        if (rootFile.exists()) {
          try {
            LOG.debug("Theme editor custom widgets found at " + jarPath);
            return rootFile.toURI().toURL();
          }
          catch (MalformedURLException e) {
            LOG.error(e);
          }
        }
      }
      else {
        notFoundPaths.append(jarPath).append('\n');
      }
    }

    LOG.error("Unable to find theme-editor-widgets.jar in paths:\n" + notFoundPaths.toString());
    return null;
  }
}
