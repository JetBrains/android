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
package com.android.tools.idea.gradle.project.common;

import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

import static com.android.SdkConstants.DOT_GRADLE;
import static com.android.tools.idea.gradle.eclipse.GradleImport.escapeGroovyStringLiteral;
import static com.intellij.openapi.util.io.FileUtil.createTempFile;
import static com.intellij.openapi.util.io.FileUtil.writeToFile;
import static org.jetbrains.plugins.gradle.util.GradleConstants.INIT_SCRIPT_CMD_OPTION;

public class GradleInitScripts {
  @NotNull private final ContentCreator myContentCreator;

  @NotNull
  public static GradleInitScripts getInstance() {
    return ServiceManager.getService(GradleInitScripts.class);
  }

  public GradleInitScripts() {
    this(new ContentCreator());
  }

  @VisibleForTesting
  GradleInitScripts(@NotNull ContentCreator contentCreator) {
    myContentCreator = contentCreator;
  }

  public void addLocalMavenRepoInitScriptCommandLineArgTo(@NotNull List<String> allArgs) {
    File initScriptFile = createLocalMavenRepoInitScriptFile();
    if (initScriptFile != null) {
      addInitScriptCommandLineArg(initScriptFile, allArgs);
    }
  }

  @VisibleForTesting
  @Nullable
  File createLocalMavenRepoInitScriptFile() {
    List<File> repoPaths = EmbeddedDistributionPaths.getInstance().findAndroidStudioLocalMavenRepoPaths();
    String content = myContentCreator.createLocalMavenRepoInitScriptContent(repoPaths);
    if (content != null) {
      return createInitScriptFile("asLocalRepo", content);
    }
    return null;
  }

  public void addProfilerClasspathInitScriptCommandLineArgTo(@NotNull List<String> allArgs) {
    String content = "allprojects {\n" +
                     "  buildscript {\n" +
                     "    dependencies {\n" +
                     "      classpath 'com.android.tools:studio-profiler-plugin:1.0'\n" +
                     "    }\n" +
                     "  }\n" +
                     "}\n";

    File initScriptFile = createInitScriptFile("asPerfClasspath", content);
    if (initScriptFile != null) {
      addInitScriptCommandLineArg(initScriptFile, allArgs);
    }
  }

  @Nullable
  private static File createInitScriptFile(@NotNull String fileName, @NotNull String content) {
    try {
      File file = createTempFile(fileName, DOT_GRADLE);
      file.deleteOnExit();
      writeToFile(file, content);
      return file;
    }
    catch (Throwable e) {
      String message = String.format("Failed to set up  Gradle init script: '%1$s'", fileName);
      Logger.getInstance(GradleInitScripts.class).warn(message, e);
    }
    return null;
  }

  private static void addInitScriptCommandLineArg(@NotNull File initScriptFile, @NotNull List<String> allArgs) {
    allArgs.add(INIT_SCRIPT_CMD_OPTION);
    allArgs.add(initScriptFile.getAbsolutePath());
  }

  @VisibleForTesting
  static class ContentCreator {
    @Nullable
    String createLocalMavenRepoInitScriptContent(@NotNull List<File> repoPaths) {
      if (repoPaths.isEmpty()) {
        return null;
      }

      String paths = "";
      for (File file : repoPaths) {
        String path = escapeGroovyStringLiteral(file.getPath());
        paths += "      maven { url '" + path + "'}\n";
      }
      return "allprojects {\n" +
             "  buildscript {\n" +
             "    repositories {\n" + paths +
             "    }\n" +
             "  }\n" +
             "  repositories {\n" + paths +
             "  }\n" +
             "}\n";
    }
  }
}
