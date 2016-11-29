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
package com.android.tools.idea.gradle.project.sync.setup.post.project;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.io.FileUtil.delete;

public class DisposedModules implements Disposable {
  @VisibleForTesting
  static final Key<DisposedModules> KEY = Key.create("com.android.tools.gradle.sync.DisposedModules");

  private final
  @NotNull List<File> myFilesToDelete = new ArrayList<>();

  private static Factory ourFactory = new Factory();

  @NotNull
  public static DisposedModules getInstance(@NotNull Project project) {
    DisposedModules disposedModules = project.getUserData(KEY);
    if (disposedModules == null || disposedModules.isDisposed()) {
      disposedModules = ourFactory.createNewInstance();
      project.putUserData(KEY, disposedModules);
    }
    return disposedModules;
  }

  public void markImlFilesForDeletion(@NotNull List<File> filesToDelete) {
    checkNotDisposed();
    myFilesToDelete.clear();
    if (!filesToDelete.isEmpty()) {
      myFilesToDelete.addAll(filesToDelete);
    }
  }

  /**
   * Deletes .iml files that have been added when invoking {@link #markImlFilesForDeletion(List)}.
   * <p>
   * This method will dispose this instance of {@code DisposedModules}. Any subsequent calls to {@link #markImlFilesForDeletion(List)} or
   * {@link #deleteImlFilesForDisposedModules()} will throw {@link IllegalStateException}s. To avoid this exception, invoke
   * {@link #getInstance(Project)} first.
   * </p>
   */
  public void deleteImlFilesForDisposedModules() {
    checkNotDisposed();
    for (File imlFile : myFilesToDelete) {
      if (imlFile.isFile()) {
        boolean deleted = delete(imlFile);
        if (!deleted) {
          Logger.getInstance(DisposedModules.class).info("Failed to delete '" + imlFile.getPath() + "'");
        }
      }
    }
    Disposer.dispose(this);
  }

  private void checkNotDisposed() {
    if (isDisposed()) {
      throw new IllegalStateException("Already disposed");
    }
  }

  @VisibleForTesting
  boolean isDisposed() {
    return Disposer.isDisposed(this);
  }

  @Override
  public void dispose() {
    myFilesToDelete.clear();
  }

  @VisibleForTesting
  @NotNull
  List<File> getFilesToDelete() {
    return myFilesToDelete;
  }

  @TestOnly
  public static void restoreFactory() {
    setFactory(new Factory());
  }

  @TestOnly
  public static void setFactory(@NotNull Factory factory) {
    ourFactory = factory;
  }

  @VisibleForTesting
  public static class Factory {
    @NotNull
    public DisposedModules createNewInstance() {
      return new DisposedModules();
    }
  }
}
