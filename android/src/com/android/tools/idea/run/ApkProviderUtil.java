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
package com.android.tools.idea.run;

import com.android.SdkConstants;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * Common static utilities used by the ApkProvider implementations.
 * TODO: The Gradle and non-Gradle logic here should be split and inlined into its respective provider implementations.
 */
public class ApkProviderUtil {
  private static final Logger LOG = Logger.getInstance(ApkProviderUtil.class);

  /**
   * DO NOT USE! Use `ApplicationIdProvider`s instead.
   */
  @Deprecated
  @NotNull
  public static String computePackageName(@NotNull final AndroidFacet facet) throws ApkProvisionException {
    // TODO: Separate Gradle and non-Gradle logic here.
    if (facet.getProperties().USE_CUSTOM_MANIFEST_PACKAGE) {
      return facet.getProperties().CUSTOM_MANIFEST_PACKAGE;
    }
    else {
      String pkg = AndroidModuleInfo.getInstance(facet).getPackage();
      if (pkg == null || pkg.isEmpty()) {
        throw new ApkProvisionException("[" + facet.getModule().getName() + "] Unable to obtain main package from manifest.");
      }
      return pkg;
    }
  }

  @Nullable
  public static Pair<File, String> getCopyOfCompilerManifestFile(@NotNull AndroidFacet facet) throws IOException {
    final VirtualFile manifestFile = AndroidRootUtil.getCustomManifestFileForCompiler(facet);

    if (manifestFile == null) {
      return null;
    }
    File tmpDir = null;
    try {
      tmpDir = FileUtil.createTempDirectory("android_manifest_file_for_execution", "tmp");
      final File manifestCopy = new File(tmpDir, manifestFile.getName());
      FileUtil.copy(new File(manifestFile.getPath()), manifestCopy);
      //noinspection ConstantConditions
      return Pair.create(manifestCopy, PathUtil.getLocalPath(manifestFile));
    }
    catch (IOException e) {
      LOG.info(e);
      if (tmpDir != null) {
        FileUtil.delete(tmpDir);
      }
      throw e;
    }
  }
}
