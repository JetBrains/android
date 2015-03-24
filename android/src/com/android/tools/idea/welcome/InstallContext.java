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
package com.android.tools.idea.welcome;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.download.DownloadableFileDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Keeps installation process state.
 */
public class InstallContext {
  private final File myTempDirectory;
  @NotNull private final List<DownloadableFileDescription> myDescriptions;
  @NotNull private final ProgressStep myProgressStep;
  private final Map<DownloadableFileDescription, File> myDownloadLocations = Maps.newHashMap();
  private Map<DownloadableFileDescription, File> myExpandedLocations = Maps.newHashMap();

  public InstallContext(@NotNull File tempDirectory, @NotNull Iterable<DownloadableFileDescription> descriptions,
                        @NotNull ProgressStep progressStep) {
    myTempDirectory = tempDirectory;
    myDescriptions = ImmutableList.copyOf(descriptions);
    myProgressStep = progressStep;
  }

  public File getExpandedLocation(DownloadableFileDescription downloadableFile) {
    return myExpandedLocations.get(downloadableFile);
  }

  @NotNull
  public ProgressStep getProgressStep() {
    return myProgressStep;
  }

  public List<DownloadableFileDescription> getFilesToDownload() {
    return myDescriptions;
  }

  public void setDownloadedLocation(DownloadableFileDescription downloadableFile, File destination) {
    myDownloadLocations.put(downloadableFile, destination);
  }

  public File getTempDirectory() {
    return myTempDirectory;
  }

  public void cleanup(ProgressStep step) {
    for (File file : Iterables.concat(myDownloadLocations.values(), myExpandedLocations.values())) {
      if (!FileUtil.delete(file)) {
        step.print(String.format("Can't delete %s\n", file.getAbsolutePath()), ConsoleViewContentType.ERROR_OUTPUT);
      }
    }
  }

  public Collection<File> getDownloadedFiles() {
    return myDownloadLocations.values();
  }

  @Nullable
  public File getDownloadLocation(DownloadableFileDescription description) {
    return myDownloadLocations.get(description);
  }

  public void setExpandedLocation(DownloadableFileDescription description, File dir) {
    myExpandedLocations.put(description, dir);
  }
}
