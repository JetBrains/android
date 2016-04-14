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
package com.android.tools.idea.run.activity;

import com.android.SdkConstants;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ApkProviderUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class MavenDefaultActivityLocator extends ActivityLocator {
  private final AndroidFacet myFacet;

  public MavenDefaultActivityLocator(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  @Override
  public void validate() throws ActivityLocatorException {
    // We can't validate anything before having the artifacts from the build,
    // so nothing much to do here..
  }

  @NotNull
  @Override
  public String getQualifiedActivityName(@NotNull IDevice device) throws ActivityLocatorException {
    File manifestCopy = null;
    try {
      Pair<File, String> pair;
      try {
        pair = ApkProviderUtil.getCopyOfCompilerManifestFile(myFacet);
      }
      catch (IOException e) {
        throw new ActivityLocatorException("Error while obtaining compiler manifest file", e);
      }
      manifestCopy = pair != null ? pair.getFirst() : null;
      VirtualFile manifestVFile = manifestCopy != null ? LocalFileSystem.getInstance().findFileByIoFile(manifestCopy) : null;
      final Manifest manifest =
        manifestVFile == null ? null : AndroidUtils.loadDomElement(myFacet.getModule(), manifestVFile, Manifest.class);
      if (manifest == null) {
        throw new ActivityLocatorException("Cannot find " + SdkConstants.FN_ANDROID_MANIFEST_XML + " file");
      }
      return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Nullable
        @Override
        public String compute() {
          return DefaultActivityLocator.getDefaultLauncherActivityName(myFacet.getModule().getProject(), manifest);
        }
      });
    }
    finally {
      if (manifestCopy != null) {
        FileUtil.delete(manifestCopy.getParentFile());
      }
    }
  }
}
