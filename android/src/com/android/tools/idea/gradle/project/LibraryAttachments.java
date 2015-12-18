/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static java.util.Collections.addAll;

public class LibraryAttachments {
  @NotNull static final List<OrderRootType> SUPPORTED_TYPES = Lists.newArrayList(SOURCES, JavadocOrderRootType.getInstance());

  private static final Key<LibraryAttachments> LIBRARY_ATTACHMENTS = Key.create("project.library.attachments");

  @NotNull private final Project myProject;

  // Data is stored this way:
  // Key:
  //   Type of attachment [OrderRootType] (e.g. "SOURCES", "JAVADOC")
  // Value:
  //   Attachments per library [Map<String, List<String>>]
  //     Key:
  //       Library name [String] (e.g. "Guava")
  //     Value:
  //       Paths of the source/javadoc attachments of the library [List<String>] (e.g. "~/guava/guava-18.0-sources.jar")
  @NotNull private final Map<OrderRootType, Map<String, List<String>>> myAttachmentsByType;

  @Nullable
  public static LibraryAttachments getStoredLibraryAttachments(@NotNull Project project) {
    return project.getUserData(LIBRARY_ATTACHMENTS);
  }

  public static void removeLibrariesAndStoreAttachments(@NotNull Project project) {
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(project);
    LibraryTable.ModifiableModel model = libraryTable.getModifiableModel();
    try {
      Map<OrderRootType, Map<String, List<String>>> attachmentsByType = Maps.newHashMap();

      for (Library library : model.getLibraries()) {
        for (OrderRootType type : SUPPORTED_TYPES) {
          Map<String, List<String>> attachmentsByLibrary = attachmentsByType.get(type);
          if (attachmentsByLibrary == null) {
            attachmentsByLibrary = Maps.newHashMap();
            attachmentsByType.put(type, attachmentsByLibrary);
          }
          String name = library.getName();
          if (name != null) {
            List<String> attachments = attachmentsByLibrary.get(name);
            if (attachments == null) {
              attachments = Lists.newArrayList();
              attachmentsByLibrary.put(name, attachments);
            }
            String[] urls = library.getUrls(type);
            addAll(attachments, urls);
          }
          attachmentsByType.put(type, attachmentsByLibrary);
        }
        model.removeLibrary(library);
      }
      LibraryAttachments attachments = new LibraryAttachments(project, attachmentsByType);
      project.putUserData(LIBRARY_ATTACHMENTS, attachments);
    }
    finally {
      model.commit();
    }
  }

  private LibraryAttachments(@NotNull Project project, @NotNull Map<OrderRootType, Map<String, List<String>>> attachmentsByType) {
    myProject = project;
    myAttachmentsByType = attachmentsByType;
  }

  public void addUrlsTo(@NotNull Library.ModifiableModel libraryModel) {
    for (OrderRootType type : myAttachmentsByType.keySet()) {
      Map<String, List<String>> attachmentsByLibrary = myAttachmentsByType.get(type);
      if (attachmentsByLibrary != null) {
        List<String> attachments = attachmentsByLibrary.get(libraryModel.getName());
        if (attachments != null) {
          addUrlsToLibrary(type, libraryModel, attachments);
        }
      }
    }
  }

  private static void addUrlsToLibrary(@NotNull OrderRootType type,
                                       @NotNull Library.ModifiableModel libraryModel,
                                       @NotNull Collection<String> urls) {
    if (!urls.isEmpty()) {
      Set<String> existing = Sets.newHashSet(libraryModel.getUrls(type));
      for (String url : urls) {
        if (!existing.contains(url)) {
          libraryModel.addRoot(url, type);
        }
      }
    }
  }

  public void removeFromProject() {
    myProject.putUserData(LIBRARY_ATTACHMENTS, null);
  }
}
