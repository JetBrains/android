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
import com.google.common.collect.ForwardingQueue;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static com.android.ide.common.resources.ResourceResolver.THEME_NAME;
import static com.android.ide.common.resources.ResourceResolver.THEME_NAME_DOT;

/**
 * Class that provides methods to resolve themes for a given configuration.
 *
 * TODO(ddrone): get rid of this class
 * @deprecated this class is supposed to be replaced by ProjectThemeResolver
 */
@Deprecated
public class ThemeResolver {
  @SuppressWarnings("ConstantNamingConvention")
  private static final Logger LOG = Logger.getInstance(ThemeResolver.class);

  // Order is important, we want project themes first.
  private final Set<String> myThemeNames = Sets.newHashSet();
  private final List<ThemeEditorStyle> myAllThemes;
  private final List<ThemeEditorStyle> myFrameworkThemes;
  private final List<ThemeEditorStyle> myProjectThemes;
  private final List<ThemeEditorStyle> myProjectLocalThemes;
  private final Configuration myConfiguration;

  public ThemeResolver(@NotNull Configuration configuration) {
    myConfiguration = configuration;

    final Queue<StyleResourceValue> localThemes = new LinkedList<StyleResourceValue>(getProjectThemesNoLibraries(configuration));
    // If there are no libraries, resolvedThemes will be the same as localThemes.
    final Queue<StyleResourceValue> resolvedThemes = new LinkedList<StyleResourceValue>(getProjectThemes(configuration));
    final Queue<StyleResourceValue> frameworkThemes = new LinkedList<StyleResourceValue>(getFrameworkThemes(configuration));
    myProjectLocalThemes = Lists.newArrayListWithCapacity(resolvedThemes.size());
    // We expect every local theme to have 1 parent.
    myProjectThemes = Lists.newArrayListWithExpectedSize(resolvedThemes.size() * 2);
    myFrameworkThemes = Lists.newArrayListWithCapacity(frameworkThemes.size());
    myAllThemes = Lists.newArrayListWithExpectedSize(resolvedThemes.size() * 2 + frameworkThemes.size());

    ResourceResolver resolver = configuration.getResourceResolver();
    if (resolver == null) {
      LOG.error("Unable to get ResourceResolver.");
      return;
    }

    /*
    Process all the available themes and their parents. We process them in the following order:
    - Local themes
    - Resolved themes (this include the parent themes that result from the checking the local themes and their parents)
    - Framework themes

    The order will ensure that we keep the attribute value that is lower in the hierarchy (first local, then any of the parents in order
    and lastly the framework themes).
     */
    Queue<StyleResourceValue> pendingThemes = new ForwardingQueue<StyleResourceValue>() {
      @Override
      protected Queue<StyleResourceValue> delegate() {
        if (!localThemes.isEmpty()) {
          return localThemes;
        }
        else if (!resolvedThemes.isEmpty()) {
          return resolvedThemes;
        }
        else {
          return frameworkThemes;
        }
      }
    };
    while (!pendingThemes.isEmpty()) {
      boolean isLocalTheme = !localThemes.isEmpty();
      boolean isProjectDependency = isLocalTheme || !resolvedThemes.isEmpty();
      StyleResourceValue style = pendingThemes.remove();
      String styleQualifiedName = ResolutionUtils.getQualifiedStyleName(style);

      if (myThemeNames.contains(styleQualifiedName)) {
        continue;
      }
      myThemeNames.add(styleQualifiedName);
      ThemeEditorStyle resolvedStyle = ResolutionUtils.getStyle(configuration, styleQualifiedName);

      if (resolvedStyle == null) {
        continue;
      }

      myAllThemes.add(resolvedStyle);
      if (isProjectDependency) {
        myProjectThemes.add(resolvedStyle);

        StyleResourceValue parent = resolver.getParent(style);
        if (parent != null) {
          resolvedThemes.add(parent);
        }
      }

      if (isLocalTheme) {
        myProjectLocalThemes.add(resolvedStyle);
      }
      else {
        myFrameworkThemes.add(resolvedStyle);
      }
    }
  }

  @NotNull
  private static List<StyleResourceValue> getFrameworkThemes(@NotNull Configuration myConfiguration) {
    ResourceRepository repository = myConfiguration.getFrameworkResources();
    if (repository == null) {
      return Collections.emptyList();
    }

    Map<ResourceType, Map<String, ResourceValue>> resources = repository.getConfiguredResources(myConfiguration.getFullConfig());
    return getThemes(myConfiguration, resources, true /*isFramework*/);
  }

  @NotNull
  private static List<StyleResourceValue> getProjectThemes(@NotNull Configuration myConfiguration) {
    LocalResourceRepository repository = AppResourceRepository.getAppResources(myConfiguration.getModule(), true);
    if (repository == null) {
      return Collections.emptyList();
    }

    Map<ResourceType, Map<String, ResourceValue>> resources = repository.getConfiguredResources(myConfiguration.getFullConfig());
    return getThemes(myConfiguration, resources, false /*isFramework*/);
  }

  @NotNull
  private static List<StyleResourceValue> getProjectThemesNoLibraries(@NotNull Configuration myConfiguration) {
    LocalResourceRepository repository = ProjectResourceRepository.getProjectResources(myConfiguration.getModule(), true);
    if (repository == null) {
      return Collections.emptyList();
    }

    Map<ResourceType, Map<String, ResourceValue>> resources = repository.getConfiguredResources(myConfiguration.getFullConfig());
    return getThemes(myConfiguration, resources, false /*isFramework*/);
  }

  @NotNull
  private static List<StyleResourceValue> getThemes(@NotNull Configuration configuration,
                                                    @NotNull Map<ResourceType, Map<String, ResourceValue>> resources,
                                                    boolean isFramework) {
    // get the styles.
    Map<String, ResourceValue> styles = resources.get(ResourceType.STYLE);

    // Collect the themes out of all the styles.
    Collection<ResourceValue> values = styles.values();
    List<StyleResourceValue> themes = new ArrayList<StyleResourceValue>(values.size());

    if (!isFramework) {
      // Try a little harder to see if the user has themes that don't have the normal naming convention
      ResourceResolver resolver = configuration.getResourceResolver();
      if (resolver != null) {
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

    // For the framework (and projects if resolver can't be computed) the computation is easier
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
   * Returns the list of themes declared by the project.
   */
  @NotNull
  public Collection<ThemeEditorStyle> getLocalThemes() {
    return Collections.unmodifiableList(myProjectLocalThemes);
  }

  /**
   * Returns the list of themes declared by the project and its dependencies.
   */
  @NotNull
  public Collection<ThemeEditorStyle> getProjectThemes() {
    return Collections.unmodifiableList(myProjectThemes);
  }

  /**
   * Returns the list of themes declared by the project and its dependencies.
   */
  @NotNull
  public Collection<ThemeEditorStyle> getFrameworkThemes() {
    return Collections.unmodifiableList(myFrameworkThemes);
  }

  @NotNull
  Collection<ThemeEditorStyle> getThemes() {
    return Collections.unmodifiableCollection(myAllThemes);
  }
}
