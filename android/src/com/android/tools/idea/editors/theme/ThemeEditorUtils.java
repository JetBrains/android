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

import com.android.SdkConstants;
import com.android.builder.model.SourceProvider;
import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.ResourceUrl;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.AndroidTextUtils;
import com.android.tools.idea.actions.OverrideResourceAction;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.configurations.ResourceResolverCache;
import com.android.tools.idea.configurations.ThemeSelectionPanel;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.javadoc.AndroidJavaDocRenderer;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.res.*;
import com.google.common.base.Predicate;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.JBColor;
import com.intellij.util.Processor;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Style;
import org.jetbrains.android.facet.AndroidFacet;
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.List;

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
    "/../adt/idea/android/lib/androidWidgets/theme-editor-widgets.jar",
    // IDEA plugin Development path
    "/community/android/android/lib/androidWidgets/theme-editor-widgets.jar"
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

    ResourceUrl url = ResourceUrl.parse(ResolutionUtils.getResourceUrlFromQualifiedName(ResolutionUtils.getQualifiedItemName(resValue), SdkConstants.TAG_ATTR));
    assert url != null;
    String tooltipContents = AndroidJavaDocRenderer.render(module, configuration, url);
    assert tooltipContents != null;
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

  public static boolean isThemeEditorSelected(@NotNull Project project) {
    for (FileEditor editor : FileEditorManager.getInstance(project).getSelectedEditors()) {
      if (editor instanceof ThemeEditor) {
        return true;
      }
    }
    return false;
  }

  public static void openThemeEditor(@NotNull final Project project) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ThemeEditorVirtualFile file = ThemeEditorVirtualFile.getThemeEditorFile(project);
        OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file);
        FileEditorManager.getInstance(project).openEditor(descriptor, true);
      }
    });
  }

  /**
   * Finds an ItemResourceValue for a given name in a theme inheritance tree
   */
  @Nullable("if there is not an item with that name")
  public static ItemResourceValue resolveItemFromParents(@NotNull final ConfiguredThemeEditorStyle theme,
                                                         @NotNull String name,
                                                         boolean isFrameworkAttr) {
    ConfiguredThemeEditorStyle currentTheme = theme;

    for (int i = 0; (i < ResourceResolver.MAX_RESOURCE_INDIRECTION) && currentTheme != null; i++) {
      ItemResourceValue item = currentTheme.getItem(name, isFrameworkAttr);
      if (item != null) {
        return item;
      }
      currentTheme = currentTheme.getParent();
    }
    return null;
  }

  @Nullable
  public static Object extractRealValue(@NotNull final EditedStyleItem item, @NotNull final Class<?> desiredClass) {
    String value = item.getValue();
    if (desiredClass == Boolean.class && ("true".equals(value) || "false".equals(value))) {
      return Boolean.valueOf(value);
    }
    if (desiredClass == Integer.class) {
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

  @NotNull
  private static ImmutableCollection<ConfiguredThemeEditorStyle> findThemes(@NotNull Collection<ConfiguredThemeEditorStyle> themes, final @NotNull Set<String> names) {
    return ImmutableSet.copyOf(Iterables.filter(themes, new Predicate<ConfiguredThemeEditorStyle>() {
      @Override
      public boolean apply(@Nullable ConfiguredThemeEditorStyle theme) {
        return theme != null && names.contains(theme.getName());
      }
    }));
  }

  @NotNull
  public static ImmutableList<Module> findAndroidModules(@NotNull Project project) {
    final ModuleManager manager = ModuleManager.getInstance(project);

    final ImmutableList.Builder<Module> builder = ImmutableList.builder();
    for (Module module : manager.getModules()) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        builder.add(module);
      }
    }

    return builder.build();
  }

  @NotNull
  public static ImmutableList<String> getDefaultThemeNames(@NotNull ThemeResolver themeResolver) {
    Collection<ConfiguredThemeEditorStyle> readOnlyLibThemes = themeResolver.getExternalLibraryThemes();

    Collection<ConfiguredThemeEditorStyle> foundThemes = new HashSet<ConfiguredThemeEditorStyle>();
    foundThemes.addAll(findThemes(readOnlyLibThemes, DEFAULT_THEMES));

    if (foundThemes.isEmpty()) {
      Collection<ConfiguredThemeEditorStyle> readOnlyFrameworkThemes = themeResolver.getFrameworkThemes();
      foundThemes = new HashSet<ConfiguredThemeEditorStyle>();
      foundThemes.addAll(findThemes(readOnlyFrameworkThemes, DEFAULT_THEMES_FALLBACK));

      if (foundThemes.isEmpty()) {
        foundThemes.addAll(readOnlyLibThemes);
        foundThemes.addAll(readOnlyFrameworkThemes);
      }
    }
    Set<String> temporarySet = Sets.newTreeSet(String.CASE_INSENSITIVE_ORDER);
    for (ConfiguredThemeEditorStyle theme : foundThemes) {
      temporarySet.add(theme.getQualifiedName());
    }
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
    for (String path : CUSTOM_WIDGETS_JAR_PATHS) {
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

  /**
   * Creates a new style
   * @param module the module where the new style is being created
   * @param newStyleName the new style name
   * @param parentStyleName the name of the new style parent
   * @param fileName name of the xml file where the style will be added (usually "styles.xml")
   * @param folderNames folder names where the style will be added
   * @return true if the style was created or false otherwise
   */
  public static boolean createNewStyle(@NotNull final Module module, final @NotNull String newStyleName, final @Nullable String parentStyleName, final @NotNull String fileName, final @NotNull List<String> folderNames) {
    return new WriteCommandAction<Boolean>(module.getProject(), "Create new style " + newStyleName) {
      @Override
      protected void run(@NotNull Result<Boolean> result) {
        CommandProcessor.getInstance().markCurrentCommandAsGlobal(module.getProject());
        result.setResult(AndroidResourceUtil.
          createValueResource(module, newStyleName, null,
                              ResourceType.STYLE, fileName, folderNames, new Processor<ResourceElement>() {
              @Override
              public boolean process(ResourceElement element) {
                assert element instanceof Style;
                final Style style = (Style)element;

                if (parentStyleName != null) {
                  style.getParentStyle().setStringValue(parentStyleName);
                }

                return true;
              }
            }));
      }
    }.execute().getResultObject();
  }

  /**
   * Creates a new style by displaying the dialog of the {@link NewStyleDialog}.
   * @param defaultParentStyle is used in NewStyleDialog, will be preselected in the parent text field and name will be suggested based on it
   * @param themeEditorContext  current theme editor context
   * @param isTheme whether theme or style will be created
   * @param message is used in NewStyleDialog to display message to user
   * @return the new style name or null if the style wasn't created
   */
  @Nullable
  public static String showCreateNewStyleDialog(@Nullable ConfiguredThemeEditorStyle defaultParentStyle,
                                                @NotNull final ThemeEditorContext themeEditorContext,
                                                boolean isTheme,
                                                boolean enableParentChoice,
                                                @Nullable final String message,
                                                @Nullable ThemeSelectionPanel.ThemeChangedListener themeChangedListener) {
    // if isTheme is true, defaultParentStyle shouldn't be null
    String defaultParentStyleName = null;
    if (isTheme && defaultParentStyle == null) {
      ImmutableList<String> defaultThemes = getDefaultThemeNames(themeEditorContext.getThemeResolver());
      defaultParentStyleName = !defaultThemes.isEmpty() ? defaultThemes.get(0) : null;
    }
    else if (defaultParentStyle != null) {
      defaultParentStyleName = defaultParentStyle.getQualifiedName();
    }

    final NewStyleDialog dialog = new NewStyleDialog(isTheme, themeEditorContext, defaultParentStyleName,
                                                     (defaultParentStyle == null) ? null : defaultParentStyle.getName(), message);
    dialog.enableParentChoice(enableParentChoice);
    if (themeChangedListener != null) {
      dialog.setThemeChangedListener(themeChangedListener);
    }

    boolean createStyle = dialog.showAndGet();
    if (!createStyle) {
      return null;
    }

    int minModuleApi = getMinApiLevel(themeEditorContext.getCurrentContextModule());
    int minAcceptableApi = ResolutionUtils.getOriginalApiLevel(ResolutionUtils.getStyleResourceUrl(dialog.getStyleParentName()), themeEditorContext.getProject());

    final String fileName = AndroidResourceUtil.getDefaultResourceFileName(ResourceType.STYLE);
    FolderConfiguration config = new FolderConfiguration();
    if (minModuleApi < minAcceptableApi) {
      VersionQualifier qualifier = new VersionQualifier(minAcceptableApi);
      config.setVersionQualifier(qualifier);
    }

    if (fileName == null) {
      LOG.error("Couldn't find a default filename for ResourceType.STYLE");
      return null;
    }

    final List<String> dirNames = Collections.singletonList(config.getFolderName(ResourceFolderType.VALUES));
    String parentStyleName = dialog.getStyleParentName();
    boolean isCreated = createNewStyle(
      themeEditorContext.getCurrentContextModule(), dialog.getStyleName(), parentStyleName, fileName, dirNames);

    return isCreated ? dialog.getStyleName() : null;
  }

  /**
   * Checks if the selected theme is AppCompat
   */
  public static boolean isSelectedAppCompatTheme(@NotNull ThemeEditorContext context) {
    ConfiguredThemeEditorStyle currentTheme = context.getCurrentTheme();
    return currentTheme != null && isAppCompatTheme(currentTheme);
  }

  /**
   * Checks if a theme is AppCompat
   */
  public static boolean isAppCompatTheme(@NotNull ConfiguredThemeEditorStyle configuredThemeEditorStyle) {
    ConfiguredThemeEditorStyle currentTheme = configuredThemeEditorStyle;
    for (int i = 0; (i < ResourceResolver.MAX_RESOURCE_INDIRECTION) && currentTheme != null; i++) {
      // for loop ensures that we don't run into cyclic theme inheritance.
      //TODO: This check is not enough. User themes could also start with "Theme.AppCompat" and not be AppCompat
      if (currentTheme.getName().startsWith("Theme.AppCompat") && currentTheme.getSourceModule() == null) {
        return true;
      }
      currentTheme = currentTheme.getParent();
    }
    return false;
  }

  /**
   * Copies a theme to a values folder with api version apiLevel,
   * potentially creating the necessary folder or file.
   * @param apiLevel api level of the folder the theme is copied to
   * @param toBeCopied theme to be copied
   */
  public static void copyTheme(int apiLevel, @NotNull final XmlTag toBeCopied) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    PsiFile file = toBeCopied.getContainingFile();
    assert file instanceof XmlFile : file;
    ResourceFolderType folderType = ResourceHelper.getFolderType(file);
    assert folderType != null : file;
    FolderConfiguration config = ResourceHelper.getFolderConfiguration(file);
    assert config != null : file;

    VersionQualifier qualifier = new VersionQualifier(apiLevel);
    config.setVersionQualifier(qualifier);
    String folder = config.getFolderName(folderType);

    if (folderType != ResourceFolderType.VALUES) {
      OverrideResourceAction.forkResourceFile((XmlFile)file, folder, false);
    }
    else {
      XmlTag tag = OverrideResourceAction.getValueTag(PsiTreeUtil.getParentOfType(toBeCopied, XmlTag.class, false));
      if (tag != null) {
        AndroidFacet facet = AndroidFacet.getInstance(toBeCopied);
        if (facet != null) {
          PsiDirectory dir = null;
          PsiDirectory resFolder = file.getParent();
          if (resFolder != null) {
            resFolder = resFolder.getParent();
          }
          if (resFolder != null) {
            dir = resFolder.findSubdirectory(folder);
            if (dir == null) {
              dir = resFolder.createSubdirectory(folder);
            }
          }
          OverrideResourceAction.forkResourceValue(toBeCopied.getProject(), tag, file, facet, dir, false);
        }
      }
    }
  }

  /**
   * Returns version qualifier of FolderConfiguration.
   * Returns -1, if FolderConfiguration has default version
   */
  public static int getVersionFromConfiguration(@NotNull FolderConfiguration configuration) {
    VersionQualifier qualifier = configuration.getVersionQualifier();
    return (qualifier != null) ? qualifier.getVersion() : -1;
  }

  /**
   * Returns the smallest api level of the folders in folderNames.
   * Returns Integer.MAX_VALUE if folderNames is empty.
   */
  public static int getMinFolderApi(@NotNull List<String> folderNames, @NotNull Module module) {
    int minFolderApi = Integer.MAX_VALUE;
    int minModuleApi = getMinApiLevel(module);
    for (String folderName : folderNames) {
      FolderConfiguration folderConfig = FolderConfiguration.getConfigForFolder(folderName);
      if (folderConfig != null) {
        VersionQualifier version = folderConfig.getVersionQualifier();
        int folderApi = version != null ? version.getVersion() : minModuleApi;
        minFolderApi = Math.min(minFolderApi, folderApi);
      }
    }
    return minFolderApi;
  }

  @NotNull
  public static Configuration getConfigurationForModule(@NotNull Module module) {
    Project project = module.getProject();
    final AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null : "moduleComboModel must contain only Android modules";

    ConfigurationManager configurationManager = facet.getConfigurationManager();

    // Using the project virtual file to set up configuration for the theme editor
    // That fact is hard-coded in computeBestDevice() method in Configuration.java
    // BEWARE if attempting to modify to use a different virtual file
    final VirtualFile projectFile = project.getProjectFile();
    assert projectFile != null;

    return configurationManager.getConfiguration(projectFile);
  }

  /**
   * Given a {@link SourceProvider}, it returns a list of all the available ResourceFolderRepositories
   */
  @NotNull
  public static List<ResourceFolderRepository> getResourceFolderRepositoriesFromSourceSet(@NotNull AndroidFacet facet,
                                                                                          @Nullable SourceProvider provider) {
    if (provider == null) {
      return Collections.emptyList();
    }

    Collection<File> resDirectories = provider.getResDirectories();

    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    List<ResourceFolderRepository> folders = Lists.newArrayListWithExpectedSize(resDirectories.size());
    for (File dir : resDirectories) {
      VirtualFile virtualFile = fileSystem.findFileByIoFile(dir);
      if (virtualFile != null) {
        folders.add(ResourceFolderRegistry.get(facet, virtualFile));
      }
    }

    return folders;
  }

  /**
   * Returns the color that should be used for the background of the preview panel depending on the background color
   * of the theme being displayed, so as to always keep some contrast between the two.
   */
  public static JBColor getGoodContrastPreviewBackground(@NotNull ConfiguredThemeEditorStyle theme, @NotNull ResourceResolver resourceResolver) {
    ItemResourceValue themeColorBackgroundItem = resolveItemFromParents(theme, "colorBackground", true);
    ResourceValue backgroundResourceValue = resourceResolver.resolveResValue(themeColorBackgroundItem);
    if (backgroundResourceValue != null) {
      String colorBackgroundValue = backgroundResourceValue.getValue();
      Color colorBackground = ResourceHelper.parseColor(colorBackgroundValue);
      if (colorBackground != null) {
        float backgroundDistance = MaterialColorUtils.colorDistance(colorBackground, ThemeEditorComponent.PREVIEW_BACKGROUND);
        if (backgroundDistance < ThemeEditorComponent.COLOR_DISTANCE_THRESHOLD &&
            backgroundDistance < MaterialColorUtils.colorDistance(colorBackground, ThemeEditorComponent.ALT_PREVIEW_BACKGROUND)) {
          return ThemeEditorComponent.ALT_PREVIEW_BACKGROUND;
        }
      }
    }

    return ThemeEditorComponent.PREVIEW_BACKGROUND;
  }

  /**
   * Interface to visit all the available {@link LocalResourceRepository}
   */
  public interface ResourceFolderVisitor {
    /**
     * @param resources a repository containing resources
     * @param moduleName the module name
     * @param variantName string that identifies the variant used to obtain the resources
     * @param isSelected true if the current passed repository is in an active source set
     */
    void visitResourceFolder(@NotNull LocalResourceRepository resources, String moduleName, @NotNull String variantName, boolean isSelected);
  }

  /**
   * Visits every ResourceFolderRepository. It visits every resource in order, meaning that the later calls may override resources from
   * previous ones.
   */
  public static void acceptResourceResolverVisitor(final @NotNull AndroidFacet mainFacet, final @NotNull ResourceFolderVisitor visitor) {
    // Get all the dependencies of the module in reverse order (first one is the lowest priority one)
    List<AndroidFacet> dependencies =  Lists.reverse(AndroidUtils.getAllAndroidDependencies(mainFacet.getModule(), true));

    // The order of iteration here is important since the resources from the mainFacet will override those in the dependencies.
    for (AndroidFacet dependency : Iterables.concat(dependencies, ImmutableList.of(mainFacet))) {
      AndroidGradleModel androidModel = AndroidGradleModel.get(dependency);
      if (androidModel == null) {
        // For non gradle module, get the main source provider
        SourceProvider provider = dependency.getMainSourceProvider();
        for (LocalResourceRepository resourceRepository : getResourceFolderRepositoriesFromSourceSet(dependency, provider)) {
          visitor.visitResourceFolder(resourceRepository, dependency.getName(), provider.getName(), true);
        }
      } else {
        // For gradle modules, get all source providers and go through them
        // We need to iterate the providers in the returned to make sure that they correctly override each other
        List<SourceProvider> activeProviders = androidModel.getActiveSourceProviders();
        for (SourceProvider provider : activeProviders) {
          for (LocalResourceRepository resourceRepository : getResourceFolderRepositoriesFromSourceSet(dependency, provider)) {
            visitor.visitResourceFolder(resourceRepository, dependency.getName(), provider.getName(), true);
          }
        }

        // Not go through all the providers that are not in the activeProviders
        ImmutableSet<SourceProvider> selectedProviders = ImmutableSet.copyOf(activeProviders);
        for (SourceProvider provider : androidModel.getAllSourceProviders()) {
          if (!selectedProviders.contains(provider)) {
            for (LocalResourceRepository resourceRepository : getResourceFolderRepositoriesFromSourceSet(dependency, provider)) {
              visitor.visitResourceFolder(resourceRepository, dependency.getName(), provider.getName(), false);
            }
          }
        }
      }
    }
  }

  /**
   * Returns the list of the qualified names of all the user-defined themes available from a given module
   */
  @NotNull
  public static ImmutableList<String> getModuleThemeQualifiedNamesList(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;
    ConfigurationManager manager = facet.getConfigurationManager();
    // We create a new ResourceResolverCache instead of using cache from myConfiguration to optimize memory instead of time/speed,
    // because we are about to create a lot of instances of ResourceResolver here that won't be used outside of this method
    final ResourceResolverCache resolverCache = new ResourceResolverCache(manager);
    final IAndroidTarget target = manager.getTarget();
    final Map<ResourceValue, Boolean> cache = new HashMap<ResourceValue, Boolean>();
    final Set<String> themeNamesSet = Sets.newTreeSet(String.CASE_INSENSITIVE_ORDER);

    ResourceFolderVisitor visitor = new ResourceFolderVisitor() {
      @Override
      public void visitResourceFolder(@NotNull LocalResourceRepository resources,
                                      String moduleName,
                                      @NotNull String variantName,
                                      boolean isSelected) {
        if (!isSelected) {
          return;
        }
        for (String simpleThemeName : resources.getItemsOfType(ResourceType.STYLE)) {
          String themeStyleResourceUrl = SdkConstants.STYLE_RESOURCE_PREFIX + simpleThemeName;
          List<ResourceItem> themeItems = resources.getResourceItem(ResourceType.STYLE, simpleThemeName);
          assert themeItems != null;
          for (ResourceItem themeItem : themeItems) {
            ResourceResolver resolver = resolverCache.getResourceResolver(target, themeStyleResourceUrl, themeItem.getConfiguration());
            ResourceValue themeItemResourceValue = themeItem.getResourceValue(false);
            assert themeItemResourceValue != null;
            if (resolver.isTheme(themeItemResourceValue, cache)) {
              themeNamesSet.add(simpleThemeName);
              break;
            }
          }
        }
      }
    };

    acceptResourceResolverVisitor(facet, visitor);

    return ImmutableList.copyOf(themeNamesSet);
  }

  @NotNull
  public static ChooseResourceDialog getResourceDialog(@NotNull EditedStyleItem item,
                                                       @NotNull ThemeEditorContext context,
                                                       ResourceType[] allowedTypes) {
    Module module = context.getModuleForResources();
    ItemResourceValue itemSelectedValue = item.getSelectedValue();

    String value = itemSelectedValue.getValue();
    boolean isFrameworkValue = itemSelectedValue.isFramework();

    String nameSuggestion = value;
    ResourceUrl url = ResourceUrl.parse(value, isFrameworkValue);
    if (url != null) {
      nameSuggestion = url.name;
    }
    nameSuggestion = getDefaultResourceName(context, nameSuggestion);

    ChooseResourceDialog.ResourceNameVisibility resourceNameVisibility = ChooseResourceDialog.ResourceNameVisibility.FORCE;
    if (nameSuggestion.startsWith("#")) {
      nameSuggestion = null;
      resourceNameVisibility = ChooseResourceDialog.ResourceNameVisibility.SHOW;
    }

    ChooseResourceDialog dialog = new ChooseResourceDialog(module, allowedTypes, value, isFrameworkValue, resourceNameVisibility, nameSuggestion);
    dialog.setUseGlobalUndo(true);

    return dialog;
  }

  /**
   * Build a name for a new resource based on a provided name.
   * @param initialName a name that result should be based on (that might not be vacant)
   */
  @NotNull
  private static String getDefaultResourceName(@NotNull ThemeEditorContext context, final @NotNull String initialName) {
    if (context.getCurrentTheme() == null || !context.getCurrentTheme().isReadOnly()) {
      // If the currently selected theme is not read-only, then the expected
      // behaviour of color picker would be to edit the existing resource.
      return initialName;
    }

    final ResourceResolver resolver = context.getResourceResolver();
    assert resolver != null;
    final ResourceValue value = resolver.findResValue(SdkConstants.COLOR_RESOURCE_PREFIX + initialName, false);

    // Value doesn't exist, safe to use initial guess
    if (value == null) {
      return initialName;
    }

    // Given value exist, need to add a suffix to initialName to make it unique
    for (int i = 1; i <= 50; ++i) {
      final String name = initialName + "_" + i;

      if (resolver.findResValue(SdkConstants.COLOR_RESOURCE_PREFIX + name, false) == null) {
        // Found a vacant name
        return name;
      }
    }

    // Made 50 iterations and still no luck finding a vacant name
    // Just set a default name to empty string so user have to insert the name manually
    return "";
  }

  /**
   * Returns a more user-friendly name of a given theme.
   * Aimed at framework themes with names of the form Theme.*.Light.*
   * or Theme.*.*
   */
  @NotNull
  public static String simplifyThemeName(@NotNull ConfiguredThemeEditorStyle theme) {
    String result;
    String name = theme.getQualifiedName();
    String[] pieces = name.split("\\.");
    if (pieces.length > 1 && !"Light".equals(pieces[1])) {
      result = pieces[1];
    }
    else {
      result = "Theme";
    }
    ConfiguredThemeEditorStyle parent = theme;
    while (parent != null) {
      if ("Theme.Light".equals(parent.getName())) {
        return result + " Light";
      }
      else {
        parent = parent.getParent();
      }
    }
    return result + " Dark";
  }

  /**
   * Returns a string with the words concatenated into an enumeration w1, w2, ..., w(n-1) and wn
   */
  @NotNull
  public static String generateWordEnumeration(@NotNull Collection<String> words) {
    return AndroidTextUtils.generateCommaSeparatedList(words, "and");
  }

  @NotNull
  public static Font scaleFontForAttribute(@NotNull Font font) {
    // Use Math.ceil to ensure that the result is a font with an integer point size
    return font.deriveFont((float)Math.ceil(font.getSize() * ThemeEditorConstants.ATTRIBUTES_FONT_SCALE));
  }

  public static void setInheritsPopupMenuRecursive(JComponent comp) {
    comp.setInheritsPopupMenu(true);
    for (Component child : comp.getComponents()) {
      if (child instanceof JComponent) {
        setInheritsPopupMenuRecursive((JComponent) child);
      }
    }
  }
}
