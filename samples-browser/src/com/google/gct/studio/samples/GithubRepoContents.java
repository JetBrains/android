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
package com.google.gct.studio.samples;

import com.google.common.collect.Lists;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.templates.github.DownloadUtil;
import com.intellij.platform.templates.github.Outcome;
import com.intellij.platform.templates.github.ZipUtil;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_BUILD_GRADLE_KTS;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE_KTS;

/**
 * Contents fetched from a Github Repo.
 */
public final class GithubRepoContents {

  private static final long CACHE_TIMEOUT_MS = 60 * 60 * 1000;

  /**
   * Download a Github repository and search it for templates and Android Studio projects. Returns 2 lists of files, one pointing
   * to directories that contain templates and the other to directories that contain projects.
   *
   * @param url            The github URL to retrieve
   * @param branch         The name of the branch to retrieve. If not specified, it is assumed to be "master"
   * @param cacheDirectory An optional location to cache the downloaded repository. If not specified it will default to a working
   *                       directory inside the operating system's temporary folder (e.g. /tmp)
   * @return A GithubRepoContents instance. If the download failed, then the errorMessage member will be set. If the errorMessage
   * is null, then both templateFolders and sampleRoots will NOT be null, but MAY be empty.
   */
  @NotNull
  public static GithubRepoContents download(@NotNull String url,
                                            @Nullable String branch,
                                            @Nullable File cacheDirectory) {
    GithubRepoContents returnValue = new GithubRepoContents();
    if (cacheDirectory == null) {
      cacheDirectory = new File(FileUtil.getTempDirectory(), "github_cache");
    }
    if (branch == null || branch.trim().isEmpty()) {
      branch = "HEAD";
    }

    URL parsedUrl;
    try {
      parsedUrl = new URL(url);
    }
    catch (MalformedURLException e) {
      returnValue.errorMessage = "Malformed URL";
      return returnValue;
    }

    String repositoryName = "Github" + parsedUrl.getPath().replace('/', '-');
    final File outputFile = new File(cacheDirectory, repositoryName + ".zip");
    final String finalUrl = url + "/zipball/" + branch;
    File unzippedDir = new File(cacheDirectory, repositoryName);

    // TODO: Do some smarter caching here
    if (!unzippedDir.exists() || unzippedDir.lastModified() == 0 ||
        (System.currentTimeMillis() - unzippedDir.lastModified()) > CACHE_TIMEOUT_MS) {
      FileUtil.delete(unzippedDir);

      try {
        Outcome<File> outcome = DownloadUtil.provideDataWithProgressSynchronously(
          null,
          "Downloading project from GitHub",
          "Downloading zip archive" + DownloadUtil.CONTENT_LENGTH_TEMPLATE + " ...",
          () -> {
            ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
            DownloadUtil.downloadAtomically(progress, finalUrl, outputFile);
            return outputFile;
          },
          null
        );

        Exception e = outcome.getException();
        if (e != null) {
          throw e;
        }
        if (outcome.isCancelled()) {
          returnValue.cancelled = true;
          return returnValue;
        }

        ProgressManager.getInstance().runProcessWithProgressSynchronously(
          () -> {
            try {
              ZipUtil.unzip(ProgressManager.getInstance().getProgressIndicator(), unzippedDir, outputFile, null, null, true);
            }
            catch (IOException unzipE) {
              returnValue.errorMessage = "Could not unzip specified project from Github.\n\n" + unzipE.getMessage();
            }
          },
          "Unzipping sample...", false, null, null
        );
      }
      catch (Exception e) {
        returnValue.errorMessage = "Could not download specified project from Github. Check the URL and branch name.\n\n" + e.getMessage();

        return returnValue;
      }
      outputFile.delete();
    }

    returnValue.rootFolder = unzippedDir;
    returnValue.sampleRoots = findSamplesInDirectory(unzippedDir, true);
    return returnValue;
  }

  /**
   * Returns a list of files which point to the roots of projects/modules within the given folder.
   * If the recursive parameter is not set, we will only check to see if the given directory is a gradle based project and return
   * a singleton list. Otherwise, we recurse down, finding all project/module roots (folders containing a build.gradle file) and return
   * them as a list.
   *
   * @param directory The root directory to check
   * @param recursive Whether we should look several levels down.
   * @return A list of root directories of projects and/or modules
   */
  @NotNull
  private static List<File> findSamplesInDirectory(@NotNull File directory, boolean recursive) {
    List<File> samples = Lists.newArrayList();
    if (new File(directory, FN_BUILD_GRADLE).exists() || new File(directory, FN_SETTINGS_GRADLE).exists()
        || new File(directory, FN_BUILD_GRADLE_KTS).exists() || new File(directory, FN_SETTINGS_GRADLE_KTS).exists()) {
      samples.add(directory);
    }
    if (recursive) {
      File[] files = directory.listFiles();
      if (files != null) {
        for (File file : files) {
          if (file.isDirectory()) {
            samples.addAll(findSamplesInDirectory(file, true));
          }
        }
      }
    }
    return samples;
  }

  private File rootFolder;
  private List<File> sampleRoots;
  private String errorMessage;
  private boolean cancelled = false;

  /**
   * Use {@link #download(String, String, File)} instead.
   */
  private GithubRepoContents() {
  }

  /**
   * @return any error message encountered during {@link #download(String, String, File)}. This will be {@code null} if no error
   * occurred, and it should be checked before calling any of the other getters.
   */
  @Nullable
  public String getErrorMessage() {
    return errorMessage;
  }

  /**
   * @return the root folder that all samples were downloaded into.
   */
  public File getRootFolder() {
    return rootFolder;
  }

  /**
   * @return a list of top-level directories that contain sample code. These will all be under the root folder returned by
   * {@link #getRootFolder()}
   */
  public List<File> getSampleRoots() {
    return sampleRoots;
  }

  public boolean isCancelled() {
    return cancelled;
  }
}
