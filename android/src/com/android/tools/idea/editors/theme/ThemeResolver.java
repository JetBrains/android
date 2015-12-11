package com.android.tools.idea.editors.theme;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.ModuleResourceRepository;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.ide.common.resources.ResourceResolver.THEME_NAME;
import static com.android.ide.common.resources.ResourceResolver.THEME_NAME_DOT;

/**
 * Class that provides methods to resolve themes for a given configuration.
 */
public class ThemeResolver {
  private final Map<String, ConfiguredThemeEditorStyle> myThemeByName = Maps.newHashMap();
  private final ImmutableList<ConfiguredThemeEditorStyle> myFrameworkThemes;
  private final ImmutableList<ConfiguredThemeEditorStyle> myLocalThemes;
  private final ImmutableList<ConfiguredThemeEditorStyle> myExternalLibraryThemes;

  private final Configuration myConfiguration;
  private final ResourceResolver myResolver;

  public ThemeResolver(@NotNull Configuration configuration) {
    myConfiguration = configuration;
    myResolver = configuration.getResourceResolver();

    if (myResolver == null) {
      throw new IllegalArgumentException("Acquired ResourceResolver is null, not an Android module?");
    }

    myFrameworkThemes = fillThemeResolverFromStyles(resolveFrameworkThemes());

    final ImmutableList.Builder<ConfiguredThemeEditorStyle> localThemes = ImmutableList.builder();
    for (Pair<StyleResourceValue, Module> pair : resolveLocallyDefinedModuleThemes()) {
      final ConfiguredThemeEditorStyle theme = constructThemeFromResourceValue(pair.getFirst(), pair.getSecond());
      if (theme != null) {
        localThemes.add(theme);
      }
    }
    myLocalThemes = localThemes.build();

    // resolveNonFrameworkThemes() returns all themes available from the current module, including library themes.
    // Because all local themes would be added at previous step to myLocalThemes, they'll be ignored
    // at this step, and all that we've got here is library themes.
    myExternalLibraryThemes = fillThemeResolverFromStyles(resolveNonFrameworkThemes());
  }

  /**
   * Create a ThemeEditorStyle instance stored in ThemeResolver, which can be added to one of theme lists.
   */
  @Nullable("if theme with this name was already added or resolution has failed")
  private ConfiguredThemeEditorStyle constructThemeFromResourceValue(@NotNull StyleResourceValue value, @Nullable Module sourceModule) {
    final String name = ResolutionUtils.getQualifiedStyleName(value);

    if (myThemeByName.containsKey(name)) {
      return null;
    }

    final ConfiguredThemeEditorStyle theme = ResolutionUtils.getStyle(myConfiguration, name, sourceModule);
    if (theme != null) {
      myThemeByName.put(name, theme);
    }

    return theme;
  }

  private ImmutableList<ConfiguredThemeEditorStyle> fillThemeResolverFromStyles(@NotNull List<StyleResourceValue> source) {
    ImmutableList.Builder<ConfiguredThemeEditorStyle> builder = ImmutableList.builder();

    for (StyleResourceValue value : source) {
      ConfiguredThemeEditorStyle theme = constructThemeFromResourceValue(value, null);
      if (theme != null) {
        builder.add(theme);
      }
    }

    return builder.build();
  }

  @NotNull
  private List<StyleResourceValue> resolveFrameworkThemes() {
    ResourceRepository repository = myConfiguration.getFrameworkResources();
    if (repository == null) {
      return Collections.emptyList();
    }

    return getThemes(repository.getConfiguredResources(myConfiguration.getFullConfig()).get(ResourceType.STYLE), true /*isFramework*/);
  }

  /**
   * Resolve all non-framework themes available from module of passed Configuration
   */
  @NotNull
  private List<StyleResourceValue> resolveNonFrameworkThemes() {
    LocalResourceRepository repository = AppResourceRepository.getAppResources(myConfiguration.getModule(), true);
    if (repository == null) {
      return Collections.emptyList();
    }

    return getThemes(repository.getConfiguredResources(ResourceType.STYLE, myConfiguration.getFullConfig()), false /*isFramework*/);
  }

  /**
   * Resolve all themes available from passed Configuration's source module and its dependencies which are defined
   * in the current project (doesn't include themes available from libraries)
   */
  @NotNull
  private List<Pair<StyleResourceValue, Module>> resolveLocallyDefinedModuleThemes() {
    final Module module = myConfiguration.getModule();
    final List<Pair<StyleResourceValue, Module>> result = Lists.newArrayList();

    fillModuleResources(module, ModuleResourceRepository.getModuleResources(module, true), result);

    final List<AndroidFacet> allAndroidDependencies = AndroidUtils.getAllAndroidDependencies(module, false);
    for (AndroidFacet facet : allAndroidDependencies) {
      fillModuleResources(facet.getModule(), facet.getModuleResources(true), result);
    }

    return result;
  }

  private void fillModuleResources(@NotNull Module module,
                                   @Nullable LocalResourceRepository repository,
                                   @NotNull List<Pair<StyleResourceValue, Module>> sink) {
    if (repository == null) {
      return;
    }

    for (StyleResourceValue value : getThemes(repository.getConfiguredResources(ResourceType.STYLE, myConfiguration.getFullConfig()), false)) {
      sink.add(Pair.create(value, module));
    }
  }

  @NotNull
  private List<StyleResourceValue> getThemes(@NotNull Map<String, ResourceValue> styles,
                                             boolean isFramework) {
    // Collect the themes out of all the styles.
    Collection<ResourceValue> values = styles.values();
    List<StyleResourceValue> themes = new ArrayList<StyleResourceValue>(values.size());

    if (!isFramework) {
      Map<ResourceValue, Boolean> cache = Maps.newHashMapWithExpectedSize(values.size());
      for (ResourceValue value : values) {
        if (value instanceof StyleResourceValue) {
          StyleResourceValue styleValue = (StyleResourceValue)value;
          if (myResolver.isTheme(styleValue, cache)) {
            themes.add(styleValue);
          }
        }
      }
      return themes;
    }

    // For the framework themes the computation is easier
    for (ResourceValue value : values) {
      String name = value.getName();
      if (name.startsWith(THEME_NAME_DOT) || name.equals(THEME_NAME)) {
        themes.add((StyleResourceValue)value);
      }
    }
    return themes;
  }

  @Nullable
  public ConfiguredThemeEditorStyle getTheme(@NotNull String themeName) {
    return myThemeByName.get(themeName);
  }

  /**
   * Returns the list of themes available from the module passed Configuration comes from and all its dependencies.
   */
  @NotNull
  public ImmutableList<ConfiguredThemeEditorStyle> getLocalThemes() {
    return myLocalThemes;
  }

  /**
   * Returns the list of themes that come from external libraries (e.g. AppCompat)
   */
  @NotNull
  public ImmutableList<ConfiguredThemeEditorStyle> getExternalLibraryThemes() {
    return myExternalLibraryThemes;
  }

  /**
   * Returns the list of available framework themes.
   */
  @NotNull
  public ImmutableList<ConfiguredThemeEditorStyle> getFrameworkThemes() {
    return myFrameworkThemes;
  }

  public int getThemesCount() {
    return myFrameworkThemes.size() + myExternalLibraryThemes.size() + myLocalThemes.size();
  }

  @NotNull
  Configuration getConfiguration() {
    return myConfiguration;
  }
}
