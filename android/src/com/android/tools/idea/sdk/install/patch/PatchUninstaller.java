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

import com.android.annotations.NonNull;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.installer.AbstractUninstaller;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Generates a patch that deletes all the files in the given package.
 */
class PatchUninstaller extends AbstractUninstaller implements PatchOperation {
  private LocalPackage myPatcher;
  private File myEmptyDir;
  private File myGeneratedPatch;

  public PatchUninstaller(@NotNull LocalPackage p, @NotNull RepoManager mgr, @NotNull FileOp fop) {
    super(p, mgr, fop);
    myPatcher = PatchInstallerUtil.getLatestPatcher(getRepoManager());
    myEmptyDir = FileOpUtils.getNewTempDir("PatchUninstaller", mFop);
    registerStateChangeListener((op, progress) -> {
      if (getInstallStatus() == InstallStatus.COMPLETE) {
        mFop.deleteFileOrFolder(getLocation(progress));
      }
    });
  }

  @NotNull
  @Override
  public LocalPackage getPatcher() {
    return myPatcher;
  }

  @NotNull
  @Override
  public File getNewFilesRoot() {
    return myEmptyDir;
  }

  @NotNull
  @Override
  public String getNewVersionName() {
    return "<None>";
  }

  @Override
  @Nullable
  public LocalPackage getExisting() {
    return getPackage();
  }

  @Override
  protected boolean doPrepare(@Nullable File installTemp,
                              @NonNull ProgressIndicator progress) {
    if (myPatcher == null) {
      return false;
    }
    myGeneratedPatch = PatchInstallerUtil.generatePatch(this, installTemp, mFop, progress);
    return myGeneratedPatch != null;
  }

  @Override
  protected boolean doComplete(@Nullable File installTemp,
                               @NotNull ProgressIndicator progress) {
    return PatchInstallerUtil.installPatch(this, myGeneratedPatch, mFop, progress);
  }
}
