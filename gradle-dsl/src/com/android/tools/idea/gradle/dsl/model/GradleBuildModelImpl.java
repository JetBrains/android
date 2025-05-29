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
import static com.android.tools.idea.gradle.dsl.model.GradleModelFactory.createGradleBuildModel;
import static com.android.tools.idea.gradle.dsl.model.PluginModelImpl.ALIAS;
import static com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement.APPLY_BLOCK_NAME;
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
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.java.JavaDeclarativeModel;
import com.android.tools.idea.gradle.dsl.api.java.JavaModel;
import com.android.tools.idea.gradle.dsl.api.kotlin.KotlinModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel;
import com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl;
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement;
import com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslInfixExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradlePropertiesFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleScriptFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleVersionCatalogFile;
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

  protected GradleBuildModelImpl(@NotNull GradleBuildFile buildDslFile) {
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
      if (!elements.isEmpty() && elements.get(0) instanceof BuildScriptDslElement) {
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
  public @NotNull PluginModel applyPlugin(@NotNull String plugin, @Nullable String version, @Nullable Boolean apply) {
    // For this method, the existence of an apply block is irrelevant, as the features of the plugins Dsl are not supported
    // with an apply operator; we must always find the plugins block.

    int at = 0;
    List<GradleDslElement> elements = myGradleDslFile.getCurrentElements();
    if (!elements.isEmpty() && elements.get(0) instanceof BuildScriptDslElement) {
      at += 1;
    }
    PluginsDslElement pluginsElement = myGradleDslFile.ensurePropertyElementAt(PLUGINS, at);
    GradleDslInfixExpression expression = new GradleDslInfixExpression(pluginsElement, null);

    // id '<plugin>'
    expression.setNewLiteral(ID, plugin);
    // ... version '<version>'
    if(version != null) expression.setNewLiteral(VERSION, version);
    // ... apply <boolean>
    if (apply != null) {
      expression.setNewLiteral(APPLY, apply);
    }
    // link everything up
    pluginsElement.setNewElement(expression);

    return new PluginModelImpl(expression);
  }

  @Override
  public @NotNull PluginModel applyPlugin(@NotNull ReferenceTo reference, @Nullable Boolean apply) {
    int at = 0;
    List<GradleDslElement> elements = myGradleDslFile.getCurrentElements();
    if (!elements.isEmpty() && elements.get(0) instanceof BuildScriptDslElement) {
      at += 1;
    }
    PluginsDslElement pluginsElement = myGradleDslFile.ensurePropertyElementAt(PLUGINS, at);

    // not: reparented if apply is non-null
    GradleDslMethodCall alias = new GradleDslMethodCall(pluginsElement, GradleNameElement.empty(), ALIAS);
    GradleDslLiteral target = new GradleDslLiteral(alias.getArgumentsElement(), GradleNameElement.empty());
    target.setValue(reference);
    alias.addNewArgument(target);
    if (apply != null) {
      GradleDslInfixExpression expression = new GradleDslInfixExpression(pluginsElement, null);
      alias.setParent(expression);
      expression.setNewElement(alias);
      expression.setNewLiteral(APPLY, apply);
      pluginsElement.setNewElement(expression);
      return new PluginModelImpl(expression);
    }
    else {
      pluginsElement.setNewElement(alias);
      return new PluginModelImpl(alias);
    }

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
    return getModel(AndroidModel.class);
  }

  @NotNull
  @Override
  public BuildScriptModel buildscript() {
    return getModel(BuildScriptModel.class);
  }

  @NotNull
  @Override
  public ConfigurationsModel configurations() {
    return getModel(ConfigurationsModel.class);
  }

  @NotNull
  @Override
  public DependenciesModel dependencies() {
    return getModel(DependenciesModel.class);
  }

  @Override
  @NotNull
  public ExtModel ext() {
    return getModel(ExtModel.class);
  }

  @NotNull
  @Override
  public JavaModel java() {
    return getModel(JavaModel.class);
  }

  @NotNull
  @Override
  public JavaDeclarativeModel javaApplication() {
    return getModel(JavaDeclarativeModel.class);
  }

  @NotNull
  @Override
  public KotlinModel kotlin() {
    return getModel(KotlinModel.class);
  }

  @NotNull
  @Override
  public RepositoriesModel repositories() {
    return getModel(RepositoriesModel.class);
  }

  @Override
  @NotNull
  public Set<GradleFileModel> getInvolvedFiles() {
    return getAllInvolvedFiles().stream().distinct()
      .map(GradleBuildModelImpl::getFileModel).filter(Objects::nonNull).collect(Collectors.toSet());
  }

  @Override
  public <T extends GradleDslModel> @NotNull T getModel(Class<T> klass) {
    return GradleBlockModelMap.get(myGradleDslFile, GradleBuildModel.class, klass);
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
    // The dsl file does not correspond to a known module, so we don't know where the module directory is.
    // Best-effort result: the directory of the build.gradle file.
    if (directory == null) return myGradleDslFile.getDirectoryPath();
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

    // Get all the properties and version catalog files.
    for (final GradleDslFile file : new ArrayList<>(files)) {
      if (file instanceof GradleBuildFile buildFile) {
        final GradleDslFile sibling = buildFile.getPropertiesFile();
        if (sibling != null) {
          files.add(sibling);
        }
      }
    }
    files.addAll(getContext().getVersionCatalogFiles());

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
      .collect(Collectors.toMap(e -> e.getFile().getPath(), GradleDslFile::getPublicNotifications));
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
    //noinspection ConstantConditions
    myGradleDslFile.removeProperty(REPOSITORIES.name);
  }

  @Nullable
  private static GradleFileModel getFileModel(@NotNull GradleDslFile file) {
    if (file instanceof GradleBuildFile buildFile) {
      return createGradleBuildModel(buildFile);
    }
    else if (file instanceof GradleSettingsFile) {
      return new GradleSettingsModelImpl((GradleSettingsFile)file);
    }
    else if (file instanceof GradlePropertiesFile) {
      return new GradlePropertiesModelImpl((GradlePropertiesFile)file);
    }
    else if (file instanceof GradleVersionCatalogFile) {
      return new GradleVersionCatalogModelImpl((GradleVersionCatalogFile)file);
    }
    else {
      LOG.warn(new IllegalStateException("Unknown GradleDslFile type found!" + file));
      return null;
    }
  }

  @Override
  public @NotNull GradleDslElement getRawPropertyHolder() {
    // This implementation is needed here essentially because we do not model plugins { ... } as a block, which itself is because
    // historically plugins have been declared in multiple ways (in apply statements / blocks as well as in a plugins block).
    return myGradleBuildFile;
  }
}
