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

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.BOOLEAN_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.BOOLEAN;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.NONE;
import static com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement.ANDROID;
import static com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement.APPLY_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement.BUILDSCRIPT;
import static com.android.tools.idea.gradle.dsl.parser.configurations.ConfigurationsDslElement.CONFIGURATIONS;
import static com.android.tools.idea.gradle.dsl.parser.crashlytics.CrashlyticsDslElement.CRASHLYTICS;
import static com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement.DEPENDENCIES;
import static com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement.EXT;
import static com.android.tools.idea.gradle.dsl.parser.java.JavaDslElement.JAVA;
import static com.android.tools.idea.gradle.dsl.parser.plugins.PluginsDslElement.PLUGINS;
import static com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement.REPOSITORIES;

import com.android.tools.idea.gradle.dsl.api.BuildModelNotification;
import com.android.tools.idea.gradle.dsl.api.BuildScriptModel;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleFileModel;
import com.android.tools.idea.gradle.dsl.api.GradlePropertiesModel;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.PluginModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.configurations.ConfigurationsModel;
import com.android.tools.idea.gradle.dsl.api.crashlytics.CrashlyticsModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.java.JavaModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl;
import com.android.tools.idea.gradle.dsl.model.build.BuildScriptModelImpl;
import com.android.tools.idea.gradle.dsl.model.configurations.ConfigurationsModelImpl;
import com.android.tools.idea.gradle.dsl.model.crashlytics.CrashlyticsModelImpl;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModelImpl;
import com.android.tools.idea.gradle.dsl.model.ext.ExtModelImpl;
import com.android.tools.idea.gradle.dsl.model.java.JavaModelImpl;
import com.android.tools.idea.gradle.dsl.model.repositories.RepositoriesModelImpl;
import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement;
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement;
import com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement;
import com.android.tools.idea.gradle.dsl.parser.configurations.ConfigurationsDslElement;
import com.android.tools.idea.gradle.dsl.parser.crashlytics.CrashlyticsDslElement;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslInfixExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradlePropertiesFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleScriptFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile;
import com.android.tools.idea.gradle.dsl.parser.java.JavaDslElement;
import com.android.tools.idea.gradle.dsl.parser.plugins.PluginsDslElement;
import com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class GradleBuildModelImpl extends GradleFileModelImpl implements GradleBuildModel {
  private static final Logger LOG = Logger.getInstance(GradleBuildModelImpl.class);
  @NonNls private static final String PLUGIN = "plugin";
  // TODO(xof): duplication with PluginModelImpl strings
  @NonNls private static final String ID = "id";
  @NonNls private static final String VERSION = "version";
  @NonNls private static final String APPLY = "apply";

  @NotNull protected GradleBuildFile myGradleBuildFile;

  public GradleBuildModelImpl(@NotNull GradleBuildFile buildDslFile) {
    super(buildDslFile);
    myGradleBuildFile = buildDslFile;
  }

  @Override
  @NotNull
  public List<PluginModel> plugins() {
    // Look for plugins block first if it exists, and then look for apply block to retrieve plugins.
    PluginsDslElement pluginsDslElement = myGradleDslFile.getPropertyElement(PLUGINS);
    ArrayList<PluginModelImpl> plugins = new ArrayList<>();
    if (pluginsDslElement != null) {
      plugins.addAll(PluginModelImpl.deduplicatePlugins(PluginModelImpl.create(pluginsDslElement)).values());
    }

    ApplyDslElement applyDslElement = myGradleDslFile.getPropertyElement(APPLY_BLOCK_NAME, ApplyDslElement.class);
    if (applyDslElement != null) {
      plugins.addAll(PluginModelImpl.deduplicatePlugins(PluginModelImpl.create(applyDslElement)).values());
    }

    return new ArrayList<>(PluginModelImpl.deduplicatePlugins(plugins).values());
  }

  @Override
  public @NotNull List<PluginModel> appliedPlugins() {
    Predicate<PluginModel> appliedPredicate = (plugin) -> {
      ResolvedPropertyModel apply = plugin.apply();
      ValueType valueType = apply.getValueType();
      if (valueType == NONE) {
        // Plugin declarations in build files default to `apply true`, which is also the correct meaning for syntactic forms
        // which cannot express an apply property, such as `apply plugin: 'foo'`.
        return true;
      }
      else if (valueType == BOOLEAN) {
        return apply.getValue(BOOLEAN_TYPE);
      }
      else {
        // not understood: default to not applied.
        return false;
      }
    };
    return plugins().stream().filter(appliedPredicate).collect(Collectors.toList());
  }


  @NotNull
  @Override
  public PluginModel applyPlugin(@NotNull String plugin) {
    PluginsDslElement pluginsDslElement = myGradleDslFile.getPropertyElement(PLUGINS);
    ApplyDslElement applyDslElement = myGradleDslFile.getPropertyElement(APPLY_BLOCK_NAME, ApplyDslElement.class);
    // If no plugins declaration exist, create a PluginDslElement to apply plugins
    if (pluginsDslElement == null && applyDslElement == null) {
      int at = 0;
      List<GradleDslElement> elements = myGradleDslFile.getCurrentElements();
      if (elements.size() > 0 && elements.get(0) instanceof BuildScriptDslElement) {
        at += 1;
      }
      pluginsDslElement = myGradleDslFile.ensurePropertyElementAt(PLUGINS, at);
    }
    else if (pluginsDslElement == null) {
      Map<String, PluginModelImpl> models = PluginModelImpl.deduplicatePlugins(PluginModelImpl.create(applyDslElement));
      if (models.containsKey(plugin)) {
        return models.get(plugin);
      }

      GradleDslExpressionMap applyMap = new GradleDslExpressionMap(myGradleDslFile, GradleNameElement.create(APPLY_BLOCK_NAME));
      applyMap.setAsNamedArgs(true);
      GradleDslLiteral literal = new GradleDslLiteral(applyMap, GradleNameElement.create(PLUGIN));
      literal.setValue(plugin.trim());
      applyMap.setNewElement(literal);
      applyDslElement.setNewElement(applyMap);

      return new PluginModelImpl(applyMap);
    }

    Map<String, PluginModelImpl> models = PluginModelImpl.deduplicatePlugins(PluginModelImpl.create(pluginsDslElement));
    if (models.containsKey(plugin)) {
      return models.get(plugin);
    }
    if (applyDslElement != null) {
      Map<String, PluginModelImpl> applyPluginsModels = PluginModelImpl.deduplicatePlugins(PluginModelImpl.create(applyDslElement));
      if (applyPluginsModels.containsKey(plugin)) {
        return applyPluginsModels.get(plugin);
      }
    }

    // Create the plugin literal.
    GradleDslLiteral literal = new GradleDslLiteral(pluginsDslElement, GradleNameElement.create(ID));
    literal.setElementType(PropertyType.REGULAR);
    literal.setValue(plugin.trim());
    pluginsDslElement.setNewElement(literal);

    return new PluginModelImpl(literal);
  }

  @Override
  public @NotNull PluginModel applyPlugin(@NotNull String plugin, @NotNull String version, @Nullable Boolean apply) {
    // For this method, the existence of an apply block is irrelevant, as the features of the plugins Dsl are not supported
    // with an apply operator; we must always find the plugins block.

    int at = 0;
    List<GradleDslElement> elements = myGradleDslFile.getCurrentElements();
    if (elements.size() > 0 && elements.get(0) instanceof BuildScriptDslElement) {
      at += 1;
    }
    PluginsDslElement pluginsElement = myGradleDslFile.ensurePropertyElementAt(PLUGINS, at);
    GradleDslInfixExpression expression = new GradleDslInfixExpression(pluginsElement, null);

    // id '<plugin>'
    expression.setNewLiteral(ID, plugin);
    // ... version '<version>'
    expression.setNewLiteral(VERSION, version);
    // ... apply <boolean>
    if (apply != null) {
      expression.setNewLiteral(APPLY, apply);
    }
    // link everything up
    pluginsElement.setNewElement(expression);

    return new PluginModelImpl(expression);
  }

  @Override
  public void removePlugin(@NotNull String plugin) {
    // First look for plugins{} block (i.e. PluginsDslElement) if it exists, and try to remove the plugin from.
    PluginsDslElement pluginsDslElement = myGradleDslFile.getPropertyElement(PLUGINS);
    if (pluginsDslElement != null) {
      PluginModelImpl.removePlugins(PluginModelImpl.create(pluginsDslElement), plugin);
    }

    // If ApplyDslElement exists, try to remove the plugin from it as well (We might have plugins defined in both apply and plugins blocks).
    ApplyDslElement applyDslElement = myGradleDslFile.getPropertyElement(APPLY_BLOCK_NAME, ApplyDslElement.class);
    if (applyDslElement != null) {
      PluginModelImpl.removePlugins(PluginModelImpl.create(applyDslElement), plugin);
    }
  }

  @Nullable
  @Override
  public PsiElement getPluginsPsiElement() {
    PluginsDslElement pluginsDslElement = myGradleDslFile.getPropertyElement(PLUGINS);
    if (pluginsDslElement != null) {
      return pluginsDslElement.getPsiElement();
    }
    return null;
  }

  /**
   * Returns {@link AndroidModelImpl} to read and update android block contents in the build.gradle file.
   *
   * <p>Returns {@code null} when experimental plugin is used as reading and updating android section is not supported for the
   * experimental dsl.</p>
   */
  @Override
  @NotNull
  public AndroidModel android() {
    AndroidDslElement androidDslElement = myGradleDslFile.ensurePropertyElement(ANDROID);
    return new AndroidModelImpl(androidDslElement);
  }

  @NotNull
  @Override
  public BuildScriptModel buildscript() {
    BuildScriptDslElement buildScriptDslElement = myGradleDslFile.ensurePropertyElementAt(BUILDSCRIPT, 0);
    return new BuildScriptModelImpl(buildScriptDslElement);
  }

  @NotNull
  @Override
  public ConfigurationsModel configurations() {
    ConfigurationsDslElement configurationsDslElement =
      myGradleDslFile.ensurePropertyElementBefore(CONFIGURATIONS, DependenciesDslElement.class);
    return new ConfigurationsModelImpl(configurationsDslElement);
  }

  @NotNull
  @Override
  public CrashlyticsModel crashlytics() {
    CrashlyticsDslElement crashlyticsDslElement = myGradleDslFile.ensurePropertyElement(CRASHLYTICS);
    return new CrashlyticsModelImpl(crashlyticsDslElement);
  }

  @NotNull
  @Override
  public DependenciesModel dependencies() {
    DependenciesDslElement dependenciesDslElement = myGradleDslFile.ensurePropertyElement(DEPENDENCIES);
    return new DependenciesModelImpl(dependenciesDslElement);
  }

  @Override
  @NotNull
  public ExtModel ext() {
    int at = 0;
    List<GradleDslElement> elements = myGradleDslFile.getCurrentElements();
    for (GradleDslElement element : elements) {
      if (!(element instanceof ApplyDslElement || element instanceof PluginsDslElement || element instanceof BuildScriptDslElement)) {
        break;
      }
      at += 1;
    }
    ExtDslElement extDslElement = myGradleDslFile.ensurePropertyElementAt(EXT, at);
    return new ExtModelImpl(extDslElement);
  }

  @NotNull
  @Override
  public JavaModel java() {
    JavaDslElement javaDslElement = myGradleDslFile.ensurePropertyElement(JAVA);
    return new JavaModelImpl(javaDslElement);
  }

  @NotNull
  @Override
  public RepositoriesModel repositories() {
    RepositoriesDslElement repositoriesDslElement = myGradleDslFile.ensurePropertyElement(REPOSITORIES);
    return new RepositoriesModelImpl(repositoriesDslElement);
  }

  @Override
  @NotNull
  public Set<GradleFileModel> getInvolvedFiles() {
    return getAllInvolvedFiles().stream().distinct()
      .map(GradleBuildModelImpl::getFileModel).filter(Objects::nonNull).collect(Collectors.toSet());
  }

  @Override
  @NotNull
  public File getModuleRootDirectory() {
    BuildModelContext context = myGradleDslFile.getContext();
    VirtualFile projectSettingsFile = context.getProjectSettingsFile();
    if (projectSettingsFile == null) {
      // The settings file does not exist, so we don't know much about this project.
      // Best-effort result: the directory of the build.gradle file.
      return myGradleDslFile.getDirectoryPath();
    }
    GradleSettingsFile settingsFile = context.getOrCreateSettingsFile(projectSettingsFile);
    GradleSettingsModel settingsModel = new GradleSettingsModelImpl(settingsFile);
    File directory = settingsModel.moduleDirectory(myGradleDslFile.getName());
    if (directory == null) {
      // The dsl file does not correspond to a known module, so we don't know where the module directory is.
      // Best-effort result: the directory of the build.gradle file.
      return myGradleDslFile.getDirectoryPath();
    }
    return directory;
  }

  @Override
  public @NotNull Set<GradleDslFile> getAllInvolvedFiles() {
    Set<GradleDslFile> files = new HashSet<>();
    files.add(myGradleBuildFile);
    // Add all parent dsl files.
    files.addAll(getParentFiles());

    List<GradleScriptFile> currentFiles = new ArrayList<>();
    currentFiles.add(myGradleBuildFile);
    // TODO: Generalize cycle detection in GradleDslSimpleExpression and reuse here.
    // Attempting to parse a cycle of applied files will fail in GradleDslFile#mergeAppliedFiles;
    while (!currentFiles.isEmpty()) {
      GradleScriptFile currentFile = currentFiles.remove(0);
      files.addAll(currentFile.getApplyDslElement());
      currentFiles.addAll(currentFile.getApplyDslElement());
    }

    // Get all the properties files.
    for (GradleDslFile file : new ArrayList<>(files)) {
      if (file instanceof GradleBuildFile) {
        GradleBuildFile buildFile = (GradleBuildFile)file;
        GradleDslFile sibling = buildFile.getPropertiesFile();
        if (sibling != null) {
          files.add(sibling);
        }
      }
    }

    return files;
  }

  private Set<GradleBuildFile> getParentFiles() {
    Set<GradleBuildFile> files = new HashSet<>();
    GradleBuildFile file = myGradleBuildFile.getParentModuleBuildFile();
    while (file != null) {
      files.add(file);
      file = file.getParentModuleBuildFile();
    }
    return files;
  }

  @Override
  @NotNull
  public Map<String, List<BuildModelNotification>> getNotifications() {
    return getAllInvolvedFiles().stream().filter(e -> !e.getPublicNotifications().isEmpty())
      .collect(Collectors.toMap(e -> e.getFile().getPath(), e -> e.getPublicNotifications()));
  }

  @Override
  public @Nullable GradlePropertiesModel getPropertiesModel() {
    GradlePropertiesFile propertiesFile = myGradleBuildFile.getPropertiesFile();
    if (propertiesFile == null) return null;
    return new GradlePropertiesModelImpl(propertiesFile);
  }

  /**
   * Removes property {@link RepositoriesDslElement#REPOSITORIES}.
   */
  @Override
  @TestOnly
  public void removeRepositoriesBlocks() {
    myGradleDslFile.removeProperty(REPOSITORIES.name);
  }

  @Nullable
  private static GradleFileModel getFileModel(@NotNull GradleDslFile file) {
    if (file instanceof GradleBuildFile) {
      return new GradleBuildModelImpl((GradleBuildFile)file);
    }
    else if (file instanceof GradleSettingsFile) {
      return new GradleSettingsModelImpl((GradleSettingsFile)file);
    }
    else if (file instanceof GradlePropertiesFile) {
      return new GradlePropertiesModelImpl((GradlePropertiesFile)file);
    }
    else {
      LOG.warn(new IllegalStateException("Unknown GradleDslFile type found!" + file));
      return null;
    }
  }
}
