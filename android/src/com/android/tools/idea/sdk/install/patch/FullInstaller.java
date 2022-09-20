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

import com.android.io.CancellableFileIo;
import com.android.repository.api.Downloader;
import com.android.repository.api.Installer;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.installer.AbstractInstaller;
import com.android.repository.impl.meta.Archive;
import com.android.repository.util.InstallerUtil;
import com.android.utils.PathUtils;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link Installer} that creates a patch based on a complete zip of the new component and installs it.
 * The benefit of this is that we get the functionality provided by the IJ patcher (e.g. killing processes that are holding locks on
 * files, including studio itself).
 */
class FullInstaller extends AbstractInstaller implements PatchOperation {

  private static final String UNZIP_DIR_FN = "unzip";

  private final LocalPackage myExisting;
  private LocalPackage myPatcher;
  private Path myUnzippedPackage;
  private Path myGeneratedPatch;

  public FullInstaller(@Nullable LocalPackage existing,
                       @NotNull RemotePackage p,
                       @NotNull RepoManager mgr,
                       @NotNull Downloader downloader) {
    super(p, mgr, downloader);
    myExisting = existing;
    myPatcher = PatchInstallerUtil.getDependantPatcher(getPackage(), getRepoManager());
    if (myPatcher == null) {
      myPatcher = PatchInstallerUtil.getLatestPatcher(getRepoManager());
    }
  }

  @Override
  protected boolean doComplete(@Nullable Path installTemp,
                               @NotNull ProgressIndicator progress) {
    if (myPatcher == null) {
      return false;
    }
    return PatchInstallerUtil.installPatch(this, myGeneratedPatch, progress);
  }

  @Override
  protected boolean doPrepare(@NotNull Path installTempPath, @NotNull ProgressIndicator progress) {
    if (!downloadAndUnzip(installTempPath, getDownloader(), progress.createSubProgress(0.5))) {
      progress.setFraction(1);
      return false;
    }
    myUnzippedPackage = installTempPath.resolve(UNZIP_DIR_FN);
    try (Stream<Path> childrenStream = CancellableFileIo.list(myUnzippedPackage)) {
      List<Path> children = childrenStream.limit(2).collect(Collectors.toList());
      if (children.size() == 1) {
        myUnzippedPackage = children.get(0);
      }
    }
    catch (IOException ignore) {
      // fall through
    }

    myGeneratedPatch = PatchInstallerUtil.generatePatch(this, installTempPath, progress.createSubProgress(1));
    progress.setFraction(1);
    return myGeneratedPatch != null;
  }

  private boolean downloadAndUnzip(@NotNull Path installTempPath, @NotNull Downloader downloader, @NotNull ProgressIndicator progress) {
    URL url = InstallerUtil.resolveCompleteArchiveUrl(getPackage(), progress);
    if (url == null) {
      progress.logWarning("No compatible archive found!");
      return false;
    }
    Archive archive = getPackage().getArchive();
    assert archive != null;
    try {
      Path downloadLocation = installTempPath.resolve(url.getFile());
      downloader.downloadFullyWithCaching(url, downloadLocation, archive.getComplete().getTypedChecksum(), progress.createSubProgress(0.5));
      progress.setFraction(0.5);
      if (progress.isCanceled()) {
        progress.setFraction(1);
        return false;
      }
      if (!CancellableFileIo.exists(downloadLocation)) {
        progress.setFraction(1);
        progress.logWarning("Failed to download package!");
        return false;
      }
      Path unzip = installTempPath.resolve(UNZIP_DIR_FN);
      Files.createDirectories(unzip);
      InstallerUtil.unzip(downloadLocation, unzip,
                          archive.getComplete().getSize(), progress.createSubProgress(1));
      progress.setFraction(1);
      if (progress.isCanceled()) {
        return false;
      }
      try {
        PathUtils.deleteRecursivelyIfExists(downloadLocation);
      }
      catch (IOException ignore) {}

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
    progress.setFraction(1);
    return false;
  }

  @Nullable
  @Override
  public LocalPackage getExisting() {
    return myExisting;
  }

  @NotNull
  @Override
  public LocalPackage getPatcher(@NotNull ProgressIndicator progressIndicator) {
    return myPatcher;
  }

  @NotNull
  @Override
  public Path getNewFilesRoot() {
    return myUnzippedPackage;
  }

  @NotNull
  @Override
  public String getNewVersionName() {
    return getPackage().getDisplayName() + " Version " + getPackage().getVersion();
  }

  @Override
  protected void cleanup(@NotNull ProgressIndicator progress) {
    super.cleanup(progress);
    try {
      PathUtils.deleteRecursivelyIfExists(getLocation(progress));
    }
    catch (IOException ignore) {}
    if (myUnzippedPackage != null) {
      try {
        PathUtils.deleteRecursivelyIfExists(myUnzippedPackage);
      }
      catch (IOException ignore) {}
    }
  }
}
