/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build;

import static com.android.tools.idea.gradle.util.BuildOutputUtil.getOutputFilesFromListingFile;
import static com.android.tools.idea.gradle.util.BuildOutputUtil.getOutputListingFileOrLogError;
import static com.android.tools.idea.projectsystem.gradle.GradleProjectPathKt.getGradleProjectPath;

import com.android.build.OutputFile;
import com.android.builder.model.AppBundleProjectBuildOutput;
import com.android.builder.model.AppBundleVariantBuildOutput;
import com.android.builder.model.InstantAppProjectBuildOutput;
import com.android.builder.model.InstantAppVariantBuildOutput;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.android.tools.idea.gradle.actions.BuildsToPathsMapper;
import com.android.tools.idea.gradle.model.IdeAndroidProjectType;
import com.android.tools.idea.gradle.model.IdeBuildTasksAndOutputInformation;
import com.android.tools.idea.gradle.model.IdeVariantBuildInformation;
import com.android.tools.idea.gradle.project.build.invoker.AssembleInvocationResult;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.run.OutputBuildAction;
import com.android.tools.idea.gradle.run.PostBuildModel;
import com.android.tools.idea.gradle.util.OutputType;
import com.android.tools.idea.projectsystem.gradle.GradleProjectPath;
import com.intellij.openapi.module.Module;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <p> Generates a map from module/build variant to the location of generated apk or bundle,
 * if it's a single apk, returns the apk file;
 * if there're multiple apks, returns the parent folder of apk files;
 * if it's app bundle, returns the bundle file.
 * <p>
 * {@link PostBuildModel} being built from the result of {@link OutputBuildAction} contains paths information of each of the build.
 */
public class BuildsToPathsMapperImpl extends BuildsToPathsMapper {
  @Override
  @NotNull
  public Map<String, File> getBuildsToPaths(@NotNull AssembleInvocationResult assembleResult,
                                            @NotNull List<String> buildVariants,
                                            @NotNull Collection<Module> modules,
                                            boolean isAppBundle) {
    boolean isSigned = !buildVariants.isEmpty();
    if (isSigned) {
      assert modules.size() == 1;
    }

    PostBuildModel postBuildModel = null;
    TreeMap<String, File> buildsToPathsCollector = new TreeMap<>();

    List<OutputBuildAction.PostBuildProjectModels> postBuildProjectModels =
      assembleResult.getInvocationResult()
        .getModels().stream()
        .filter(it -> it instanceof OutputBuildAction.PostBuildProjectModels)
        .map(it -> (OutputBuildAction.PostBuildProjectModels)it)
        .collect(Collectors.toList());
    if (!postBuildProjectModels.isEmpty()) {
      postBuildModel = new PostBuildModel(postBuildProjectModels.toArray(new OutputBuildAction.PostBuildProjectModels[0]));
    }

    for (Module module : modules) {
      GradleAndroidModel androidModel = GradleAndroidModel.get(module);
      if (androidModel == null) {
        continue;
      }

      if (!isSigned) {
        buildVariants = Collections.singletonList(androidModel.getSelectedVariant().getName());
      }

      for (String buildVariant : buildVariants) {
        collectBuildsToPaths(androidModel, postBuildModel, module, buildVariant, buildsToPathsCollector, isAppBundle, isSigned
        );
      }
    }

    return buildsToPathsCollector;
  }

  private static void collectBuildsToPaths(@NotNull GradleAndroidModel androidModel,
                                           @Nullable PostBuildModel postBuildModel,
                                           @NotNull Module module,
                                           @NotNull String buildVariant,
                                           @NotNull Map<String, File> buildsToPathsCollector,
                                           boolean isAppBundle,
                                           boolean isSigned) {
    File outputFolderOrFile = null;
    if (androidModel.getFeatures().isBuildOutputFileSupported()) {
      // get from build output listing file.
      OutputType outputType = isAppBundle ? OutputType.Bundle : OutputType.Apk;
      IdeBuildTasksAndOutputInformation outputInformation =
        androidModel.getAndroidProject().getVariantsBuildInformation().stream()
          .filter(it -> it.getVariantName().equals(buildVariant))
          .findFirst()
          .map(IdeVariantBuildInformation::getBuildInformation)
          .orElse(null);
      List<File> outputFiles = null;
      if (outputInformation != null) {
        String outputListingFile = getOutputListingFileOrLogError(outputInformation, outputType);
        if (outputListingFile != null) {
          outputFiles = getOutputFilesFromListingFile(outputListingFile);
        }
      }
      outputFolderOrFile =
        outputFiles != null
        ? outputFiles.size() > 1
          ? outputFiles.get(0).getParentFile()
          : !outputFiles.isEmpty() ? outputFiles.get(0) : null
        : null;
    }
    else if (postBuildModel != null) {
      if (androidModel.getAndroidProject().getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_APP ||
          androidModel.getAndroidProject().getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE) {
        if (isAppBundle) {
          outputFolderOrFile = tryToGetOutputPostBuildBundleFile(module, postBuildModel, buildVariant);
        }
        else {
          outputFolderOrFile = tryToGetOutputPostBuildApkFile(module, postBuildModel, buildVariant);
        }
      }
      else if (androidModel.getAndroidProject().getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP) {
        outputFolderOrFile = tryToGetOutputPostBuildInstantApp(module, postBuildModel, buildVariant);
      }
    }

    if (outputFolderOrFile == null) {
      return;
    }

    buildsToPathsCollector.put(isSigned ? buildVariant : module.getName(), outputFolderOrFile);
  }

  @Nullable
  private static File tryToGetOutputPostBuildApkFile(@NotNull Module module,
                                                     @NotNull PostBuildModel postBuildModel,
                                                     @NotNull String buildVariant) {
    // NOTE: Post build models do not support composite builds properly.
    ProjectBuildOutput projectBuildOutput = postBuildModel.findProjectBuildOutput(getGradlePath(module));
    if (projectBuildOutput == null) {
      return null;
    }

    for (VariantBuildOutput variantBuildOutput : projectBuildOutput.getVariantsBuildOutput()) {
      if (variantBuildOutput.getName().equals(buildVariant)) {
        Collection<OutputFile> outputs = variantBuildOutput.getOutputs();
        File outputFolderOrApk = outputs.iterator().next().getOutputFile();
        if (outputs.size() > 1) {
          return outputFolderOrApk.getParentFile();
        }
        return outputFolderOrApk;
      }
    }

    return null;
  }

  @Nullable
  private static String getGradlePath(@NotNull Module module) {
    GradleProjectPath gradleProjectPath = getGradleProjectPath(module);
    return gradleProjectPath != null ? gradleProjectPath.getPath() : null;
  }

  @Nullable
  private static File tryToGetOutputPostBuildBundleFile(@NotNull Module module,
                                                        @NotNull PostBuildModel postBuildModel,
                                                        @NotNull String buildVariant) {
    AppBundleProjectBuildOutput appBundleProjectBuildOutput = postBuildModel.findAppBundleProjectBuildOutput(getGradlePath(module));
    if (appBundleProjectBuildOutput == null) {
      return null;
    }

    for (AppBundleVariantBuildOutput variantBuildOutput : appBundleProjectBuildOutput.getAppBundleVariantsBuildOutput()) {
      if (variantBuildOutput.getName().equals(buildVariant)) {
        return variantBuildOutput.getBundleFile();
      }
    }

    return null;
  }

  @Nullable
  private static File tryToGetOutputPostBuildInstantApp(@NotNull Module module,
                                                        @NotNull PostBuildModel postBuildModel,
                                                        @NotNull String buildVariant) {
    InstantAppProjectBuildOutput instantAppProjectBuildOutput = postBuildModel.findInstantAppProjectBuildOutput(getGradlePath(module));
    if (instantAppProjectBuildOutput == null) {
      return null;
    }

    for (InstantAppVariantBuildOutput variantBuildOutput : instantAppProjectBuildOutput.getInstantAppVariantsBuildOutput()) {
      if (variantBuildOutput.getName().equals(buildVariant)) {
        return variantBuildOutput.getOutput().getOutputFile();
      }
    }

    return null;
  }
}
