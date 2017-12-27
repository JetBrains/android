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
package com.android.tools.idea.gradle.project.sync;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;

import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.PropertiesFiles.getProperties;
import static com.android.tools.idea.gradle.util.PropertiesFiles.savePropertiesToFile;
import static com.android.tools.idea.testing.HighlightInfos.getHighlightInfos;
import static com.android.tools.idea.testing.TestProjectPaths.TRANSITIVE_DEPENDENCIES;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.createTempDirectory;

/**
 * Integration tests for 'Gradle Sync' and the Gradle build cache.
 */
public class BuildCacheSyncTest extends AndroidGradleTestCase {
  // See https://code.google.com/p/android/issues/detail?id=229633
  public void testSyncWithGradleBuildCacheUninitialized() throws Exception {
    prepareProjectForImport(TRANSITIVE_DEPENDENCIES);
    setBuildCachePath(createTempDirectory("build-cache", ""));

    Project project = getProject();
    importProject(project.getName(), getBaseDirPath(project), null);

    File mainActivityFile = new File("app/src/main/java/com/example/alruiz/transitive_dependencies/MainActivity.java");
    Predicate<HighlightInfo> matchByDescription = info -> "Cannot resolve symbol 'AppCompatActivity'".equals(info.getDescription());
    List<HighlightInfo> highlights = getHighlightInfos(project, mainActivityFile, matchByDescription);
    // AppCompatActivity should be resolved, since AARs are exploded during sync.
    assertThat(highlights).isEmpty();
  }

  private void setBuildCachePath(@NotNull File path) throws IOException {
    // Set up path of build-cache
    // See: https://developer.android.com/r/tools/build-cache.html
    Project project = getProject();
    File gradlePropertiesFilePath = new File(getBaseDirPath(project), "gradle.properties");
    Properties gradleProperties = getProperties(gradlePropertiesFilePath);
    gradleProperties.setProperty("android.enableBuildCache", "true");
    gradleProperties.setProperty("android.buildCacheDir", path.getAbsolutePath());
    savePropertiesToFile(gradleProperties, gradlePropertiesFilePath, "");
  }
}
