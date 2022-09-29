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
package com.android.tools.idea.gradle.dsl.model;

import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.FILE_CONSTRUCTOR_NAME;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.FILE_METHOD_NAME;
import static com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.ASSIGNMENT;
import static com.android.tools.idea.gradle.dsl.parser.include.IncludeDslElement.INCLUDE;
import static com.android.tools.idea.gradle.dsl.parser.settings.ProjectPropertiesDslElement.BUILD_FILE_NAME;
import static com.android.tools.idea.gradle.dsl.parser.settings.ProjectPropertiesDslElement.PROJECT_DIR;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.api.settings.DependencyResolutionManagementModel;
import com.android.tools.idea.gradle.dsl.api.settings.PluginManagementModel;
import com.android.tools.idea.gradle.dsl.api.settings.PluginsBlockModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.model.settings.DependencyResolutionManagementModelImpl;
import com.android.tools.idea.gradle.dsl.model.settings.PluginManagementModelImpl;
import com.android.tools.idea.gradle.dsl.model.settings.PluginsBlockModelImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile;
import com.android.tools.idea.gradle.dsl.parser.include.IncludeDslElement;
import com.android.tools.idea.gradle.dsl.parser.plugins.PluginsDslElement;
import com.android.tools.idea.gradle.dsl.parser.settings.DependencyResolutionManagementDslElement;
import com.android.tools.idea.gradle.dsl.parser.settings.PluginManagementDslElement;
import com.android.tools.idea.gradle.dsl.parser.settings.ProjectPropertiesDslElement;
import com.android.tools.idea.gradle.dsl.utils.BuildScriptUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleSettingsModelImpl extends GradleFileModelImpl implements GradleSettingsModel {
  @NotNull protected GradleSettingsFile myGradleSettingsFile;

  public GradleSettingsModelImpl(@NotNull GradleSettingsFile parsedModel) {
    super(parsedModel);
    myGradleSettingsFile = parsedModel;
  }

  private static class ModulePathsCache {
    long committedCount = -1;
    @NotNull LinkedHashSet<String> paths;

    ModulePathsCache() {
      paths = new LinkedHashSet<>();
      paths.add(":");
    }
  }

  private final ModulePathsCache myModulePathsCache = new ModulePathsCache();

  /**
   * Returns the module paths specified by the include statements. Note that these path are not file paths, but instead specify the
   * location of the modules in the project hierarchy. As such, the paths use the ':' character as separator.
   *
   * <p>For example, the path a:b or :a:b represents the module in the directory $projectDir/a/b.
   */
  @NotNull
  @Override
  public Set<String> modulePaths() {
    long committedCount = myGradleDslFile.getLastCommittedModificationCount();
    long modificationCount = myGradleDslFile.getModificationCount();
    IncludeDslElement includePaths = myGradleDslFile.getPropertyElement(INCLUDE);

    // if we the committedCount in our cache is equal to the current modification count, we must be unmodified since a previous
    // already-committed count.  Since counts increase monotonically, and modificationCount >= lastCommittedCount, if modificationCount is
    // equal to our cached committedCount the GradleDslFile must be unchanged since the last cache save, so our cached result is valid.
    synchronized(myModulePathsCache) {
      if (myModulePathsCache.committedCount == modificationCount) {
        return myModulePathsCache.paths;
      }
    }

    LinkedHashSet<String> result = new LinkedHashSet<>();
    result.add(":"); // Indicates the root module.

    if (includePaths != null) {
      for (GradleDslSimpleExpression includePath : includePaths.getModules()) {
        String value = includePath.getValue(String.class);
        if (value != null) {
          result.add(standardiseModulePath(value));
        }
      }
    }

    // update the cache.  (It does not matter if the GradleDslFile was initially modified, or has been modified since; any difference
    // in either counter from thie initial committedCount will simply render this cache entry invalid.)
    synchronized(myModulePathsCache) {
      myModulePathsCache.paths = result;
      myModulePathsCache.committedCount = committedCount;
    }

    return result;
  }

  @Override
  public void addModulePath(@NotNull String modulePath) {
    modulePath = standardiseModulePath(modulePath);
    IncludeDslElement includeDslElement = myGradleDslFile.ensurePropertyElement(INCLUDE);
    GradleDslLiteral literal = new GradleDslLiteral(includeDslElement, GradleNameElement.create(INCLUDE.name));
    literal.setValue(modulePath);
    includeDslElement.setNewElement(literal);
  }

  @Override
  public void removeModulePath(@NotNull String modulePath) {
    IncludeDslElement includeDslElement = myGradleDslFile.getPropertyElement(INCLUDE);
    if (includeDslElement != null) {
      // Try to remove the module path whether it has ":" prefix or not.
      if (!modulePath.startsWith(":")) {
        includeDslElement.removeModule(":" + modulePath);
      }
      includeDslElement.removeModule(modulePath);
    }
  }

  @Override
  public void replaceModulePath(@NotNull String oldModulePath, @NotNull String newModulePath) {
    IncludeDslElement includeDslElement = myGradleDslFile.getPropertyElement(INCLUDE);
    if (includeDslElement != null) {
      // Try to replace the module path whether it has ":" prefix or not.
      if (!newModulePath.startsWith(":")) {
        newModulePath = ":" + newModulePath;
      }
      if (!oldModulePath.startsWith(":")) {
        includeDslElement.replaceModulePath(":" + oldModulePath, newModulePath);
      }
      includeDslElement.replaceModulePath(oldModulePath, newModulePath);
    }
  }

  @Nullable
  @Override
  public File moduleDirectory(String modulePath) {
    modulePath = standardiseModulePath(modulePath);
    if (!modulePaths().contains(modulePath)) {
      return null;
    }
    return moduleDirectoryNoCheck(modulePath);
  }

  /**
   * WARNING: This method does not write in the same format as it is read, this means that the changes from this method
   * WON'T be visible until the file as been re-parsed.
   *
   * For example:
   *   gradleSettingsModel.setModuleDirectory(":app", new File("/cool/file"))
   *   File moduleDir = gradleSettingModel.moduleDirectory(":app") // returns projectDir/app not /cool/file
   *
   * TODO: FIX THIS
   */
  @Override
  public void setModuleDirectory(@NotNull String modulePath, @NotNull File moduleDir) {
    String projectKey = "project('" + modulePath + "')";
    String projectDirPropertyName = projectKey + "." + PROJECT_DIR;
    // If the property already exists on file then delete it and then re-create with a new value.
    ProjectPropertiesDslElement projectProperties = myGradleDslFile.getPropertyElement(projectKey, ProjectPropertiesDslElement.class);
    if (projectProperties != null) {
      projectProperties.removeProperty(PROJECT_DIR);
    }

    // If the property has already been set by this method, remove it and recreate it.
    myGradleDslFile.removeProperty(projectDirPropertyName);

    // Create the GradleDslMethodCall that represents that method.
    GradleNameElement gradleNameElement = GradleNameElement.fake(projectDirPropertyName);
    GradleDslMethodCall methodCall = new GradleDslMethodCall(myGradleDslFile, gradleNameElement, FILE_METHOD_NAME);
    methodCall.setExternalSyntax(ASSIGNMENT);
    myGradleDslFile.setNewElement(methodCall);

    // Make the method call new File(rootDir, <PATH>) if possible.
    String dirPath = moduleDir.getAbsolutePath();
    File rootDir = virtualToIoFile(myGradleDslFile.getFile().getParent());
    if (VfsUtilCore.isAncestor(rootDir, moduleDir, false)) {
      GradleDslLiteral rootDirArg = new GradleDslLiteral(methodCall, GradleNameElement.empty());
      GradlePropertyModel elementModel = GradlePropertyModelBuilder.create(rootDirArg).build();
      rootDirArg.setValue(ReferenceTo.createReferenceFromText("rootDir", elementModel));
      methodCall.addNewArgument(rootDirArg);
      methodCall.setMethodName(FILE_CONSTRUCTOR_NAME);
      methodCall.setIsConstructor(true);
      dirPath = rootDir.toURI().relativize(moduleDir.toURI()).getPath();
    }

    if (dirPath != null && !dirPath.isEmpty()) {
      GradleDslLiteral extraArg = new GradleDslLiteral(methodCall, GradleNameElement.empty());
      extraArg.setValue(dirPath);
      methodCall.addNewArgument(extraArg);
    }
  }

  @Nullable
  private File moduleDirectoryNoCheck(String modulePath) {
    File rootDirPath = virtualToIoFile(myGradleDslFile.getFile().getParent());
    if (modulePath.equals(":")) {
      return rootDirPath;
    }

    String projectKey = "project('" + modulePath + "')";
    ProjectPropertiesDslElement projectProperties = myGradleDslFile.getPropertyElement(projectKey, ProjectPropertiesDslElement.class);
    if (projectProperties != null) {
      File projectDir = projectProperties.projectDir();
      if (projectDir != null) {
        return projectDir;
      }
    }

    File parentDir;
    if (modulePath.lastIndexOf(':') == 0) {
      parentDir = rootDirPath;
    }
    else {
      String parentModule = parentModuleNoCheck(modulePath);
      if (parentModule == null) {
        return null;
      }
      parentDir = moduleDirectoryNoCheck(parentModule);
    }
    String moduleName = modulePath.substring(modulePath.lastIndexOf(':') + 1);
    return new File(parentDir, moduleName);
  }

  @Nullable
  @Override
  public String moduleWithDirectory(@NotNull File moduleDir) {
    for (String modulePath : modulePaths()) {
      if (filesEqual(moduleDir, moduleDirectoryNoCheck(modulePath))) {
        return modulePath;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public GradleBuildModel moduleModel(@NotNull String modulePath) {
    File buildFilePath = buildFile(modulePath);
    if (buildFilePath == null) {
      return null;
    }
    VirtualFile buildFile = findFileByIoFile(buildFilePath, false);
    if (buildFile == null) {
      return null;
    }
    GradleBuildFile dslFile =
      myGradleDslFile.getContext().getOrCreateBuildFile(buildFile, modulePath.substring(modulePath.lastIndexOf(':') + 1), false);
    return new GradleBuildModelImpl(dslFile);
  }

  @Nullable
  @Override
  public String parentModule(@NotNull String modulePath) {
    modulePath = standardiseModulePath(modulePath);
    Collection<String> allModulePaths = modulePaths();
    if (!allModulePaths.contains(modulePath)) {
      return null;
    }
    String currentPath = modulePath;
    do {
      currentPath = parentModuleNoCheck(currentPath);
      if (allModulePaths.contains(currentPath)) {
        return currentPath;
      }
    }
    while (currentPath != null && !currentPath.equals(":"));
    return null;
  }

  @Nullable
  private static String parentModuleNoCheck(@NotNull String modulePath) {
    modulePath = standardiseModulePath(modulePath);
    if (modulePath.equals(":")) {
      return null;
    }
    int lastPathElementIndex = modulePath.lastIndexOf(':');
    return lastPathElementIndex == 0 ? ":" : modulePath.substring(0, lastPathElementIndex);
  }

  @Nullable
  @Override
  public GradleBuildModel getParentModuleModel(@NotNull String modulePath) {
    String parentModule = parentModule(modulePath);
    if (parentModule == null) {
      return null;
    }
    return moduleModel(parentModule);
  }

  @Nullable
  @Override
  public File buildFile(@NotNull String modulePath) {
    File moduleDirectory = moduleDirectory(modulePath);
    if (moduleDirectory == null) {
      return null;
    }

    String buildFileName = null;
    String projectKey = "project('" + modulePath + "')";
    ProjectPropertiesDslElement projectProperties = myGradleDslFile.getPropertyElement(projectKey, ProjectPropertiesDslElement.class);
    if (projectProperties != null) {
      buildFileName =  projectProperties.getLiteral(BUILD_FILE_NAME, String.class);
    }

    // If the BUILD_FILE_NAME property doesn't exist, look for the default build file in the module.
    if (buildFileName == null) {
      return BuildScriptUtil.findGradleBuildFile(moduleDirectory);
    }

    return new File(moduleDirectory, buildFileName);
  }

  private static String standardiseModulePath(@NotNull String modulePath) {
    return modulePath.startsWith(":") ? modulePath : ":" + modulePath;
  }

  @Override
  public @NotNull DependencyResolutionManagementModel dependencyResolutionManagement() {
    DependencyResolutionManagementDslElement dependencyResolutionManagementElement =
      myGradleDslFile.ensurePropertyElement(DependencyResolutionManagementDslElement.DEPENDENCY_RESOLUTION_MANAGEMENT);
    return new DependencyResolutionManagementModelImpl(dependencyResolutionManagementElement);
  }

  @Override
  public @NotNull PluginManagementModel pluginManagement() {
    PluginManagementDslElement pluginManagementDslElement =
      myGradleDslFile.ensurePropertyElementAt(PluginManagementDslElement.PLUGIN_MANAGEMENT_DSL_ELEMENT, 0);
    return new PluginManagementModelImpl(pluginManagementDslElement);
  }

  @Override
  public @NotNull PluginsBlockModel plugins() {
    PluginManagementDslElement pluginManagementDslElement =
      myGradleDslFile.getPropertyElement(PluginManagementDslElement.PLUGIN_MANAGEMENT_DSL_ELEMENT);
    // pluginManagement must come first, but plugins must be immediately after if so.
    Integer at = pluginManagementDslElement == null ? 0 : 1;
    PluginsDslElement pluginsDslElement = myGradleDslFile.ensurePropertyElementAt(PluginsDslElement.PLUGINS, at);
    return new PluginsBlockModelImpl(pluginsDslElement);
  }
}
