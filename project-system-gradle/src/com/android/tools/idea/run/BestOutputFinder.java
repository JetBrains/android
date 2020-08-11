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
import com.android.ide.common.build.SplitOutputMatcher;
import com.android.ide.common.gradle.model.IdeAndroidArtifactOutput;
import com.android.ide.common.gradle.model.IdeVariant;
import com.google.common.base.Joiner;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Find the best output for the selected device and variant when multiple splits are available.
 */
class BestOutputFinder {
  @NotNull
  File findBestOutput(@NotNull IdeVariant variant,
                      @NotNull List<String> abis,
                      @NotNull List<IdeAndroidArtifactOutput> outputs)
    throws ApkProvisionException {
    if (outputs.isEmpty()) {
      throw new ApkProvisionException("No outputs for the main artifact of variant: " + variant.getDisplayName());
    }
    return doFindBestOutput(variant, abis, outputs, null);
  }

  @NotNull
  File findBestOutput(@NotNull IdeVariant variant, @NotNull List<String> abis, @NotNull GenericBuiltArtifacts builtArtifact)
    throws ApkProvisionException {
    return doFindBestOutput(variant, abis, null, builtArtifact);
  }

  @NotNull
  private static File doFindBestOutput(@NotNull IdeVariant variant,
                                       @NotNull List<String> abis,
                                       @Nullable List<IdeAndroidArtifactOutput> outputs,
                                       @Nullable GenericBuiltArtifacts builtArtifact)
    throws ApkProvisionException {
    Set<String> variantAbiFilters = variant.getMainArtifact().getAbiFilters();
    List<File> apkFiles = new ArrayList<>();
    int outputCount = 0;
    if (outputs != null) {
      apkFiles =
        ContainerUtil.map(SplitOutputMatcher.computeBestOutput(outputs, variantAbiFilters, abis), IdeAndroidArtifactOutput::getOutputFile);
      outputCount = outputs.size();
    }
    if (builtArtifact != null) {
      apkFiles = GenericBuiltArtifactsSplitOutputMatcher.INSTANCE.computeBestOutput(builtArtifact, variantAbiFilters, abis);
      outputCount = builtArtifact.getElements().size();
    }
    if (apkFiles.isEmpty()) {
      String message = AndroidBundle.message("deployment.failed.splitapk.nomatch",
                                             variant.getDisplayName(),
                                             outputCount,
                                             Joiner.on(", ").join(abis));
      throw new ApkProvisionException(message);
    }
    // Install apk (note that variant.getOutputFile() will point to a .aar in the case of a library).
    return apkFiles.get(0);
  }
}
