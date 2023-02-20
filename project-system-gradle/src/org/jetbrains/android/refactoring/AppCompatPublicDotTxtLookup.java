/*
 * Copyright (C) 2017 The Android Open Source Project
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
package org.jetbrains.android.refactoring;

import com.android.tools.sdk.AndroidSdkData;
import com.google.common.annotations.VisibleForTesting;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager;
import com.android.tools.idea.ui.GuiTestingService;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.PathUtil;
import com.intellij.util.net.HttpConfigurable;
import java.nio.file.FileSystems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

import static com.android.SdkConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Fetch and parse the 'public.txt' from appcompat-v7 for a given appCompat version
 * 1. First check to see if the aar exists locally.
 * 2. If not, check the cache for an existing public.txt for the version
 * 3. If not, fetch the aar for the specific version from maven.google.com, store the public.txt in the cache
 *    and return the parsed version of public.txt.
 */
public class AppCompatPublicDotTxtLookup {

  private static final String BASE_URL =
    "https://maven.google.com/com/android/support/appcompat-v7/%1$s/appcompat-v7-%2$s.aar";
  private static final String ANDROIDX_BASE_URL =
    "https://maven.google.com/androidx/appcompat/appcompat/%1$s/appcompat-%2$s.aar";
  private static final Splitter PUBLIC_TXT_SPLITTER = Splitter.on(' ').trimResults();
  private static final String RELATIVE_URI_PATH = "com/android/support/appcompat-v7/%1$s/" + FN_PUBLIC_TXT;
  private static final String ANDROIDX_RELATIVE_URI_PATH = "androidx/appcompat/appcompat/%1$s/" + FN_PUBLIC_TXT;
  private static final String RELATIVE_FILE_PATH = RELATIVE_URI_PATH.replace('/', File.separatorChar);
  private static final String ANDROIDX_RELATIVE_FILE_PATH = ANDROIDX_RELATIVE_URI_PATH.replace('/', File.separatorChar);


  private static final class LookupCreator {
    static final AppCompatPublicDotTxtLookup INSTANCE = new AppCompatPublicDotTxtLookup();
  }

  static AppCompatPublicDotTxtLookup getInstance() {
    return LookupCreator.INSTANCE;
  }

  @VisibleForTesting
  AppCompatPublicDotTxtLookup() {
  }

  AppCompatStyleMigration createAppCompatStyleMigration(@NotNull GoogleMavenArtifactId artifactId, @NotNull String appCompatVersion) {
    Set<String> attrSet = Sets.newHashSetWithExpectedSize(250); // 25.1.1 has 198 entries for attr
    Set<String> styleSet = Sets.newHashSetWithExpectedSize(200); // 25.1.1 has 149 entries for style

    try (BufferedReader reader = findAppCompatPublicTxt(artifactId, appCompatVersion)) {
      String line;
      while ((line = reader.readLine()) != null) {
        List<String> splitList = PUBLIC_TXT_SPLITTER.splitToList(line);
        if (splitList.size() != 2) {
          continue;
        }
        String key = splitList.get(0);
        String value = splitList.get(1);
        if (TAG_ATTR.equals(key)) {
          attrSet.add(value);
        }
        else if (TAG_STYLE.equals(key)) {
          // Note: A gradle level change since appcompat-v7/26.0.0-beta1 causes styles to be
          // declared in public.txt as:
          // `style Theme_AppCompat_Light_DarkActionBar`
          // instead of
          // `style Theme.AppCompat.Light.DarkActionBar`
          if (value.contains("_")) {
            value = value.replace('_', '.');
          }
          styleSet.add(value);
        }
        // The is one other typed namely 'layout' that has a single appCompat specific layout.
      }

      return new AppCompatStyleMigration(attrSet, styleSet);
    }
    catch (IOException e) {
      Logger.getInstance(AppCompatPublicDotTxtLookup.class).warn(e);
      return new AppCompatStyleMigration(Collections.emptySet(), Collections.emptySet());
    }
  }

  private File getCacheDir() {
    if (ApplicationManager.getApplication().isUnitTestMode() || GuiTestingService.getInstance().isGuiTestingMode()) {
      return null;
    }
    else {
      return new File(PathUtil.getCanonicalPath(PathManager.getSystemPath()), "maven.google");
    }
  }

  @NotNull
  private static JarFile findRemoteAppCompatAar(@NotNull GoogleMavenArtifactId artifactId, @NotNull String appCompatVersion) throws IOException {
    URLConnection ucon = null;
    BufferedInputStream is = null;
    try {
      String url = String.format(artifactId.isAndroidxLibrary() ? ANDROIDX_BASE_URL : BASE_URL,
                                 appCompatVersion, appCompatVersion);
      ucon = HttpConfigurable.getInstance().openConnection(url);
      is = new BufferedInputStream(ucon.getInputStream());
      // Create a temp file for the aar file
      // Note that we don't keep this around and instead cache the public.txt
      Path tmpPath = Files.createTempFile("aar_appcompat_cache", null);
      tmpPath.toFile().deleteOnExit();
      Files.copy(is, tmpPath, StandardCopyOption.REPLACE_EXISTING);
      return new JarFile(tmpPath.toFile());
    }
    finally {
      Closeables.closeQuietly(is);
      if (ucon instanceof HttpURLConnection) {
        ((HttpURLConnection)ucon).disconnect();
      }
    }
  }

  @Nullable
  private static JarFile findLocalAppCompatAar(@NotNull GoogleMavenArtifactId artifactId, @NotNull String appCompatVersion) throws IOException {
    AndroidSdkData sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk();
    if (sdkData == null) {
      return null;
    }
    GradleCoordinate coordinate = artifactId.getCoordinate(appCompatVersion);
    File appCompatAarFile = RepositoryUrlManager.get().getArchiveForCoordinate(coordinate, sdkData.getLocationFile(),
                                                                               FileSystems.getDefault());
    if (appCompatAarFile == null || !appCompatAarFile.exists()) {
      return null;
    }
    return new JarFile(appCompatAarFile);
  }

  private static byte[] findEntryRemote(@NotNull GoogleMavenArtifactId artifactId, @NotNull String appCompatVersion) throws IOException {
    JarFile jarFile = findLocalAppCompatAar(artifactId, appCompatVersion);
    if (jarFile == null) {
      jarFile = findRemoteAppCompatAar(artifactId, appCompatVersion);
    }
    try (InputStream is = jarFile.getInputStream(jarFile.getEntry(FN_PUBLIC_TXT))) {
      return ByteStreams.toByteArray(is);
    }
    finally {
      jarFile.close();
    }
  }

  @NotNull
  private synchronized BufferedReader findAppCompatPublicTxt(@NotNull GoogleMavenArtifactId artifactId, @NotNull String appCompatVersion) throws IOException {
    if (getCacheDir() != null) {
      File file = new File(getCacheDir(),
                           String.format(artifactId.isAndroidxLibrary() ?
                                         ANDROIDX_RELATIVE_FILE_PATH : RELATIVE_FILE_PATH, appCompatVersion));
      if (file.exists()) {
        return new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF_8));
      }
      else {
        try {
          byte[] bytes = findEntryRemote(artifactId, appCompatVersion);
          ByteArrayInputStream in = new ByteArrayInputStream(bytes);
          if (file.getParentFile().mkdirs()) {
            in.mark(Integer.MAX_VALUE);
            Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            in.reset();
          }
          return new BufferedReader(new InputStreamReader(in, UTF_8));
        } catch (IOException e) {
          Logger.getInstance(AppCompatPublicDotTxtLookup.class).warn(e);
          return getPublicTxtAsResource();
        }
      }
    }
    return getPublicTxtAsResource();
  }

  // Return the version of public.txt stored as a resource under resources/migrateToAppCompat/public.txt
  @NotNull
  private static BufferedReader getPublicTxtAsResource() {
    //noinspection IOResourceOpenedButNotSafelyClosed
    InputStream is = AppCompatPublicDotTxtLookup.class.getResourceAsStream("/migrateToAppCompat/public.txt");
    if (is == null) {
      throw new AssertionError("Missing resource migrateToAppCompat/public.txt");
    }
    return new BufferedReader(new InputStreamReader(is, UTF_8));
  }
}