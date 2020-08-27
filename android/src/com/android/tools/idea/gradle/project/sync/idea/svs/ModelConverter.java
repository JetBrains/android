/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea.svs;

import static com.intellij.util.containers.ContainerUtil.map;
import static java.util.Collections.emptyList;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.ProjectSyncIssues;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
import com.android.builder.model.v2.models.ndk.NativeModule;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.ide.common.gradle.model.impl.ModelCache;
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeAndroidProject;
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeVariantAbi;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.model.V2NdkModel;
import com.android.tools.idea.gradle.project.sync.idea.issues.AndroidSyncException;
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueData;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModelConverter {
  @NotNull
  public static IdeAndroidModels convertToIdeModels(@NotNull ModelCache modelCache,
                                                    @NotNull AndroidProject androidProject,
                                                    @Nullable VariantGroup variantGroup,
                                                    @Nullable NativeModule nativeModule,
                                                    @Nullable NativeAndroidProject nativeAndroidProject,
                                                    @Nullable ProjectSyncIssues projectSyncIssues) {
    IdeAndroidProject ideAndroidProject = modelCache.androidProjectFrom(androidProject);
    Collection<Variant> fetchedVariants = (variantGroup == null) ? androidProject.getVariants() : variantGroup.getVariants();
    List<IdeVariant> fetchedIdeVariants =
      map(fetchedVariants, it -> modelCache.variantFrom(it, GradleVersion.tryParse(androidProject.getModelVersion())));
    String selectedVariantName = findVariantToSelect(androidProject, variantGroup).getName();
    List<SyncIssueData> syncIssues = createSyncIssueData(androidProject, projectSyncIssues);

    @Nullable V2NdkModel ndkModel =
      nativeModule != null
      ? new V2NdkModel(androidProject.getModelVersion(), modelCache.nativeModuleFrom(nativeModule))
      : null;

    @Nullable IdeNativeAndroidProject nativeProjectCopy =
      nativeAndroidProject != null
      ? modelCache.nativeAndroidProjectFrom(nativeAndroidProject)
      : null;

    @Nullable List<IdeNativeVariantAbi> ideNativeVariantAbis =
      variantGroup != null
      ? map(ModelCache.safeGet(variantGroup::getNativeVariants, emptyList()), modelCache::nativeVariantAbiFrom)
      : null;

    return new IdeAndroidModels(ideAndroidProject,
                                fetchedIdeVariants,
                                selectedVariantName,
                                syncIssues,
                                ndkModel,
                                nativeProjectCopy,
                                ideNativeVariantAbis);
  }

  @NotNull
  private static List<SyncIssueData> createSyncIssueData(@NotNull AndroidProject androidProject, ProjectSyncIssues projectSyncIssues) {
    List<SyncIssueData> issueData;
    Collection<SyncIssue> syncIssues = findSyncIssues(androidProject, projectSyncIssues);
    // Add the SyncIssues as DataNodes to the project data tree. While we could just re-use the
    // SyncIssues in AndroidModuleModel this allows us to remove sync issues from the IDE side model in the future.
    issueData = map(syncIssues, syncIssue -> {
      List<String> multiLineMessage = ModelCache.safeGet(syncIssue::getMultiLineMessage, null);
      multiLineMessage = multiLineMessage != null ? ImmutableList.copyOf(multiLineMessage) : null;
      return new SyncIssueData(syncIssue.getMessage(),
                               syncIssue.getData(),
                               multiLineMessage,
                               syncIssue.getSeverity(),
                               syncIssue.getType());
    });
    return issueData;
  }

  /**
   * Obtains a list of [SyncIssue]s from either the [AndroidProject] (legacy pre Android Gradle plugin 3.6)
   * or from the [ProjectSyncIssues] model (post Android Gradle plugin 3.6).
   */
  @NotNull
  private static Collection<SyncIssue> findSyncIssues(@NotNull AndroidProject androidProject,
                                                      @Nullable ProjectSyncIssues projectSyncIssues) {
    if (projectSyncIssues != null) {
      return projectSyncIssues.getSyncIssues();
    }
    else {
      //noinspection deprecation
      return androidProject.getSyncIssues();
    }
  }

  /**
   * Obtain the selected variant using either the legacy method or from the [VariantGroup]. If no variants are
   * found then this method throws an [AndroidSyncException].
   */
  @VisibleForTesting
  @NotNull
  public static Variant findVariantToSelect(@NotNull AndroidProject androidProject, @Nullable VariantGroup variantGroup) {
    if (variantGroup != null) {
      List<Variant> variants = variantGroup.getVariants();
      if (!variants.isEmpty()) {
        return variants.get(0);
      }
    }

    Variant legacyVariant = findLegacyVariantToSelect(androidProject);
    if (legacyVariant != null) {
      return legacyVariant;
    }

    throw new AndroidSyncException(
      "No variants found for '" + androidProject.getName() + "'. Check build files to ensure at least one variant exists.");
  }

  /**
   * Attempts to find a variant from the [AndroidProject], this is here to support legacy versions of the
   * Android Gradle plugin that don't have the [VariantGroup] model populated. First it tries to find a
   * [Variant] by the name "debug", otherwise returns the first variant found.
   */
  @Nullable
  private static Variant findLegacyVariantToSelect(@NotNull AndroidProject androidProject) {
    Collection<Variant> variants = androidProject.getVariants();
    if (variants.isEmpty()) {
      return null;
    }

    // First attempt to select the "debug" variant if it exists.
    Variant debugVariant = variants.stream().filter(variant -> variant.getName().equals("debug")).findFirst().orElse(null);
    if (debugVariant != null) {
      return debugVariant;
    }

    // Otherwise return the first variant.
    return variants.stream().min(Comparator.comparing(Variant::getName)).orElse(null);
  }
}
