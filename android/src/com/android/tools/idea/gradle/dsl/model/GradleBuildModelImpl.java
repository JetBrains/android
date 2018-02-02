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

import com.android.tools.idea.gradle.dsl.api.BuildScriptModel;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleFileModel;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import com.android.tools.idea.gradle.dsl.api.java.JavaModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl;
import com.android.tools.idea.gradle.dsl.model.build.BuildScriptModelImpl;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModelImpl;
import com.android.tools.idea.gradle.dsl.model.ext.ExtModelImpl;
import com.android.tools.idea.gradle.dsl.model.java.JavaModelImpl;
import com.android.tools.idea.gradle.dsl.model.repositories.RepositoriesModelImpl;
import com.android.tools.idea.gradle.dsl.model.values.GradleNotNullValueImpl;
import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement;
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement;
import com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement;
import com.android.tools.idea.gradle.dsl.parser.build.SubProjectsDslElement;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradlePropertiesFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile;
import com.android.tools.idea.gradle.dsl.parser.java.JavaDslElement;
import com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.android.SdkConstants.FN_GRADLE_PROPERTIES;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.dsl.api.values.GradleValue.getValues;
import static com.android.tools.idea.gradle.dsl.model.GradlePropertiesModel.parsePropertiesFile;
import static com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement.ANDROID_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement.APPLY_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement.BUILDSCRIPT_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.build.SubProjectsDslElement.SUBPROJECTS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement.DEPENDENCIES_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement.EXT_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.java.JavaDslElement.JAVA_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement.REPOSITORIES_BLOCK_NAME;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

public class GradleBuildModelImpl extends GradleFileModelImpl implements GradleBuildModel {
  @NonNls private static final String PLUGIN = "plugin";

  @NotNull private List<GradleDslExpressionMap> myToBeAppliedPlugins = new ArrayList<>();

  @Nullable
  public static GradleBuildModel get(@NotNull Project project) {
    VirtualFile file = getGradleBuildFile(getBaseDirPath(project));
    return file != null ? parseBuildFile(file, project, project.getName()) : null;
  }

  @Nullable
  public static GradleBuildModel get(@NotNull Module module) {
    VirtualFile file = getGradleBuildFile(module);
    return file != null ? parseBuildFile(file, module.getProject(), module.getName()) : null;
  }

  @NotNull
  public static GradleBuildModel parseBuildFile(@NotNull VirtualFile file, @NotNull Project project) {
    return parseBuildFile(file, project, "<Unknown>");
  }

  @NotNull
  public static GradleBuildModel parseBuildFile(@NotNull VirtualFile file, @NotNull Project project, @NotNull String moduleName) {
    GradleBuildFile buildDslFile = new GradleBuildFile(file, project, moduleName);
    ApplicationManager.getApplication().runReadAction(() -> {
      populateWithParentModuleSubProjectsProperties(buildDslFile);
      populateSiblingDslFileWithGradlePropertiesFile(buildDslFile);
      buildDslFile.parse();
    });
    return new GradleBuildModelImpl(buildDslFile);
  }

  private static void populateWithParentModuleSubProjectsProperties(@NotNull GradleBuildFile buildDslFile) {
    GradleSettingsModel gradleSettingsModel = GradleSettingsModelImpl.get(buildDslFile.getProject());
    if (gradleSettingsModel == null) {
      return;
    }

    String modulePath = gradleSettingsModel.moduleWithDirectory(buildDslFile.getDirectoryPath());
    if (modulePath == null) {
      return;
    }

    GradleBuildModel parentModuleModel = gradleSettingsModel.getParentModuleModel(modulePath);
    if (parentModuleModel == null || !(parentModuleModel instanceof GradleBuildModelImpl)) {
      return;
    }

    GradleBuildModelImpl parentModuleModelImpl = (GradleBuildModelImpl)parentModuleModel;

    GradleDslFile parentModuleDslFile = parentModuleModelImpl.myGradleDslFile;
    buildDslFile.setParentModuleDslFile(parentModuleDslFile);

    SubProjectsDslElement subProjectsDslElement =
      parentModuleDslFile.getPropertyElement(SUBPROJECTS_BLOCK_NAME, SubProjectsDslElement.class);
    if (subProjectsDslElement == null) {
      return;
    }

    buildDslFile.setParsedElement(SUBPROJECTS_BLOCK_NAME, subProjectsDslElement);
    for (Map.Entry<String, GradleDslElement> entry : subProjectsDslElement.getPropertyElements().entrySet()) {
      buildDslFile.setParsedElement(entry.getKey(), entry.getValue());
    }
  }

  private static void populateSiblingDslFileWithGradlePropertiesFile(@NotNull GradleBuildFile buildDslFile) {
    File propertiesFilePath = new File(buildDslFile.getDirectoryPath(), FN_GRADLE_PROPERTIES);
    VirtualFile propertiesFile = findFileByIoFile(propertiesFilePath, true);
    if (propertiesFile == null) {
      return;
    }

    GradlePropertiesModel propertiesModel = parsePropertiesFile(propertiesFile, buildDslFile.getProject(), buildDslFile.getName());
    if (propertiesModel == null) {
      return;
    }

    GradleDslFile propertiesDslFile = propertiesModel.myGradleDslFile;
    buildDslFile.setSiblingDslFile(propertiesDslFile);
    propertiesDslFile.setSiblingDslFile(buildDslFile);
  }

  private GradleBuildModelImpl(@NotNull GradleBuildFile buildDslFile) {
    super(buildDslFile);
  }

  @NotNull
  @Override
  public List<GradleNotNullValue<String>> appliedPlugins() {
    ApplyDslElement applyDslElement = myGradleDslFile.getPropertyElement(APPLY_BLOCK_NAME, ApplyDslElement.class);
    if (applyDslElement == null) {
      return ImmutableList.of();
    }

    List<GradleNotNullValue<String>> listProperty = applyDslElement.getListProperty(PLUGIN, String.class);
    if (listProperty == null) {
      return ImmutableList.of();
    }

    List<GradleNotNullValue<String>> plugins = new ArrayList<>();
    Set<String> pluginValues = new HashSet<>();
    for (GradleNotNullValue<String> plugin : listProperty) {
      if (pluginValues.add(plugin.value())) { // Avoid duplicate plugin entries.
        plugins.add(plugin);
      }
    }
    for (GradleDslExpressionMap toBeAppliedPlugin : myToBeAppliedPlugins) {
      GradleNullableValue<String> plugin = toBeAppliedPlugin.getLiteralProperty(PLUGIN, String.class);
      assert plugin instanceof GradleNotNullValueImpl;
      if (pluginValues.add(plugin.value())) { // Avoid duplicate plugin entries.
        plugins.add((GradleNotNullValueImpl<String>)plugin);
      }
    }

    return plugins;
  }

  @NotNull
  @Override
  public GradleBuildModel applyPlugin(@NotNull String plugin) {
    if (getValues(appliedPlugins()).contains(plugin.trim())) {
      return this;
    }
    GradleDslExpressionMap applyMap = new GradleDslExpressionMap(myGradleDslFile, GradleNameElement.create(APPLY_BLOCK_NAME));
    applyMap.addNewLiteral(PLUGIN, plugin.trim());
    myToBeAppliedPlugins.add(applyMap);
    return this;
  }

  @NotNull
  @Override
  public GradleBuildModel removePlugin(@NotNull String plugin) {
    plugin = plugin.trim();
    ApplyDslElement applyDslElement = myGradleDslFile.getPropertyElement(APPLY_BLOCK_NAME, ApplyDslElement.class);
    if (applyDslElement == null) {
      return this;
    }

    List<GradleDslExpressionMap> toBeRemovedPlugins = new ArrayList<>();
    for (GradleDslExpressionMap applyMap : myToBeAppliedPlugins) {
      if (plugin.equals(applyMap.getLiteralProperty(PLUGIN, String.class).value())) {
        toBeRemovedPlugins.add(applyMap);
      }
    }
    myToBeAppliedPlugins.removeAll(toBeRemovedPlugins);

    while (getValues(applyDslElement.getListProperty(PLUGIN, String.class)).contains(plugin)) {
      applyDslElement.removeFromExpressionList(PLUGIN, plugin);
    }

    return this;
  }

  /**
   * Returns {@link AndroidModelImpl} to read and update android block contents in the build.gradle file.
   *
   * <p>Returns {@code null} when experimental plugin is used as reading and updating android section is not supported for the
   * experimental dsl.</p>
   */
  @Nullable
  @Override
  public AndroidModel android() {
    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.find(myGradleDslFile.getProject());
    if (androidPluginInfo != null && androidPluginInfo.isExperimental()) {
      return null; // Reading or updating Android block contents is not supported when experimental plugin is used.
    }

    AndroidDslElement androidDslElement = myGradleDslFile.getPropertyElement(ANDROID_BLOCK_NAME, AndroidDslElement.class);
    if (androidDslElement == null) {
      androidDslElement = new AndroidDslElement(myGradleDslFile);
      myGradleDslFile.setNewElement(ANDROID_BLOCK_NAME, androidDslElement);
    }
    return new AndroidModelImpl(androidDslElement);
  }

  @NotNull
  @Override
  public BuildScriptModel buildscript() {
    BuildScriptDslElement buildScriptDslElement = myGradleDslFile.getPropertyElement(BUILDSCRIPT_BLOCK_NAME, BuildScriptDslElement.class);
    if (buildScriptDslElement == null) {
      buildScriptDslElement = new BuildScriptDslElement(myGradleDslFile);
      myGradleDslFile.setNewElement(BUILDSCRIPT_BLOCK_NAME, buildScriptDslElement);
    }
    return new BuildScriptModelImpl(buildScriptDslElement);
  }

  @NotNull
  @Override
  public DependenciesModel dependencies() {
    DependenciesDslElement dependenciesDslElement =
      myGradleDslFile.getPropertyElement(DEPENDENCIES_BLOCK_NAME, DependenciesDslElement.class);
    if (dependenciesDslElement == null) {
      dependenciesDslElement = new DependenciesDslElement(myGradleDslFile);
      myGradleDslFile.setNewElement(DEPENDENCIES_BLOCK_NAME, dependenciesDslElement);
    }
    return new DependenciesModelImpl(dependenciesDslElement);
  }

  @Override
  @NotNull
  public ExtModel ext() {
    ExtDslElement extDslElement = myGradleDslFile.getPropertyElement(EXT_BLOCK_NAME, ExtDslElement.class);
    if (extDslElement == null) {
      extDslElement = new ExtDslElement(myGradleDslFile);
      myGradleDslFile.setNewElement(EXT_BLOCK_NAME, extDslElement);
    }
    return new ExtModelImpl(extDslElement);
  }

  @NotNull
  @Override
  public JavaModel java() {
    JavaDslElement javaDslElement = myGradleDslFile.getPropertyElement(JAVA_BLOCK_NAME, JavaDslElement.class);
    if (javaDslElement == null) {
      javaDslElement = new JavaDslElement(myGradleDslFile);
      myGradleDslFile.setNewElement(JAVA_BLOCK_NAME, javaDslElement);
    }
    return new JavaModelImpl(javaDslElement);
  }

  @NotNull
  @Override
  public RepositoriesModel repositories() {
    RepositoriesDslElement repositoriesDslElement =
      myGradleDslFile.getPropertyElement(REPOSITORIES_BLOCK_NAME, RepositoriesDslElement.class);
    if (repositoriesDslElement == null) {
      repositoriesDslElement = new RepositoriesDslElement(myGradleDslFile);
      myGradleDslFile.setNewElement(REPOSITORIES_BLOCK_NAME, repositoriesDslElement);
    }
    return new RepositoriesModelImpl(repositoriesDslElement);
  }

  @Override
  public void resetState() {
    myToBeAppliedPlugins.clear();
    super.resetState();
  }

  @Override
  public void applyChanges() {
    // The apply plugin statements requires special handling, because we want to add a new map with same name (apply) for every plugin
    // needs to be added.
    for (GradleDslExpressionMap applyMap : myToBeAppliedPlugins) {
      applyMap.create();
      applyMap.applyChanges();
      myGradleDslFile.addParsedElement(APPLY_BLOCK_NAME, applyMap);
    }
    myToBeAppliedPlugins.clear();
    super.applyChanges();
  }

  @Override
  @NotNull
  public Set<GradleFileModel> getInvolvedFiles() {
    return getAllInvolvedFiles().stream().map(e -> getFileModel(e)).collect(Collectors.toSet());
  }

  @NotNull
  private static GradleFileModel getFileModel(@NotNull GradleDslFile file) {
    if (file instanceof GradleBuildFile) {
      return new GradleBuildModelImpl((GradleBuildFile)file);
    } else if (file instanceof GradleSettingsFile) {
      return new GradleSettingsModelImpl((GradleSettingsFile)file);
    } else if (file instanceof GradlePropertiesFile) {
      return new GradlePropertiesModel(file);
    }
    throw new IllegalStateException("Unknown GradleDslFile type found!");
  }

  /**
   * Removes property {@link RepositoriesDslElement#REPOSITORIES_BLOCK_NAME}.
   */
  @Override
  @TestOnly
  public void removeRepositoriesBlocks() {
    myGradleDslFile.removeProperty(REPOSITORIES_BLOCK_NAME);
  }
}
