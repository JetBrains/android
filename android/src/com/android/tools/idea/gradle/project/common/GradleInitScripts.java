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

import com.android.ide.common.repository.GoogleMavenRepositoryKt;
import com.android.java.model.JavaProject;
import com.android.java.model.builder.JavaLibraryPlugin;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.SdkConstants.DOT_GRADLE;
import static com.android.tools.idea.gradle.eclipse.GradleImport.escapeGroovyStringLiteral;
import static com.intellij.openapi.application.PathManager.getJarPathForClass;
import static com.intellij.openapi.util.io.FileUtil.createTempFile;
import static com.intellij.openapi.util.io.FileUtil.writeToFile;
import static org.jetbrains.plugins.gradle.util.GradleConstants.INIT_SCRIPT_CMD_OPTION;

public class GradleInitScripts {
  @NotNull private final EmbeddedDistributionPaths myEmbeddedDistributionPaths;
  @NotNull private final ContentCreator myContentCreator;

  @NotNull
  public static GradleInitScripts getInstance() {
    return ServiceManager.getService(GradleInitScripts.class);
  }

  public GradleInitScripts(@NotNull EmbeddedDistributionPaths embeddedDistributionPaths) {
    this(embeddedDistributionPaths, new ContentCreator());
  }

  @VisibleForTesting
  GradleInitScripts(@NotNull EmbeddedDistributionPaths embeddedDistributionPaths, @NotNull ContentCreator contentCreator) {
    myEmbeddedDistributionPaths = embeddedDistributionPaths;
    myContentCreator = contentCreator;
  }

  public void addLocalMavenRepoInitScriptCommandLineArg(@NotNull List<String> allArgs) {
    File initScriptFile = createLocalMavenRepoInitScriptFile();
    if (initScriptFile != null) {
      addInitScriptCommandLineArg(initScriptFile, allArgs);
    }
  }

  @Nullable
  private File createLocalMavenRepoInitScriptFile() {
    List<String> repoPaths = myEmbeddedDistributionPaths.findAndroidStudioLocalMavenRepoPaths().stream()
      .map(File::getPath).collect(Collectors.toCollection(ArrayList::new));

    if (!GoogleMavenRepositoryKt.DEFAULT_GMAVEN_URL.equals(GoogleMavenRepositoryKt.GMAVEN_BASE_URL)) {
      repoPaths.add(GoogleMavenRepositoryKt.GMAVEN_BASE_URL);
    }

    String content = myContentCreator.createLocalMavenRepoInitScriptContent(repoPaths);
    if (content != null) {
      String fileName = "sync.local.repo";
      try {
        return createInitScriptFile(fileName, content);
      }
      catch (Throwable e) {
        String message = String.format("Failed to set up Gradle init script: '%1$s'", fileName);
        getLogger().warn(message, e);
      }
    }
    return null;
  }

  public void addApplyJavaLibraryPluginInitScriptCommandLineArg(@NotNull List<String> allArgs) {
    try {
      File initScriptFile = createApplyJavaLibraryPluginInitScriptFile();
      addInitScriptCommandLineArg(initScriptFile, allArgs);
    }
    catch (IOException e) {
      // if the init script cannot be created, sync won't succeed. Unlikely to happen, but better fail now than later.
      // No need for checked exception. Client code won't be able to recover from this.
      throw new RuntimeException("Failed to create init script that applies the Java library plugin", e);
    }
  }

  @NotNull
  private File createApplyJavaLibraryPluginInitScriptFile() throws IOException {
    String content = myContentCreator.createApplyJavaLibraryPluginInitScriptContent();
    return createInitScriptFile("sync.java.lib", content);
  }

  @NotNull
  private static File createInitScriptFile(@NotNull String fileName, @NotNull String content) throws IOException {
    File file = createTempFile(fileName, DOT_GRADLE);
    file.deleteOnExit();
    writeToFile(file, content);
    getLogger().info(String.format("init script file %s contents %s", fileName, escapeAsStringLiteral(content)));
    return file;
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(GradleInitScripts.class);
  }

  @NotNull
  private static String escapeAsStringLiteral(@NotNull String s) {
    // JLS 3.10.6: Escape Sequences for Character and String Literals
    // @formatter:off
    Escaper escaper = Escapers.builder().addEscape('\b', "\\b")
                                        .addEscape('\t', "\\t")
                                        .addEscape('\n', "\\n")
                                        .addEscape('\f', "\\f")
                                        .addEscape('\r', "\\r")
                                        .addEscape('"', "\\\"")
                                        .addEscape('\\', "\\\\")
                                        .build();
    // @formatter:on
    return "\"" + escaper.escape(s) + "\"";
  }

  private static void addInitScriptCommandLineArg(@NotNull File initScriptFile, @NotNull List<String> allArgs) {
    allArgs.add(INIT_SCRIPT_CMD_OPTION);
    allArgs.add(initScriptFile.getAbsolutePath());
  }

  @VisibleForTesting
  static class ContentCreator {
    @NotNull private final JavaLibraryPluginJars myJavaLibraryPluginJars;

    ContentCreator() {
      this(new JavaLibraryPluginJars());
    }

    ContentCreator(@NotNull JavaLibraryPluginJars javaLibraryPluginJars) {
      myJavaLibraryPluginJars = javaLibraryPluginJars;
    }

    @Nullable
    String createLocalMavenRepoInitScriptContent(@NotNull List<String> repoPaths) {
      if (repoPaths.isEmpty()) {
        return null;
      }

      String paths = "";
      for (String path: repoPaths) {
        path = escapeGroovyStringLiteral(path);
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

    @NotNull
    String createApplyJavaLibraryPluginInitScriptContent() {
      List<String> paths = myJavaLibraryPluginJars.getJarPaths();
      StringBuilder classpath = new StringBuilder();
      classpath.append("classpath files([");
      int pathCount = paths.size();
      for (int i = 0; i < pathCount; i++) {
        String jarPath = escapeGroovyStringLiteral(paths.get(i));
        classpath.append("'").append(jarPath).append("'");
        if (i < pathCount - 1) {
          classpath.append(", ");
        }
      }
      classpath.append("])");
      return "initscript {\n" +
             "    dependencies {\n" +
             "        " + classpath.toString() + "\n" +
             "    }\n" +
             "}\n" +
             "allprojects {\n" +
             "    apply plugin: " + JavaLibraryPlugin.class.getName() + "\n" +
             "}\n";
    }
  }

  @VisibleForTesting
  static class JavaLibraryPluginJars {
    @NotNull
    List<String> getJarPaths() {
      return Arrays.asList(getJarPathForClass(JavaProject.class), getJarPathForClass(JavaLibraryPlugin.class));
    }
  }
}
