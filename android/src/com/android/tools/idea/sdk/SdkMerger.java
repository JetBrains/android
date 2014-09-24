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

import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.tools.idea.rendering.LogWrapper;
import com.android.utils.ILogger;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;

public class SdkMerger {
  private static final Logger LOG = Logger.getInstance(SdkMerger.class);

  public static void mergeSdks(@NotNull File srcDir, @NotNull File destDir, @Nullable ProgressIndicator indicator) {
    Collection<MergeablePackage> packages = compareSdks(srcDir, destDir);
    int numPackages = packages.size();
    int i = 0;
    for (MergeablePackage pkg : packages) {
      if (indicator != null) {
        if (indicator.isCanceled()) {
          return;
        }
        indicator.setFraction((double)i++ / numPackages);
        indicator.setText(String.format("Copying SDK package %1s", pkg.srcPkg.getDesc().getInstallId()));
      }
      if (pkg.destPkg != null) {
        // Destination package exists but is older; delete the old and replace with the new.
        File destPkgDir = pkg.destPkg.getLocalDir();
        try {
          FileUtil.delete(destPkgDir);
        }
        catch (RuntimeException e) {
          LOG.warn("Failed to delete destination directory " + destPkgDir.getPath(), e);
        }
      }
      try {
        FileUtil.copyDir(pkg.srcPkg.getLocalDir(),
                         pkg.srcPkg.getDesc().getCanonicalInstallFolder(pkg.destLocation));
      }
      catch (IOException e) {
        LOG.error("Unable to copy package " + pkg.srcPkg.getDesc().getInstallId(), e);
      }
    }
    if (indicator != null) {
      indicator.setFraction(1.0);
    }
  }

  public static boolean hasMergeableContent(@NotNull File srcDir, @NotNull File destDir) {
    return !compareSdks(srcDir, destDir).isEmpty();
  }

  @NotNull
  private static Collection<MergeablePackage> compareSdks(@NotNull File srcDir, @NotNull File destDir) {
    Collection<MergeablePackage> results = Lists.newArrayList();

    ILogger logger = new LogWrapper(LOG);
    SdkManager destManager = SdkManager.createManager(destDir.getPath(), logger);
    SdkManager srcManager = SdkManager.createManager(srcDir.getPath(), logger);
    if (srcManager == null || destManager == null) {
      return results;
    }
    LocalSdk srcSdk = srcManager.getLocalSdk();
    LocalSdk destSdk = destManager.getLocalSdk();
    LocalPkgInfo[] srcPkgs = srcSdk.getPkgsInfos(EnumSet.allOf(PkgType.class));

    File destLocation = destSdk.getLocation();
    if (destLocation == null) {
      return results;
    }

    Map<String, LocalPkgInfo> destPackages = Maps.newHashMap();
    for (LocalPkgInfo pkg : destSdk.getPkgsInfos(EnumSet.allOf(PkgType.class))) {
      destPackages.put(pkg.getDesc().getInstallId(), pkg);
    }

    for (LocalPkgInfo srcPkg : srcPkgs) {
      IPkgDesc srcPkgDesc = srcPkg.getDesc();
      LocalPkgInfo destPkg = destPackages.get(srcPkg.getDesc().getInstallId());
      if (destPkg != null) {
        IPkgDesc destPkgDesc = destPkg.getDesc();
        FullRevision srcFullRevision = srcPkgDesc.getFullRevision();
        FullRevision destFullRevision = destPkgDesc.getFullRevision();
        MajorRevision srcMajorRevision = srcPkgDesc.getMajorRevision();
        MajorRevision destMajorRevision = destPkgDesc.getMajorRevision();
        if ((srcFullRevision != null && destFullRevision != null && srcFullRevision.compareTo(destFullRevision) > 0) ||
            (srcMajorRevision != null && destMajorRevision != null && srcMajorRevision.compareTo(destMajorRevision) > 0)) {
          // Package exists in destination but is old; replace it.
          results.add(new MergeablePackage(srcPkg, destPkg, destLocation));
        }
      } else {
        // Package doesn't exist in destination; copy it over.
        results.add(new MergeablePackage(srcPkg, null, destLocation));
      }
    }
    return results;
  }

  private static class MergeablePackage {
    @NotNull private final LocalPkgInfo srcPkg;
    @Nullable private final LocalPkgInfo destPkg;
    @NotNull private final File destLocation;

    private MergeablePackage(@NotNull LocalPkgInfo srcPkg, @Nullable LocalPkgInfo destPkg, @NotNull File destLocation) {
      this.srcPkg = srcPkg;
      this.destPkg = destPkg;
      this.destLocation = destLocation;
    }
  }
}
