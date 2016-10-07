/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.sdk.install.patch;

import com.android.repository.api.*;
import com.android.repository.impl.installer.AbstractInstaller;
import com.android.repository.impl.meta.Archive;
import com.android.repository.io.FileOp;
import com.android.repository.util.InstallerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * Installer for binary diff packages, as built by {@code com.intellij.updater.Runner}.
 */
class PatchInstaller extends AbstractInstaller implements PatchOperation {

  private LocalPackage myExisting;
  private File myPatchFile;

  public PatchInstaller(@Nullable LocalPackage existing,
                        @NotNull RemotePackage p,
                        @NotNull Downloader downloader,
                        @NotNull RepoManager mgr,
                        @NotNull FileOp fop) {
    super(p, mgr, downloader, fop);
    myExisting = existing;
  }

  @Override
  protected boolean doComplete(@Nullable File installTemp,
                               @NotNull ProgressIndicator progress) {
    return PatchInstallerUtil.installPatch(this, myPatchFile, mFop, progress);
  }

  @Override
  protected boolean doPrepare(@NotNull File tempDir,
                              @NotNull ProgressIndicator progress) {
    LocalPackage local = getRepoManager().getPackages().getLocalPackages().get(getPackage().getPath());
    Archive archive = getPackage().getArchive();
    assert archive != null;

    Archive.PatchType patch = archive.getPatch(local.getVersion());
    assert patch != null;

    myPatchFile = downloadPatchFile(patch, tempDir, progress);
    if (myPatchFile == null) {
      progress.logWarning("Patch failed to download.");
      return false;
    }
    return true;
  }

  @Nullable
  @Override
  public LocalPackage getExisting() {
    return myExisting;
  }

  @NotNull
  @Override
  public LocalPackage getPatcher() {
    LocalPackage dependantPatcher = PatchInstallerUtil.getDependantPatcher(getPackage(), getRepoManager());
    assert dependantPatcher != null : "Shouldn't be creating a PatchInstaller with no patcher";
    return dependantPatcher;
  }

  @NotNull
  @Override
  public File getNewFilesRoot() {
    // PatchInstaller doesn't need to generate a patch on the fly, so it doesn't have or need this information.
    throw new UnsupportedOperationException("PatchInstaller can't generate patches");
  }

  @NotNull
  @Override
  public String getNewVersionName() {
    return getPackage().getDisplayName() + " Version " + getPackage().getVersion();
  }

  /**
   * Resolves and downloads the patch for the given {@link Archive.PatchType}.
   *
   * @return {@code null} if unsuccessful.
   */
  @Nullable
  private File downloadPatchFile(@NotNull Archive.PatchType patch,
                                 @NotNull File tempDir,
                                 @NotNull ProgressIndicator progress) {
    URL url = InstallerUtil.resolveUrl(patch.getUrl(), getPackage(), progress);
    if (url == null) {
      progress.logWarning("Failed to resolve URL: " + patch.getUrl());
      return null;
    }
    try {
      File patchFile = new File(tempDir, "patch.jar");
      getDownloader().downloadFully(url, patchFile, patch.getChecksum(), progress);
      return patchFile;
    }
    catch (IOException e) {
      progress.logWarning("Error during downloading", e);
      return null;
    }
  }
}
