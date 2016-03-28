/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.sdk;

import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepoPackage;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.tools.idea.sdk.progress.RepoProgressIndicatorAdapter;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public class SdkMerger {
  private static final Logger LOG = Logger.getInstance(SdkMerger.class);

  public static void mergeSdks(@NotNull File srcDir, @NotNull File destDir, @Nullable ProgressIndicator indicator) {
    Collection<MergeablePackage> packages = compareSdks(srcDir, destDir, indicator);
    int numPackages = packages.size();
    int i = 0;
    for (MergeablePackage pkg : packages) {
      if (indicator != null) {
        if (indicator.isCanceled()) {
          return;
        }
        indicator.setFraction((double)i++ / numPackages);
        indicator.setText(String.format("Copying SDK package %1s", pkg.srcPkg.getPath()));
      }
      if (pkg.destPkg != null) {
        // Destination package exists but is older; delete the old and replace with the new.
        File destPkgDir = pkg.destPkg.getLocation();
        try {
          FileUtil.delete(destPkgDir);
        }
        catch (RuntimeException e) {
          LOG.warn("Failed to delete destination directory " + destPkgDir.getPath(), e);
        }
      }
      try {
        FileUtil.copyDir(pkg.srcPkg.getLocation(),
                         new File(pkg.destLocation, pkg.srcPkg.getPath().replace(RepoPackage.PATH_SEPARATOR, File.separatorChar)));
      }
      catch (IOException e) {
        LOG.error("Unable to copy package " + pkg.srcPkg.getPath(), e);
      }
    }
    if (indicator != null) {
      indicator.setFraction(1.0);
    }

    // Dest dir is changed, refresh.
    com.android.repository.api.ProgressIndicator repoProgress = getRepoProgress(indicator);
    AndroidSdkHandler.getInstance(destDir).getSdkManager(repoProgress).loadSynchronously(0, repoProgress, null, null);
  }

  @NotNull
  public static com.android.repository.api.ProgressIndicator getRepoProgress(@Nullable ProgressIndicator indicator) {
    com.android.repository.api.ProgressIndicator repoProgress;
    if (indicator == null) {
      repoProgress = new StudioLoggerProgressIndicator(SdkMerger.class);
    }
    else {
      repoProgress = new RepoProgressIndicatorAdapter(indicator);
    }
    return repoProgress;
  }

  public static boolean hasMergeableContent(@NotNull File srcDir, @NotNull File destDir) {
    return !compareSdks(srcDir, destDir, null).isEmpty();
  }

  @NotNull
  private static Collection<MergeablePackage> compareSdks(@NotNull File srcDir,
                                                          @NotNull File destDir,
                                                          @Nullable ProgressIndicator progress) {
    com.android.repository.api.ProgressIndicator repoProgress = getRepoProgress(progress);
    Collection<MergeablePackage> results = Lists.newArrayList();

    AndroidSdkHandler srcHandler = AndroidSdkHandler.getInstance(srcDir);
    AndroidSdkHandler destHandler = AndroidSdkHandler.getInstance(destDir);


    Map<String, ? extends LocalPackage> srcPackages = srcHandler.getSdkManager(repoProgress).getPackages().getLocalPackages();
    Map<String, ? extends LocalPackage> destPackages = destHandler.getSdkManager(repoProgress).getPackages().getLocalPackages();

    for (LocalPackage srcPkg : srcPackages.values()) {
      LocalPackage destPkg = destPackages.get(srcPkg.getPath());
      if (destPkg != null) {
        if (srcPkg.getVersion().compareTo(destPkg.getVersion()) > 0) {
          // Package exists in destination but is old; replace it.
          results.add(new MergeablePackage(srcPkg, destPkg, destDir));
        }
      } else {
        // Package doesn't exist in destination; copy it over.
        results.add(new MergeablePackage(srcPkg, null, destDir));
      }
    }
    return results;
  }

  private static class MergeablePackage {
    @NotNull private final LocalPackage srcPkg;
    @Nullable private final LocalPackage destPkg;
    @NotNull private final File destLocation;

    private MergeablePackage(@NotNull LocalPackage srcPkg, @Nullable LocalPackage destPkg, @NotNull File destLocation) {
      this.srcPkg = srcPkg;
      this.destPkg = destPkg;
      this.destLocation = destLocation;
    }
  }
}
