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

import static com.android.ide.common.resources.ResourceResolver.MAX_RESOURCE_INDIRECTION;

import com.android.SdkConstants;
import com.android.builder.model.SourceProvider;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.AndroidTextUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.configurations.ResourceResolverCache;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceFolderRegistry;
import com.android.tools.idea.res.ResourceFolderRepository;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import java.awt.Component;
import java.awt.Font;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JComponent;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for static methods which are used in different classes of theme editor
 */
public class ThemeEditorUtils {
  private ThemeEditorUtils() {
  }

  /**
   * Finds an StyleItemResourceValue for a given name in a theme inheritance tree
   */
  @Nullable/*if there is not an item with that name*/
  public static StyleItemResourceValue resolveItemFromParents(@NotNull ConfiguredThemeEditorStyle theme,
                                                              @NotNull String name,
                                                              boolean isFrameworkAttr) {
    ConfiguredThemeEditorStyle currentTheme = theme;

    for (int i = 0; (i < MAX_RESOURCE_INDIRECTION) && currentTheme != null; i++) {
      StyleItemResourceValue item = currentTheme.getItem(name, isFrameworkAttr);
      if (item != null) {
        return item;
      }
      currentTheme = currentTheme.getParent();
    }
    return null;
  }

  /**
   * Returns version qualifier of FolderConfiguration.
   * Returns -1, if FolderConfiguration has default version
   */
  public static int getVersionFromConfiguration(@NotNull FolderConfiguration configuration) {
    VersionQualifier qualifier = configuration.getVersionQualifier();
    return (qualifier != null) ? qualifier.getVersion() : -1;
  }

  static int getMinApiLevel(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return 1;
    }
    AndroidModuleInfo moduleInfo = AndroidModuleInfo.getInstance(facet);
    return moduleInfo.getMinSdkVersion().getApiLevel();
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

    ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(module);

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
        folders.add(ResourceFolderRegistry.getInstance(facet.getModule().getProject()).get(facet, virtualFile));
      }
    }

    return folders;
  }

  @NotNull
  public static RenderTask configureRenderTask(@NotNull final Module module, @NotNull final Configuration configuration) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;
    final RenderService service = RenderService.getInstance(module.getProject());
    RenderLogger logger = new RenderLogger("ThemeEditorLogger", null);
    RenderTask task = service.taskBuilder(facet, configuration)
      .withLogger(logger)
      .buildSynchronously();
    assert task != null;
    task.getLayoutlibCallback().setLogger(logger);
    return task;
  }

  /**
   * Interface to visit all the available {@link LocalResourceRepository}
   */
  public interface ResourceFolderVisitor {
    /**
     * @param resources   a repository containing resources
     * @param moduleName  the module name
     * @param variantName string that identifies the variant used to obtain the resources
     * @param isSelected  true if the current passed repository is in an active source set
     */
    void visitResourceFolder(@NotNull LocalResourceRepository resources,
                             String moduleName,
                             @NotNull String variantName,
                             boolean isSelected);
  }

  /**
   * Visits every ResourceFolderRepository. It visits every resource in order, meaning that the later calls may override resources from
   * previous ones.
   */
  public static void acceptResourceResolverVisitor(final @NotNull AndroidFacet mainFacet, final @NotNull ResourceFolderVisitor visitor) {
    // Get all the dependencies of the module in reverse order (first one is the lowest priority one)
    List<AndroidFacet> dependencies = Lists.reverse(AndroidUtils.getAllAndroidDependencies(mainFacet.getModule(), true));

    // The order of iteration here is important since the resources from the mainFacet will override those in the dependencies.
    for (AndroidFacet dependency : Iterables.concat(dependencies, ImmutableList.of(mainFacet))) {
      AndroidModuleModel androidModel = AndroidModuleModel.get(dependency);
      if (androidModel == null) {
        // For non gradle module, get the main source provider
        SourceProvider provider = dependency.getMainSourceProvider();
        for (LocalResourceRepository resourceRepository : getResourceFolderRepositoriesFromSourceSet(dependency, provider)) {
          visitor.visitResourceFolder(resourceRepository, dependency.getName(), provider.getName(), true);
        }
      }
      else {
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
        setInheritsPopupMenuRecursive((JComponent)child);
      }
    }
  }
}
