/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.SdkConstants.DOT_GRADLE;
import static com.android.tools.idea.gradle.util.ImportUtil.escapeGroovyStringLiteral;
import static com.intellij.openapi.application.PathManager.getJarPathForClass;
import static com.intellij.openapi.util.io.FileUtil.createTempFile;
import static com.intellij.openapi.util.io.FileUtil.writeToFile;
import static org.jetbrains.plugins.gradle.util.GradleConstants.INIT_SCRIPT_CMD_OPTION;

import com.android.ide.common.repository.GoogleMavenRepositoryKt;
import com.android.ide.gradle.model.GradlePluginModel;
import com.android.ide.gradle.model.builder.AndroidStudioToolingPlugin;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.serviceContainer.NonInjectable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kotlin.reflect.KType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleInitScripts {
  @NotNull private final EmbeddedDistributionPaths myEmbeddedDistributionPaths;
  @NotNull private final ContentCreator myContentCreator;

  @NotNull
  public static GradleInitScripts getInstance() {
    return ApplicationManager.getApplication().getService(GradleInitScripts.class);
  }

  // Used by intellij
  @SuppressWarnings("unused")
  public GradleInitScripts() {
    this(EmbeddedDistributionPaths.getInstance(), new ContentCreator());
  }

  @NonInjectable
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

  public void addAndroidStudioToolingPluginInitScriptCommandLineArg(@NotNull List<String> allArgs) {
    try {
      File initScriptFile = createAndroidStudioToolingPluginInitScriptFile();
      addInitScriptCommandLineArg(initScriptFile, allArgs);
    }
    catch (IOException e) {
      // Unlikely to happen, create warning message in log files. Let Gradle sync continue without the injected init script.
      getLogger().warn("Failed to create init script that applies the Android Studio Tooling plugin.", e);
    }
  }

  @NotNull
  private File createAndroidStudioToolingPluginInitScriptFile() throws IOException {
    String content = myContentCreator.createAndroidStudioToolingPluginInitScriptContent();
    return createInitScriptFile("sync.studio.tooling", content);
  }

  @NotNull
  private static File createInitScriptFile(@NotNull String fileName, @NotNull String content) throws IOException {
    File file = createTempFile(fileName, DOT_GRADLE);
    try {
      file.deleteOnExit();
      writeToFile(file, content);
      getLogger().info(String.format("init script file %s contents %s", fileName, escapeAsStringLiteral(content)));
    } catch (Exception ex) {
      getLogger().error("Failed to create init script: " + fileName, ex);
      throw ex;
    }
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
    @NotNull private final AndroidStudioToolingPluginJars myAndroidStudioToolingPluginJars;

    ContentCreator() {
      this(new AndroidStudioToolingPluginJars());
    }

    ContentCreator(@NotNull AndroidStudioToolingPluginJars androidStudioToolingPluginJars) {
      myAndroidStudioToolingPluginJars = androidStudioToolingPluginJars;
    }

    @Nullable
    String createLocalMavenRepoInitScriptContent(@NotNull List<String> repoPaths) {
      if (repoPaths.isEmpty()) {
        return null;
      }

      StringBuilder paths = new StringBuilder();
      for (String path : repoPaths) {
        path = escapeGroovyStringLiteral(path);
        paths.append("      maven { url '").append(path).append("'}\n");
      }
      return "import org.gradle.util.GradleVersion\n\n" +
             "allprojects {\n" +
             "  buildscript {\n" +
             "    repositories {\n" + paths +
             "    }\n" +
             "  }\n" +
             "  repositories {\n" + paths +
             "  }\n" +
             "}\n" +
             "if (GradleVersion.current().baseVersion >= GradleVersion.version('7.0')) {\n" +
             "  beforeSettings {\n" +
             "    it.pluginManagement {\n" +
             "      repositories {\n" + paths +
             "      }\n" +
             "    }\n" +
             "  }\n" +
             "}\n" +
             "if (GradleVersion.current().baseVersion >= GradleVersion.version('6.8')) {\n" +
             "  beforeSettings {\n" +
             "    it.dependencyResolutionManagement {\n" +
             "      repositories {\n" + paths +
             "      }\n" +
             "    }\n" +
             "  }\n" +
             "}\n";
    }

    @NotNull
    String createAndroidStudioToolingPluginInitScriptContent() {
      List<String> paths = myAndroidStudioToolingPluginJars.getJarPaths();
      return "initscript {\n" +
             "    dependencies {\n" +
             "        " + createClassPathString(paths) + "\n" +
             "    }\n" +
             "}\n" +
             "allprojects {\n" +
             "    apply plugin: " + AndroidStudioToolingPlugin.class.getName() + "\n" +
             "}\n";
    }

    @NotNull
    String createClassPathString(@NotNull List<String> paths) {
      StringBuilder classpath = new StringBuilder();
      classpath.append("classpath files([");
      int pathCount = paths.size();
      for (int i = 0; i < pathCount; i++) {
        String jarPath = paths.get(i);
        classpath.append("mapPath('").append(jarPath).append("')");
        if (i < pathCount - 1) {
          classpath.append(", ");
        }
      }
      classpath.append("])");
      return classpath.toString();
    }
  }

  @VisibleForTesting
  static class AndroidStudioToolingPluginJars {
    @NotNull
    List<String> getJarPaths() {
      return Stream.of(
        getJarPathForClass(GradlePluginModel.class), getJarPathForClass(AndroidStudioToolingPlugin.class), getJarPathForClass(KType.class))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    }

    private static String getJarPathForClass(@NotNull Class<?> aClass) {
      return FileUtil.toCanonicalPath(PathManager.getJarPathForClass(aClass));
    }
  }
}
