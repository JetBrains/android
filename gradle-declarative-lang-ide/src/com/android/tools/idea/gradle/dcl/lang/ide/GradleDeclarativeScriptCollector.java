/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.dcl.lang.ide;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.SmartList;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.project.GradleAutoReloadSettingsCollector;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

public class GradleDeclarativeScriptCollector implements GradleAutoReloadSettingsCollector {
  private static final Logger LOG = Logger.getInstance(GradleDeclarativeScriptCollector.class);
  @Override
  public @NotNull List<File> collectSettingsFiles(@NotNull Project project, @NotNull GradleProjectSettings projectSettings) {
    List<File> files = new SmartList<>();
    if (!DeclarativeIdeSupport.isEnabled()) return files;

    for (String modulePath : projectSettings.getModules()) {
      ProgressManager.checkCanceled();

      try {
        Files.walkFileTree(Paths.get(modulePath), EnumSet.noneOf(FileVisitOption.class), 1, new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
            String fileName = path.getFileName().toString();
            if (fileName.endsWith('.' + "gradle.dcl")) {
              File file = path.toFile();
              if (file.isFile()) files.add(file);
            }
            return FileVisitResult.CONTINUE;
          }
        });
      }
      catch (IOException | InvalidPathException e) {
        LOG.debug(e);
      }
    }

    return files;
  }
}
