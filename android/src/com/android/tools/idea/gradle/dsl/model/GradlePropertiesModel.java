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
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradlePropertiesFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static com.android.tools.idea.util.PropertiesFiles.getProperties;

/**
 * Represents a gradle.properties file.
 */
public class GradlePropertiesModel extends GradleFileModelImpl {
  private static final Logger LOG = Logger.getInstance(GradlePropertiesModel.class);

  @Nullable
  public static GradlePropertiesModel parsePropertiesFile(@NotNull VirtualFile file, @NotNull Project project, @NotNull String moduleName) {
    File propertiesFile = VfsUtilCore.virtualToIoFile(file);
    try {
      Properties properties = getProperties(propertiesFile);
      GradlePropertiesFile gradlePropertiesFile = new GradlePropertiesFile(properties, file, project, moduleName);
      return new GradlePropertiesModel(gradlePropertiesFile);
    }
    catch (IOException e) {
      LOG.warn("Failed to process " + file.getPath(), e);
      return null;
    }
  }

  private GradlePropertiesModel(@NotNull GradleDslFile gradleDslFile) {
    super(gradleDslFile);
  }
}
