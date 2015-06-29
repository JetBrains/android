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

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Provides the ability to resolve all themes available in a particular project.
 * Is intended to be a replacement for ThemeResolver, which for now provides
 * more functionality but has an assumption of single module built-in and
 * contains a lot of unused code.
 *
 * TODO(ddrone): update the comment above when ThemeResolver is gone
 */
public class ProjectThemeResolver {
  private static final Logger LOG = Logger.getInstance(ProjectThemeResolver.class);

  /**
   * Pair used for storing theme together with Module which it comes from
   *
   * TODO(ddrone):
   * This class should be got rid of (Module can be stored in ThemeEditorStyle directly),
   * however, this required a fair bit of refactoring of ThemeEditorStyle.
   *
   * TODO(ddrone): After getting rid of this class, move getEditableProjectThemes to ThemeEditorUtils
   */
  public static class ThemeWithSource {
    final @NotNull ThemeEditorStyle myTheme;
    final @NotNull Module mySourceModule;

    public ThemeWithSource(@NotNull ThemeEditorStyle theme, @NotNull Module sourceModule) {
      myTheme = theme;
      mySourceModule = sourceModule;
    }

    @NotNull
    public ThemeEditorStyle getTheme() {
      return myTheme;
    }

    @NotNull
    public Module getSourceModule() {
      return mySourceModule;
    }
  }

  public static ImmutableList<ThemeWithSource> getEditableProjectThemes(@NotNull final Project project) {
    final VirtualFile projectFile = project.getProjectFile();
    assert projectFile != null : String.format("Project %s doesn't have project file", project.getName());

    ImmutableList.Builder<ThemeWithSource> builder = new ImmutableList.Builder<ThemeWithSource>();

    // Looping through all modules in the project to collect all available themes.
    for (final Module module : ModuleManager.getInstance(project).getModules()) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet == null) {
        // Not an Android module, skipping it.
        continue;
      }

      // TODO(ddrone): this code basically replicates the way Configuration was acquired prior to multiple modules,
      // but it doesn't work properly - getConfiguration() should be called on proper resource file.
      // Fixing that requires a lot of changes elsewhere, and thus, it postponed to a later CL
      final Configuration configuration = facet.getConfigurationManager().getConfiguration(projectFile);
      final StyleResolver resolver = new StyleResolver(configuration);

      LocalResourceRepository resources = facet.getModuleResources(true);
      Map<String, ResourceValue> styles = resources.getConfiguredResources(ResourceType.STYLE, configuration.getFullConfig());

      for (StyleResourceValue value : getThemesFromResources(styles, configuration)) {
        // Iterate through all themes that are available in the current module
        ThemeEditorStyle themeEditorStyle = resolver.getStyle(value.getName());
        assert themeEditorStyle != null : "Style with name " + value.getName() + " doesn't exist";
        builder.add(new ThemeWithSource(themeEditorStyle, module));
      }
    }

    return builder.build();
  }

  @NotNull
  private static List<StyleResourceValue> getThemesFromResources(@NotNull Map<String, ResourceValue> styles,
                                                                 @NotNull Configuration configuration) {
    // Collect the themes out of all the styles.
    Collection<ResourceValue> values = styles.values();
    List<StyleResourceValue> themes = new ArrayList<StyleResourceValue>(values.size());

    // Try a little harder to see if the user has themes that don't have the normal naming convention
    ResourceResolver resolver = configuration.getResourceResolver();
    assert resolver != null;

    Map<ResourceValue, Boolean> cache = Maps.newHashMapWithExpectedSize(values.size());
    for (ResourceValue value : values) {
      if (value instanceof StyleResourceValue) {
        StyleResourceValue styleValue = (StyleResourceValue)value;
        if (resolver.isTheme(styleValue, cache)) {
          themes.add(styleValue);
        }
      }
    }

    return themes;
  }
}
