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

import com.android.build.OutputFile;
import com.android.builder.model.Variant;
import com.android.ddmlib.IDevice;
import com.android.ide.common.build.SplitOutputMatcher;
import com.google.common.base.Joiner;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

class BestOutputFinder {
  @NotNull
  List<OutputFile> findBestOutput(@NotNull Variant variant, @NotNull IDevice device, @NotNull List<? extends OutputFile> outputs)
    throws ApkProvisionException {
    Set<String> variantAbiFilters = variant.getMainArtifact().getAbiFilters();
    int density = device.getDensity();
    List<String> abis = device.getAbis();

    List<OutputFile> apkFiles = SplitOutputMatcher.computeBestOutput(outputs, variantAbiFilters, density, abis);
    if (apkFiles.isEmpty()) {
      String message = AndroidBundle.message("deployment.failed.splitapk.nomatch",
                                             variant.getDisplayName(),
                                             outputs.size(),
                                             density,
                                             Joiner.on(", ").join(abis));
      throw new ApkProvisionException(message);
    }
    return apkFiles;
  }
}
