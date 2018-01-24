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
package com.android.tools.idea.gradle.project.sync.ng.caching;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;

import static com.android.tools.idea.gradle.util.GradleUtil.getCacheFolderRootPath;
import static com.intellij.openapi.util.io.FileUtil.ensureExists;

public class CachedProjectModels implements Serializable {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  public static class Factory {
    @NotNull
    public CachedProjectModels createNew() {
      return new CachedProjectModels();
    }
  }

  public static class Loader {
    @Nullable
    public CachedProjectModels loadFromDisk(@NotNull Project project) {
      File cacheFilePath = getCacheFilePath(project);
      if (cacheFilePath.isFile()) {
        try (FileInputStream fis = new FileInputStream(cacheFilePath)) {
          try (ObjectInputStream ois = new ObjectInputStream(fis)) {
            return (CachedProjectModels)ois.readObject();
          }
          catch (Throwable e) {
            getLog().warn(String.format("Failed to load Gradle models from '%1$s'", cacheFilePath.getPath()), e);
          }
        }
        catch (Throwable e) {
          getLog().warn(String.format("Failed to access '%1$s' while loading Gradle models", cacheFilePath.getPath()), e);
        }
      }
      return null;
    }
  }

  // Key: module name.
  @NotNull private final Map<String, CachedModuleModels> myModelsByModuleName = new HashMap<>();

  @VisibleForTesting
  CachedProjectModels() {
  }

  @NotNull
  public CachedModuleModels addModule(@NotNull Module module) {
    CachedModuleModels cache = new CachedModuleModels(module);
    myModelsByModuleName.put(module.getName(), cache);
    return cache;
  }

  @Nullable
  public CachedModuleModels findCacheForModule(@NotNull String moduleName) {
    return myModelsByModuleName.get(moduleName);
  }

  @NotNull
  public Future<?> saveToDisk(@NotNull Project project) {
    File cacheFilePath = getCacheFilePath(project);
    return saveToDisk(cacheFilePath);
  }

  @VisibleForTesting
  @NotNull
  static File getCacheFilePath(@NotNull Project project) {
    return new File(getCacheFolderRootPath(project), "gradle_models.ser");
  }

  @NotNull
  private Future<?> saveToDisk(@NotNull File path) {
    return ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        ensureExists(path.getParentFile());
      }
      catch (IOException e) {
        getLog().warn(String.format("Failed to create folders for path '%1$s'", path.getPath()), e);
      }
      try (FileOutputStream fos = new FileOutputStream(path)) {
        try (ObjectOutputStream oos = new ObjectOutputStream(fos)) {
          oos.writeObject(this);
        }
        catch (Throwable e) {
          getLog().warn(String.format("Failed to save Gradle models to path '%1$s'", path.getPath()), e);
        }
      }
      catch (Throwable e) {
        getLog().warn(String.format("Failed to open path '%1$s'", path.getPath()), e);
      }
    });
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(CachedProjectModels.class);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CachedProjectModels)) {
      return false;
    }
    CachedProjectModels cache = (CachedProjectModels)o;
    return Objects.equals(myModelsByModuleName, cache.myModelsByModuleName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myModelsByModuleName);
  }

  @Override
  public String toString() {
    return "CachedProjectModels{" +
           "myModelsByModuleName=" + myModelsByModuleName +
           '}';
  }
}
