package com.android.tools.idea.editors.theme;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.ProjectResourceRepository;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.ide.common.resources.ResourceResolver.THEME_NAME;
import static com.android.ide.common.resources.ResourceResolver.THEME_NAME_DOT;

/**
 * Class that provides methods to resolve themes for a given configuration.
 */
public class ThemeResolver {
  private static final Logger LOG = Logger.getInstance(ThemeResolver.class);

  private final Set<String> myThemeNames = Sets.newHashSet();
  private final ImmutableList<ThemeEditorStyle> myFrameworkThemes;
  private final ImmutableList<ThemeEditorStyle> myLocalThemes;
  private final ImmutableList<ThemeEditorStyle> myExternalLibraryThemes;

  private final Configuration myConfiguration;
  private final ResourceResolver myResolver;

  public ThemeResolver(@NotNull Configuration configuration) {
    myConfiguration = configuration;
    myResolver = configuration.getResourceResolver();

    if (myResolver == null) {
      throw new IllegalArgumentException("Acquired ResourceResolver is null, not an Android module?");
    }

    myFrameworkThemes = fillThemeResolverFromStyles(resolveFrameworkThemes());
    myLocalThemes = fillThemeResolverFromStyles(resolveLocallyDefinedModuleThemes());

    // resolveNonFrameworkThemes() returns all themes available from the current module, including library themes.
    // Because all local themes would be added at previous step to myLocalThemes, they'll be ignored
    // at this step, and all that we've got here is library themes.
    myExternalLibraryThemes = fillThemeResolverFromStyles(resolveNonFrameworkThemes());
  }

  private ImmutableList<ThemeEditorStyle> fillThemeResolverFromStyles(@NotNull List<StyleResourceValue> source) {
    ImmutableList.Builder<ThemeEditorStyle> builder = ImmutableList.builder();

    for (StyleResourceValue value : source) {
      final String name = ResolutionUtils.getQualifiedStyleName(value);

      if (myThemeNames.contains(name)) {
        continue;
      }

      myThemeNames.add(name);
      final ThemeEditorStyle theme = ResolutionUtils.getStyle(myConfiguration, name);
      if (theme == null) {
        continue;
      }

      builder.add(theme);
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
  private List<StyleResourceValue> resolveLocallyDefinedModuleThemes() {
    LocalResourceRepository repository = ProjectResourceRepository.getProjectResources(myConfiguration.getModule(), true);
    if (repository == null) {
      return Collections.emptyList();
    }

    return getThemes(repository.getConfiguredResources(ResourceType.STYLE, myConfiguration.getFullConfig()), false /*isFramework*/);
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
  public ThemeEditorStyle getTheme(@NotNull String themeName) {
    if (myThemeNames.contains(themeName)) {
      return ResolutionUtils.getStyle(myConfiguration, themeName);
    }

    return null;
  }

  /**
   * Returns the list of themes available from the module passed Configuration comes from and all its dependencies.
   */
  @NotNull
  public ImmutableList<ThemeEditorStyle> getLocalThemes() {
    return myLocalThemes;
  }

  /**
   * Returns the list of themes that come from external libraries (e.g. AppCompat)
   */
  @NotNull
  public ImmutableList<ThemeEditorStyle> getExternalLibraryThemes() {
    return myExternalLibraryThemes;
  }

  /**
   * Returns the list of available framework themes.
   */
  @NotNull
  public ImmutableList<ThemeEditorStyle> getFrameworkThemes() {
    return myFrameworkThemes;
  }

  public int getThemesCount() {
    return myFrameworkThemes.size() + myExternalLibraryThemes.size() + myLocalThemes.size();
  }
}
