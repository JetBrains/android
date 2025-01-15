/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.tools.idea.gradle.dsl.model.GradleModelFactory.createGradleBuildModel;
import static com.android.tools.idea.gradle.dsl.utils.SdkConstants.FN_BUILD_GRADLE_DECLARATIVE;
import static com.android.tools.idea.gradle.dsl.utils.SdkConstants.FN_SETTINGS_GRADLE_DECLARATIVE;

import com.android.tools.idea.flags.DeclarativeStudioSupport;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleDeclarativeBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleDeclarativeSettingsModel;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogsModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleVersionCatalogFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProjectBuildModelImpl implements ProjectBuildModel {
  @NotNull private final BuildModelContext myBuildModelContext;
  @Nullable private final GradleBuildFile myProjectBuildFile;

  /**
   * @param project the project this model should be built for
   * @param file the file contain the projects main build.gradle
   * @param buildModelContext
   */
  public ProjectBuildModelImpl(@NotNull Project project, @Nullable VirtualFile file, @NotNull BuildModelContext buildModelContext) {
    myBuildModelContext = buildModelContext;
    myProjectBuildFile = myBuildModelContext.initializeContext(project, file);
  }

  @Override
  @NotNull
  public BuildModelContext getContext() {
    return myBuildModelContext;
  }

  @Override
  @Nullable
  public GradleBuildModel getProjectBuildModel() {
    return myProjectBuildFile == null ? null : createGradleBuildModel(myProjectBuildFile);
  }

  @Override
  @Nullable
  public GradleBuildModel getModuleBuildModel(@NotNull Module module) {
    VirtualFile file = myBuildModelContext.getGradleBuildFile(module);
    return file == null ? null : getModuleBuildModel(file);
  }

  @Override
  @Nullable
  public GradleBuildModel getModuleBuildModel(@NotNull File modulePath) {
    VirtualFile file = myBuildModelContext.getGradleBuildFile(modulePath);
    return file == null ? null : getModuleBuildModel(file);
  }

  @Override
  @Nullable
  public GradleDeclarativeBuildModel getDeclarativeModuleBuildModel(@NotNull Module module) {
    if(!DeclarativeStudioSupport.isEnabled()) return null;
    VirtualFile file = myBuildModelContext.getGradleBuildFile(module);
    return file == null ? null : getDeclarativeModuleBuildModel(file);
  }
  @Override
  @Nullable
  public GradleDeclarativeBuildModel getDeclarativeModuleBuildModel(@NotNull  File modulePath) {
    if(!DeclarativeStudioSupport.isEnabled()) return null;
    VirtualFile file = myBuildModelContext.getGradleBuildFile(modulePath);
    return file == null ? null : getDeclarativeModuleBuildModel(file);
  }
  @Override
  @Nullable
  public GradleDeclarativeBuildModel getDeclarativeModuleBuildModel(@NotNull VirtualFile file) {
    if(!DeclarativeStudioSupport.isEnabled()) return null;
    if(!file.getName().equals(FN_BUILD_GRADLE_DECLARATIVE)) return null;

    GradleBuildFile dslFile = myBuildModelContext.getOrCreateBuildFile(file, false);
    return new GradleDeclarativeBuildModelImpl(dslFile);
  }

  /**
   * Gets the {@link GradleBuildModel} for the given {@link VirtualFile}. Please prefer using {@link #getModuleBuildModel(Module)} if
   * possible.
   *
   * @param file the file to parse, this file should be a Gradle build file that represents a Gradle Project (Idea Module or Project). The
   *             given file must also belong to the {@link Project} for which this {@link ProjectBuildModel} was created.
   * @return the build model for the requested file
   */
  @Override
  @NotNull
  public GradleBuildModel getModuleBuildModel(@NotNull VirtualFile file) {
    GradleBuildFile dslFile = myBuildModelContext.getOrCreateBuildFile(file, false);
    return createGradleBuildModel(dslFile);
  }

  @Override
  @Nullable
  public GradleSettingsModel getProjectSettingsModel() {
    VirtualFile virtualFile = getProjectSettingsFile();
    if (virtualFile == null) return null;

    GradleSettingsFile settingsFile = myBuildModelContext.getOrCreateSettingsFile(virtualFile);
    return new GradleSettingsModelImpl(settingsFile);
  }

  @Override
  @Nullable
  public GradleDeclarativeSettingsModel getDeclarativeSettingsModel() {
    if(!DeclarativeStudioSupport.isEnabled()) return null;
    VirtualFile virtualFile = getProjectSettingsFile();
    if (virtualFile == null) return null;
    if(!virtualFile.getName().equals(FN_SETTINGS_GRADLE_DECLARATIVE)) return null;

    GradleSettingsFile settingsFile = myBuildModelContext.getOrCreateSettingsFile(virtualFile);
    return new GradleDeclarativeSettingsModelImpl(settingsFile);
  }

  @Nullable
  private VirtualFile getProjectSettingsFile() {
    VirtualFile virtualFile;
    // If we don't have a root build file, guess the location of the settings file from the project.
    if (myProjectBuildFile == null) {
      virtualFile = myBuildModelContext.getProjectSettingsFile();
    } else {
      virtualFile = myProjectBuildFile.tryToFindSettingsFile();
    }

    if (virtualFile == null) {
      return null;
    }
    return virtualFile;
  }

  @Override
  public void applyChanges() {
    PostprocessReformattingAspect.getInstance(myBuildModelContext.getProject())
      .postponeFormattingInside(() ->
                                  runOverProjectTree(file -> {
                                    file.applyChanges();
                                    file.saveAllChanges();
                                  }));
  }

  @Override
  public void resetState() {
    runOverProjectTree(GradleDslFile::resetState);
  }

  @Override
  public void reparse() {
    List<GradleDslFile> files = myBuildModelContext.getAllRequestedFiles();
    files.forEach(GradleDslFile::reparse);
  }

  @NotNull
  @Override
  public List<GradleBuildModel> getAllIncludedBuildModels() {
    return getAllIncludedBuildModels((i, j) -> {});
  }

  @NotNull
  @Override
  public List<GradleBuildModel> getAllIncludedBuildModels(@NotNull BiConsumer<Integer, Integer> func) {
    final Integer[] nModelsSeen = {0};
    List<GradleBuildModel> allModels = new ArrayList<>();
    if (myProjectBuildFile != null) {
      allModels.add(createGradleBuildModel(myProjectBuildFile));
      func.accept(++nModelsSeen[0], null);
    }

    // TODO(b/166739137): buildSrc is actually more like an included build; buildSrc could in principle have sub-projects, libraries, etc.,
    //  all themselves configured and built by the top-level Gradle build file in the buildSrc directory.
    File buildSrc =
      new File(FileUtil.toCanonicalPath(Optional.ofNullable(myBuildModelContext.getProject().getBasePath()).orElse("")), "buildSrc");
    VirtualFile buildSrcVirtualFile = myBuildModelContext.getGradleBuildFile(buildSrc);
    if (buildSrcVirtualFile != null) {
      allModels.add(getModuleBuildModel(buildSrcVirtualFile));
      func.accept(++nModelsSeen[0], null);
    }

    GradleSettingsModel settingsModel = getProjectSettingsModel();
    if (settingsModel == null) {
      return allModels;
    }

    Set<String> modulePaths = settingsModel.modulePaths();
    Integer nModelsToConsider = nModelsSeen[0] + modulePaths.size();

    allModels.addAll(modulePaths.stream().map((modulePath) -> {
      GradleBuildModel model = null;
      // This should have already been added above
      if (!modulePath.equals(":")) {
        File moduleDir = settingsModel.moduleDirectory(modulePath);
        if (moduleDir != null) {
          VirtualFile file = myBuildModelContext.getGradleBuildFile(moduleDir);
          if (file != null) {
            model = getModuleBuildModel(file);
          }
        }
      }
      func.accept(++nModelsSeen[0], nModelsToConsider);
      return model;
    }).filter(Objects::nonNull).collect(Collectors.toList()));
    return allModels;
  }

  @Override
  public @NotNull GradleVersionCatalogsModel getVersionCatalogsModel() {
    Collection<GradleVersionCatalogFile> files;
    files = getContext().getVersionCatalogFiles();
    return new GradleVersionCatalogsModelImpl(files);
  }

  private void runOverProjectTree(@NotNull Consumer<GradleDslFile> func) {
    myBuildModelContext.getAllRequestedFiles().forEach(func);
  }
}
