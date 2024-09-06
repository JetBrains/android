/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.projectsystem;

import com.android.manifmerger.ManifestSystemProperty;
import com.android.tools.idea.model.AndroidManifestIndex;
import com.android.tools.idea.model.AndroidManifestIndexCompat;
import com.android.tools.idea.model.AndroidManifestRawText;
import com.android.tools.idea.model.MergedManifestModificationTracker;
import com.android.tools.idea.projectsystem.ManifestOverrides;
import com.android.tools.idea.projectsystem.SourceProviderManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.android.dom.manifest.AndroidManifestUtils;
import org.jetbrains.android.dom.manifest.AndroidManifestXmlFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nullable;

/** Utilities to obtain the package name for a given module. */
public class PackageNameUtils {
  /**
   * Determines whether we use the {@link AndroidManifestIndex} to obtain the raw text package name
   * from a module's primary manifest. Note that we still won't use the index if {@link
   * AndroidManifestIndex#indexEnabled()} returns false.
   *
   * @see PackageNameUtils#getPackageName(Module)
   * @see PackageNameUtils#doGetPackageName(AndroidFacet, boolean)
   */
  private static final BoolExperiment USE_ANDROID_MANIFEST_INDEX =
      new BoolExperiment("use.android.manifest.index", true);

  @Nullable
  public static String getPackageName(Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;
    return CachedValuesManager.getManager(module.getProject())
        .getCachedValue(
            facet,
            () -> {
              boolean useIndex =
                  AndroidManifestIndexCompat.indexEnabled()
                      && USE_ANDROID_MANIFEST_INDEX.getValue();
              String packageName = doGetPackageName(facet, useIndex);
              return CachedValueProvider.Result.create(
                  StringUtil.nullize(packageName, true),
                  MergedManifestModificationTracker.getInstance(module));
            });
  }

  /**
   * Returns the package name from an Android module's merged manifest without actually computing
   * the whole merged manifest. This is either
   *
   * <ol>
   *   <li>The {@link ManifestSystemProperty#PACKAGE} manifest override if one is specified by the
   *       corresponding BUILD target, or
   *   <li>The result of applying placeholder substitution to the raw package name from the module's
   *       primary manifest
   * </ol>
   *
   * In the second case, we try to obtain the raw package name using the {@link
   * AndroidManifestIndex} if {@code useIndex} is true. If {@code useIndex} is false or querying the
   * index fails for some reason (e.g. this method is called in a read action but not a *smart* read
   * action), then we resort to parsing the PSI of the module's primary manifest to get the raw
   * package name.
   *
   * @see AndroidModuleSystem#getManifestOverrides()
   * @see AndroidModuleSystem#getPackageName()
   */
  @Nullable
  @VisibleForTesting
  static String doGetPackageName(AndroidFacet facet, boolean useIndex) {
    ManifestOverrides manifestOverrides =
        BazelModuleSystem.getInstance(facet.getModule()).getManifestOverrides();
    String packageOverride =
        ManifestValueProcessor.getPackageOverride(manifestOverrides.getDirectOverrides());
    if (packageOverride != null) {
      return packageOverride;
    }
    String rawPackageName = null;
    if (useIndex) {
      rawPackageName = getRawPackageNameFromIndex(facet);
    }
    if (rawPackageName == null) {
      rawPackageName = getRawPackageNameFromPsi(facet);
    }
    return rawPackageName == null ? null : manifestOverrides.resolvePlaceholders(rawPackageName);
  }

  @Nullable
  private static String getRawPackageNameFromIndex(AndroidFacet facet) {
    VirtualFile primaryManifest = SourceProviderManager.getInstance(facet).getMainManifestFile();
    if (primaryManifest == null) {
      return null;
    }
    Project project = facet.getModule().getProject();
    try {
      AndroidManifestRawText manifestRawText =
          DumbService.getInstance(project)
              .runReadActionInSmartMode(
                  () -> AndroidManifestIndex.getDataForManifestFile(project, primaryManifest));
      return manifestRawText == null ? null : manifestRawText.getPackageName();
    } catch (IndexNotReadyException e) {
      // TODO(142681129): runReadActionInSmartMode doesn't work if we already have read access.
      //  We need to refactor the callers of AndroidManifestUtils#getPackage to require a *smart*
      //  read action, at which point we can remove this try-catch.
      return null;
    }
  }

  @Nullable
  private static String getRawPackageNameFromPsi(AndroidFacet facet) {
    AndroidManifestXmlFile primaryManifest = AndroidManifestUtils.getPrimaryManifestXml(facet);
    return primaryManifest == null ? null : primaryManifest.getPackageName();
  }

  private PackageNameUtils() {}
}
