/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.sync.libraries;

import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.LibraryFilesProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.Library.ModifiableModel;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.io.URLUtil;
import java.io.File;

/** Modifies {@link Library} content in {@link Library.ModifiableModel}. */
public class LibraryModifier {
  private static final Logger logger = Logger.getInstance(LibraryModifier.class);
  private final LibraryFilesProvider libraryFilesProvider;
  private final Library.ModifiableModel modifiableModel;

  public LibraryModifier(
      LibraryFilesProvider libraryFilesProvider, IdeModifiableModelsProvider modelsProvider) {
    this.libraryFilesProvider = libraryFilesProvider;
    modifiableModel = getLibraryModifiableModel(modelsProvider, libraryFilesProvider.getName());
  }

  public Library.ModifiableModel getModifiableModel() {
    return modifiableModel;
  }

  /** Writes the library content to its {@link Library.ModifiableModel}. */
  public void updateModifiableModel(BlazeProjectData blazeProjectData) {
    removeAllContents();
    for (File classFile : libraryFilesProvider.getClassFiles(blazeProjectData)) {
      addRoot(classFile, OrderRootType.CLASSES);
    }

    for (File sourceFile : libraryFilesProvider.getSourceFiles(blazeProjectData)) {
      addRoot(sourceFile, OrderRootType.SOURCES);
    }
  }

  private ModifiableModel getLibraryModifiableModel(
      IdeModifiableModelsProvider modelsProvider, String libraryName) {
    Library library = modelsProvider.getLibraryByName(libraryName);
    boolean libraryExists = library != null;
    if (!libraryExists) {
      library = modelsProvider.createLibrary(libraryName);
    }
    return modelsProvider.getModifiableLibraryModel(library);
  }

  private void addRoot(File file, OrderRootType orderRootType) {
    if (!file.exists()) {
      logger.warn("No local file found for " + file);
      return;
    }
    modifiableModel.addRoot(pathToUrl(file), orderRootType);
  }

  private String pathToUrl(File path) {
    String name = path.getName();
    boolean isJarFile =
        FileUtilRt.extensionEquals(name, "jar")
            || FileUtilRt.extensionEquals(name, "srcjar")
            || FileUtilRt.extensionEquals(name, "zip");
    // .jar files require an URL with "jar" protocol.
    String protocol =
        isJarFile
            ? StandardFileSystems.JAR_PROTOCOL
            : VirtualFileSystemProvider.getInstance().getSystem().getProtocol();
    String filePath = FileUtil.toSystemIndependentName(path.getPath());
    String url = VirtualFileManager.constructUrl(protocol, filePath);
    if (isJarFile) {
      url += URLUtil.JAR_SEPARATOR;
    }
    return url;
  }

  private void removeAllContents() {
    for (String url : modifiableModel.getUrls(OrderRootType.CLASSES)) {
      modifiableModel.removeRoot(url, OrderRootType.CLASSES);
    }
    for (String url : modifiableModel.getUrls(OrderRootType.SOURCES)) {
      modifiableModel.removeRoot(url, OrderRootType.SOURCES);
    }
  }
}
