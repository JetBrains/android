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
import com.android.builder.model.AndroidProject;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProvider;
import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.actions.OverrideResourceAction;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.javadoc.AndroidJavaDocRenderer;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.ResourceFolderRegistry;
import com.android.tools.idea.rendering.ResourceFolderRepository;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.lint.checks.ApiLookup;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Style;
import org.jetbrains.android.dom.resources.StyleItem;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.android.inspections.lint.IntellijLintClient;
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
        return o1.getQualifiedName().compareTo(o2.getQualifiedName());
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
      for (final EditedStyleItem value : currentStyle.getValues()) {
        if (!namesSet.contains(value.getName())) {
          allValues.add(value);
          namesSet.add(value.getName());
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
  private static Collection<ThemeEditorStyle> findThemes(@NotNull Collection<ThemeEditorStyle> themes, final @NotNull Set<String> names) {
    return ImmutableSet.copyOf(Iterables.filter(themes, new Predicate<ThemeEditorStyle>() {
      @Override
      public boolean apply(@Nullable ThemeEditorStyle theme) {
        return theme != null && names.contains(theme.getName());
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
   * Creates a new style by displaying the dialog of the {@link NewStyleDialog}.
   * @param parentStyle is used in NewStyleDialog, will be preselected in the parent text field and name will be suggested based on it.
   * @param newAttributeName, if it is not null, a new attribute will be added to the style with the value specified in newAttributeValue.
   * @param isTheme whether theme or style will be created
   * @param message is used in NewStyleDialog to display message to user
   * @return the new style name or null if the style wasn't created.
   */
  @Nullable
  public static String createNewStyle(@Nullable ThemeEditorStyle parentStyle,
                                      @Nullable final String newAttributeName,
                                      @Nullable final String newAttributeValue,
                                      @NotNull final ThemeEditorContext myThemeEditorContext,
                                      boolean isTheme,
                                      @Nullable final String message) {
    // if isTheme is true, parentStyle shouldn't be null
    assert !isTheme || parentStyle != null;

    final NewStyleDialog dialog = new NewStyleDialog(isTheme, myThemeEditorContext, (parentStyle == null) ? null : parentStyle.getQualifiedName(),
                         (parentStyle == null) ? null : parentStyle.getName(), message);

    boolean createStyle = dialog.showAndGet();
    if (!createStyle) {
      return null;
    }

    int minModuleApi = getMinApiLevel(myThemeEditorContext.getCurrentThemeModule());
    int themeParentApiLevel = getOriginalApiLevel(dialog.getStyleParentName(), myThemeEditorContext.getProject());
    int newAttributeApiLevel = getOriginalApiLevel(newAttributeName, myThemeEditorContext.getProject());
    int newValueApiLevel = getOriginalApiLevel(newAttributeValue, myThemeEditorContext.getProject());
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
        CommandProcessor.getInstance().markCurrentCommandAsGlobal(myThemeEditorContext.getProject());
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

    return isCreated ? SdkConstants.STYLE_RESOURCE_PREFIX + dialog.getStyleName() : null;
  }

  /**
   * Returns the Api level at which was defined the attribute or value with the name passed as argument.
   * Returns -1 if the name argument is null or not the name of a framework attribute or resource.
   */
  public static int getOriginalApiLevel(@Nullable String name, @NotNull Project project) {
    if (name == null) {
      return -1;
    }
    boolean isAttribute;
    if (name.startsWith(SdkConstants.ANDROID_NS_NAME_PREFIX)) {
      isAttribute = true;
    }
    else if (name.startsWith(SdkConstants.ANDROID_PREFIX)) {
      isAttribute = false;
    }
    else {
      // Not a framework attribute or resource
      return -1;
    }

    ApiLookup apiLookup = IntellijLintClient.getApiLookup(project);
    assert apiLookup != null;

    if (isAttribute) {
      return apiLookup.getFieldVersion("android/R$attr", name.substring(SdkConstants.ANDROID_NS_NAME_PREFIX_LEN));
    }

    String[] namePieces = name.substring(SdkConstants.ANDROID_PREFIX.length()).split("/");
    if (namePieces.length == 2) {
      // If dealing with a value, it should be of the form "type/value"
      return apiLookup.getFieldVersion("android/R$" + namePieces[0], AndroidResourceUtil.getFieldNameByResourceName(namePieces[1]));
    }
    return -1;
  }

  /**
   * Copies a theme to a values folder with api version apiLevel,
   * potentially creating the necessary folder or file.
   * @param apiLevel api level of the folder the theme is copied to
   * @param toBeCopied theme to be copied
   */
  public static void copyTheme(int apiLevel, @NotNull final XmlTag toBeCopied) {
    PsiFile file = toBeCopied.getContainingFile();
    assert file instanceof XmlFile : file;
    ResourceFolderType folderType = ResourceHelper.getFolderType(file);
    assert folderType != null : file;
    FolderConfiguration config = ResourceHelper.getFolderConfiguration(file);
    assert config != null : file;

    VersionQualifier qualifier = new VersionQualifier(apiLevel);
    config.setVersionQualifier(qualifier);
    String folder = config.getFolderName(folderType);
    final AndroidLintQuickFix action = OverrideResourceAction.createFix(folder);
    // Context needed for calls on action, but has no effect, simply has to be non null
    final AndroidQuickfixContexts.DesignerContext context = AndroidQuickfixContexts.DesignerContext.getInstance();

    // Copies the theme to the new file
    action.apply(toBeCopied, toBeCopied, context);
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
   * Interface to visit all the available {@link LocalResourceRepository}
   */
  public interface ResourceFolderVisitor {
    /**
     * @param resources a repository containing resources
     * @param variantName string that identifies the variant used to obtain the resources
     * @param isSelected true if the current passed repository is in an active source set
     */
    void visitResourceFolder(@NotNull LocalResourceRepository resources, @NotNull String variantName, boolean isSelected);
  }

  /**
   * Visits every ResourceFolderRepository
   */
  public static void acceptResourceResolverVisitor(@NotNull AndroidFacet facet, @NotNull ResourceFolderVisitor visitor) {
    // Set of the SourceProviders for the current selected configuration
    Set<SourceProvider> selectedProviders = Sets.newHashSet();
    // Set of the SourceProviders that are not active in the current selected configuration
    Set<SourceProvider> inactiveProviders = Sets.newHashSet();
    selectedProviders.add(facet.getMainSourceProvider());

    IdeaAndroidProject ideaAndroidProject = facet.getIdeaAndroidProject();
    if (ideaAndroidProject != null) {
      assert facet.isGradleProject();

      selectedProviders.add(facet.getBuildTypeSourceProvider());
      selectedProviders.add(facet.getMultiFlavorSourceProvider());

      // Add inactive SourceSets
      AndroidProject project = ideaAndroidProject.getDelegate();
      for (BuildTypeContainer buildType : project.getBuildTypes()) {
        inactiveProviders.add(buildType.getSourceProvider());
      }

      for (ProductFlavorContainer productFlavor : project.getProductFlavors()) {
        inactiveProviders.add(productFlavor.getSourceProvider());
      }
    }

    for (SourceProvider provider : selectedProviders) {
      for (ResourceFolderRepository resourceRepository : getResourceFolderRepositoriesFromSourceSet(facet, provider)) {
        visitor.visitResourceFolder(resourceRepository, provider.getName(), true);
      }
    }

    for (SourceProvider provider : inactiveProviders) {
      for (ResourceFolderRepository resourceRepository : getResourceFolderRepositoriesFromSourceSet(facet, provider)) {
        visitor.visitResourceFolder(resourceRepository, provider.getName(), false);
      }
    }
  }
}
