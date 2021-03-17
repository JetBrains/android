/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.ide.common.build.GenericBuiltArtifacts;
import com.android.ide.common.build.GenericBuiltArtifactsSplitOutputMatcher;
import com.android.ide.common.gradle.model.IdeAndroidArtifactOutput;
import com.android.ide.common.gradle.model.IdeVariant;
import com.google.common.base.Joiner;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.util.List;
import java.util.Set;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

/**
 * Find the best output for the selected device and variant when multiple splits are available.
 */
class BestOutputFinder {
  @NotNull
  File findBestOutput(@NotNull IdeVariant variant,
                      @NotNull List<String> abis,
                      @NotNull List<IdeAndroidArtifactOutput> outputs) throws ApkProvisionException {
    return findBestOutput(variant.getDisplayName(), variant.getMainArtifact().getAbiFilters(), abis, outputs);
  }

  @NotNull
  File findBestOutput(@NotNull String variantDisplayName,
                      @NotNull Set<String> artifactAbiFilters,
                      @NotNull List<String> abis,
                      @NotNull List<IdeAndroidArtifactOutput> outputs) throws ApkProvisionException {
    if (outputs.isEmpty()) {
      throw new ApkProvisionException("No outputs for the main artifact of variant: " + variantDisplayName);
    }
    List<File> apkFiles =
      ContainerUtil.map(SplitOutputMatcher.computeBestOutput(outputs, artifactAbiFilters, abis), IdeAndroidArtifactOutput::getOutputFile);
    verifyApkCollectionIsNotEmpty(apkFiles, variantDisplayName, abis, outputs.size());
    // Install apk (note that variant.getOutputFile() will point to a .aar in the case of a library).
    return apkFiles.get(0);
  }

  @NotNull
  File findBestOutput(@NotNull IdeVariant variant, @NotNull List<String> abis, @NotNull GenericBuiltArtifacts builtArtifact)
    throws ApkProvisionException {
    return findBestOutput(variant.getDisplayName(), variant.getMainArtifact().getAbiFilters(), abis, builtArtifact);
  }

  @NotNull
  File findBestOutput(@NotNull String variantDisplayName,
                      @NotNull Set<String> artifactAbiFilters,
                      @NotNull List<String> abis,
                      @NotNull GenericBuiltArtifacts builtArtifact) throws ApkProvisionException {
    List<File> apkFiles = GenericBuiltArtifactsSplitOutputMatcher.INSTANCE.computeBestOutput(builtArtifact, artifactAbiFilters, abis);
    verifyApkCollectionIsNotEmpty(apkFiles, variantDisplayName, abis, builtArtifact.getElements().size());
    // Install apk (note that variant.getOutputFile() will point to a .aar in the case of a library).
    return apkFiles.get(0);
  }

  private static void verifyApkCollectionIsNotEmpty(@NotNull List<File> apkFiles,
                                                    @NotNull String variantDisplayName,
                                                    @NotNull List<String> abis,
                                                    int outputCount)
    throws ApkProvisionException {
    if (apkFiles.isEmpty()) {
      String message = AndroidBundle.message("deployment.failed.splitapk.nomatch",
                                             variantDisplayName,
                                             outputCount,
                                             Joiner.on(", ").join(abis));
      throw new ApkProvisionException(message);
    }
  }
}
