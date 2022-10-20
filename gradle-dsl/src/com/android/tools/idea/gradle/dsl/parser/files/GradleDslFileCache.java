/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.files;

import static com.android.tools.idea.gradle.dsl.model.notifications.NotificationTypeReference.CIRCULAR_APPLICATION;

import com.android.tools.idea.gradle.dsl.model.BuildModelContext;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.google.common.base.Charsets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Cache to store a mapping between file paths and their respective {@link GradleDslFileCache} objects, its main purpose it to
 * prevent the parsing of a file more than once. In large projects without caching the parsed file we can end up parsing the same
 * file hundreds of times.
 */
public class GradleDslFileCache {
  @NotNull private final Project myProject;
  @NotNull private final List<GradleDslFile> myParsedDslFiles = new ArrayList<>();
  @NotNull private final Map<String, GradleBuildFile> myParsedBuildFiles = new LinkedHashMap<>();
  @NotNull private final Map<String, GradleSettingsFile> myParsedSettingsFiles = new LinkedHashMap<>();
  @NotNull private final Map<String, GradlePropertiesFile> myParsedPropertiesFiles = new LinkedHashMap<>();
  @NotNull private final Map<String, GradleVersionCatalogFile> myParsedVersionCatalogFiles = new LinkedHashMap<>();
  @NotNull private Deque<VirtualFile> myParsingStack = new ArrayDeque<>();

  public GradleDslFileCache(@NotNull Project project) {
    myProject = project;
  }

  public void clearAllFiles() {
    myParsedBuildFiles.clear();
  }

  @NotNull
  public GradleBuildFile getOrCreateBuildFile(@NotNull VirtualFile file,
                                              @NotNull String name,
                                              @NotNull BuildModelContext context,
                                              boolean isApplied) {
    // TODO(xof): investigate whether (as I suspect) this cache will be wrong for an applied file included from various places in the build
    GradleBuildFile dslFile = myParsedBuildFiles.get(file.getUrl());
    if (dslFile == null) {
      if (!myParsingStack.contains(file)) {
        myParsingStack.push(file);
        dslFile = context.parseBuildFile(myProject, file, name, isApplied);
        myParsingStack.pop();
        myParsedBuildFiles.put(file.getUrl(), dslFile);
        myParsedDslFiles.add(dslFile);
      }
      else {
        // create a placeholder GradleBuildFile.  (It'll get overwritten in the cache anyway when popping from the stack)
        dslFile = new GradleBuildFile(file, myProject, name, context);
        // produce a notification.  Arguably notifying on a dslFile which won't end up in the cache is dubious, but we don't actually have
        // anywhere else at this point.
        dslFile.notification(CIRCULAR_APPLICATION);
      }
    }
    return dslFile;
  }

  public void putBuildFile(@NotNull String name, @NotNull GradleBuildFile buildFile) {
    myParsedBuildFiles.put(name, buildFile);
    myParsedDslFiles.add(buildFile);
  }

  /**
   * @return the first original file that was being parsed, this is used to resolve relative paths.
   */
  @Nullable
  public VirtualFile getCurrentParsingRoot() {
    return myParsingStack.isEmpty() ? null : myParsingStack.getLast();
  }

  @NotNull
  public GradleSettingsFile getOrCreateSettingsFile(@NotNull VirtualFile settingsFile, @NotNull BuildModelContext context) {
    GradleSettingsFile dslFile = myParsedSettingsFiles.get(settingsFile.getUrl());
    if (dslFile == null) {
      dslFile = new GradleSettingsFile(settingsFile, myProject, "settings", context);
      dslFile.parse();
      myParsedSettingsFiles.put(settingsFile.getUrl(), dslFile);
      myParsedDslFiles.add(dslFile);
    }
    return dslFile;
  }

  @Nullable
  public GradlePropertiesFile getOrCreatePropertiesFile(@NotNull VirtualFile file, @NotNull String moduleName, @NotNull BuildModelContext context) {
    GradlePropertiesFile dslFile = myParsedPropertiesFiles.get(file.getUrl());
    if (dslFile == null) {
      try {
        Properties properties = getProperties(file);
        dslFile = new GradlePropertiesFile(properties, file, myProject, moduleName, context);
        myParsedPropertiesFiles.put(file.getUrl(), dslFile);
        myParsedDslFiles.add(dslFile);
      }
      catch (IOException e) {
        Logger.getInstance(GradleDslFileCache.class).warn("Failed to process properties file " + file.getPath(), e);
        return null;
      }
    }
    return dslFile;
  }

  private static Properties getProperties(@NotNull VirtualFile file) throws IOException {
    Properties properties = new Properties();
    try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), Charsets.UTF_8)) {
      properties.load(reader);
    }
    return properties;
  }

  public @NotNull GradleVersionCatalogFile getOrCreateVersionCatalogFile(@NotNull VirtualFile file,
                                                                         @NotNull String catalogName,
                                                                         @NotNull BuildModelContext context) {
    // It is safe not to incorporate the catalogName as part of the key, because parsing the contents of the catalog file
    // is context-independent.  Looking up entries in the catalog always involves going through a property named by the
    // catalogName.
    GradleVersionCatalogFile dslFile = myParsedVersionCatalogFiles.get(file.getUrl());
    if (dslFile == null) {
      dslFile = new GradleVersionCatalogFile(file, myProject, "versionCatalog", catalogName, context);
      dslFile.parse();
      myParsedVersionCatalogFiles.put(file.getUrl(), dslFile);
      myParsedDslFiles.add(dslFile);
    }
    return dslFile;
  }



  @NotNull
  public List<GradleDslFile> getAllFiles() {
    return myParsedDslFiles;
  }
}
