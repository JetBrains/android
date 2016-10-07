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
 * {@link Installer} that creates a patch based on a complete zip of the new component and installs it.
 * The benefit of this is that we get the functionality provided by the IJ patcher (e.g. killing processes that are holding locks on
 * files, including studio itself).
 */
class FullInstaller extends AbstractInstaller implements PatchOperation {

  private static final String UNZIP_DIR_FN = "unzip";

  private LocalPackage myExisting;
  private LocalPackage myPatcher;
  private File myUnzippedPackage;
  private File myGeneratedPatch;

  public FullInstaller(@Nullable LocalPackage existing,
                          @NotNull RemotePackage p,
                          @NotNull RepoManager mgr,
                          @NotNull Downloader downloader,
                          @NotNull FileOp fop) {
    super(p, mgr, downloader, fop);
    myExisting = existing;
    myPatcher = PatchInstallerUtil.getDependantPatcher(getPackage(), getRepoManager());
    if (myPatcher == null) {
      myPatcher = PatchInstallerUtil.getLatestPatcher(getRepoManager());
    }
  }

  @Override
  protected boolean doComplete(@Nullable File installTemp,
                               @NotNull ProgressIndicator progress) {
    if (myPatcher == null) {
      return false;
    }
    return PatchInstallerUtil.installPatch(this, myGeneratedPatch, mFop, progress);
  }

  @Override
  protected boolean doPrepare(@NotNull File installTempPath, @NotNull ProgressIndicator progress) {
    if (!downloadAndUnzip(installTempPath, getDownloader(), progress)) {
      return false;
    }
    myUnzippedPackage = new File(installTempPath, UNZIP_DIR_FN);
    File[] children = mFop.listFiles(myUnzippedPackage);
    if (children.length == 1) {
      // This is the expected case: zips should contain one directory at the top level. But some (e.g. 3rd-party) don't.
      myUnzippedPackage = children[0];
    }

    myGeneratedPatch = PatchInstallerUtil.generatePatch(this, installTempPath, mFop, progress);
    return myGeneratedPatch != null;
  }

  private boolean downloadAndUnzip(@NotNull File installTempPath, @NotNull Downloader downloader, @NotNull ProgressIndicator progress) {
    URL url = InstallerUtil.resolveCompleteArchiveUrl(getPackage(), progress);
    if (url == null) {
      progress.logWarning("No compatible archive found!");
      return false;
    }
    Archive archive = getPackage().getArchive();
    assert archive != null;
    try {
      File downloadLocation = new File(installTempPath, url.getFile());
      String checksum = archive.getComplete().getChecksum();
      downloader.downloadFully(url, downloadLocation, checksum, progress);
      if (progress.isCanceled()) {
        return false;
      }
      if (!mFop.exists(downloadLocation)) {
        progress.logWarning("Failed to download package!");
        return false;
      }
      File unzip = new File(installTempPath, UNZIP_DIR_FN);
      mFop.mkdirs(unzip);
      InstallerUtil.unzip(downloadLocation, unzip, mFop,
                          archive.getComplete().getSize(), progress);
      if (progress.isCanceled()) {
        return false;
      }
      mFop.delete(downloadLocation);

      return true;
    }
    catch (IOException e) {
      StringBuilder message =
        new StringBuilder("An error occurred while preparing SDK package ")
          .append(getPackage().getDisplayName());
      String exceptionMessage = e.getMessage();
      if (exceptionMessage != null && !exceptionMessage.isEmpty()) {
        message.append(": ")
          .append(exceptionMessage);
      }
      else {
        message.append(".");
      }
      progress.logWarning(message.toString(), e);
    }
    return false;
  }

  @Nullable
  @Override
  public LocalPackage getExisting() {
    return myExisting;
  }

  @NotNull
  @Override
  public LocalPackage getPatcher() {
    return myPatcher;
  }

  @NotNull
  @Override
  public File getNewFilesRoot() {
    return myUnzippedPackage;
  }

  @NotNull
  @Override
  public String getNewVersionName() {
    return getPackage().getDisplayName() + " Version " + getPackage().getVersion();
  }
}
