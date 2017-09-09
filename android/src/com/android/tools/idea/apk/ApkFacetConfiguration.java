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
package com.android.tools.idea.apk;

import com.android.sdklib.devices.Abi;
import com.android.tools.idea.apk.debugging.DebuggableSharedObjectFile;
import com.android.tools.idea.apk.debugging.NativeLibrary;
import com.android.tools.idea.apk.debugging.SetupIssue;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.util.FilePaths.toSystemDependentPath;

public class ApkFacetConfiguration implements FacetConfiguration {
  private static final FacetEditorTab[] EDITOR_TABS = new FacetEditorTab[0];

  @NonNls public String APK_PATH;
  @NonNls public String APP_PACKAGE;
  @NotNull public List<SetupIssue> SETUP_ISSUES = new ArrayList<>();
  @NotNull public List<NativeLibrary> NATIVE_LIBRARIES = new ArrayList<>();
  @NotNull public Set<String> JAVA_SOURCE_FOLDER_PATHS = new HashSet<>();

  @Override
  public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    return EDITOR_TABS;
  }

  @NotNull
  public Collection<String> getDebugSymbolFolderPaths(@NotNull List<Abi> abis) {
    if (NATIVE_LIBRARIES.isEmpty() || abis.isEmpty()) {
      return Collections.emptyList();
    }
    Set<String> paths = new HashSet<>();
    for (Abi abi : abis) {
      for (NativeLibrary library : NATIVE_LIBRARIES) {
        DebuggableSharedObjectFile sharedObjectFile = library.debuggableSharedObjectFilesByAbi.get(abi);
        if (sharedObjectFile != null) {
          File path = toSystemDependentPath(sharedObjectFile.path);
          if (path.exists()) {
            paths.add(path.getParent());
          }
        }
      }
    }

    if (paths.isEmpty()) {
      return Collections.emptyList();
    }
    return paths;
  }

  public @NotNull Map<File, File> getExplicitModuleSymbolMap(@NotNull final Abi abi) {
    Map<File, File> moduleToSymbols = new HashMap<>();
    for (NativeLibrary library : NATIVE_LIBRARIES) {
      VirtualFile lib = library.sharedObjectFilesByAbi.get(abi);
      DebuggableSharedObjectFile sharedObjectFile = library.debuggableSharedObjectFilesByAbi.get(abi);
      if (sharedObjectFile != null && lib != null) {
        File libFile = VfsUtilCore.virtualToIoFile(lib);
        File symFile = toSystemDependentPath(sharedObjectFile.path);
        if (libFile.exists() && symFile.exists()) {
          moduleToSymbols.put(libFile, symFile);
        }
      }
    }
    return moduleToSymbols;
  }

  @NotNull
  public Map<String, String> getSymbolFolderPathMappings() {
    if (NATIVE_LIBRARIES.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, String> mappings = new HashMap<>();
    for (NativeLibrary library : NATIVE_LIBRARIES) {
      mappings.putAll(library.pathMappings);
    }
    return mappings;
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    XmlSerializer.deserializeInto(this, element);
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    XmlSerializer.serializeInto(this, element);
  }

  @NotNull
  public List<NativeLibrary> getLibrariesWithoutDebugSymbols() {
    if (NATIVE_LIBRARIES.isEmpty()) {
      return Collections.emptyList();
    }
    return NATIVE_LIBRARIES.stream().filter(library -> !library.hasDebugSymbols).collect(Collectors.toList());
  }

  public void removeIssues(@NotNull String category) {
    List<SetupIssue> forRemoval = new ArrayList<>();
    for (SetupIssue issue : SETUP_ISSUES) {
      if (category.equals(issue.category)) {
        forRemoval.add(issue);
      }
    }
    if (!forRemoval.isEmpty()) {
      SETUP_ISSUES.removeAll(forRemoval);
    }
  }
}