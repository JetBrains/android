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
package com.android.tools.idea.gradle.project.sync.setup.module;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.*;

import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;

/**
 * This registry is used to keep track of the libraries that need to be removed between sync operations.
 */
public class SyncLibraryRegistry implements Disposable {
  @VisibleForTesting
  static final Key<SyncLibraryRegistry> KEY = Key.create("com.android.tools.gradle.sync.ProjectLibraryRegistry");

  private static Factory ourFactory = new Factory();

  @Nullable private Project myProject;

  @NotNull private final Map<String, Library> myProjectLibrariesByName = new HashMap<>();
  @NotNull private final Set<LibraryToUpdate> myLibrariesToUpdate = new HashSet<>();

  @TestOnly
  public static void replaceForTesting(@NotNull Project project, @Nullable SyncLibraryRegistry libraryRegistry) {
    project.putUserData(KEY, libraryRegistry);
  }

  @NotNull
  public static SyncLibraryRegistry getInstance(@NotNull Project project) {
    SyncLibraryRegistry registry = project.getUserData(KEY);
    if (registry == null || registry.isDisposed()) {
      registry = ourFactory.createNewInstance(project);
      project.putUserData(KEY, registry);
    }
    return registry;
  }

  SyncLibraryRegistry(@NotNull Project project) {
    myProject = project;
    Disposer.register(project, this);
    registerExistingLibraries();
  }

  private void registerExistingLibraries() {
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(myProject);
    for (Library library : libraryTable.getLibraries()) {
      String name = library.getName();
      if (name != null) {
        myProjectLibrariesByName.put(name, library);
      }
    }
  }

  /**
   * Marks the given library as "used" by the project.
   *
   * @param library        the library that is being used by the project.
   * @param newBinaryPaths the library's binary paths. They may be different that the existing ones.
   */
  public void markAsUsed(@NotNull Library library, @NotNull File... newBinaryPaths) {
    checkNotDisposed();
    String name = library.getName();
    if (name != null) {
      Library used = myProjectLibrariesByName.remove(name);
      if (used != null) {
        List<String> existingBinaryUrls = Lists.newArrayList(used.getUrls(CLASSES));
        boolean urlCountChanged = newBinaryPaths.length != existingBinaryUrls.size();

        List<String> newBinaryUrls = new ArrayList<>();
        for (File newBinaryPath : newBinaryPaths) {
          String newBinaryUrl = pathToIdeaUrl(newBinaryPath);
          existingBinaryUrls.remove(newBinaryUrl);
          newBinaryUrls.add(newBinaryUrl);
        }

        if (!existingBinaryUrls.isEmpty() || urlCountChanged) {
          // Library changed, we need to update binary paths.
          LibraryToUpdate libraryToUpdate = new LibraryToUpdate(used, newBinaryUrls);
          myLibrariesToUpdate.add(libraryToUpdate);
        }
      }
    }
  }

  @NotNull
  public Collection<Library> getLibrariesToRemove() {
    checkNotDisposed();
    return myProjectLibrariesByName.values();
  }

  @NotNull
  public List<LibraryToUpdate> getLibrariesToUpdate() {
    checkNotDisposed();
    return ImmutableList.copyOf(myLibrariesToUpdate);
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
    assert myProject != null;
    myProject.putUserData(KEY, null);
    myProject = null;
    myProjectLibrariesByName.clear();
    myLibrariesToUpdate.clear();
  }

  @TestOnly
  @NotNull
  Map<String, Library> getProjectLibrariesByName() {
    return myProjectLibrariesByName;
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
    public SyncLibraryRegistry createNewInstance(@NotNull Project project) {
      return new SyncLibraryRegistry(project);
    }
  }

  public static class LibraryToUpdate {
    @NotNull private final Library myLibrary;
    @NotNull private final Collection<String> myNewBinaryUrls;

    @VisibleForTesting
    public LibraryToUpdate(@NotNull Library library, @NotNull Collection<String> newBinaryUrls) {
      myLibrary = library;
      myNewBinaryUrls = newBinaryUrls;
    }

    @NotNull
    public Library getLibrary() {
      return myLibrary;
    }

    @NotNull
    public Collection<String> getNewBinaryUrls() {
      return myNewBinaryUrls;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      LibraryToUpdate update = (LibraryToUpdate)o;
      return Objects.equals(myLibrary.getName(), update.myLibrary.getName()) &&
             Objects.equals(myNewBinaryUrls, update.myNewBinaryUrls);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myLibrary.getName(), myNewBinaryUrls);
    }
  }
}
