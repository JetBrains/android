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
package com.android.tools.idea.model;

import com.android.builder.model.NativeLibrary;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates a deep copy of {@link NativeLibrary}.
 *
 * @see IdeAndroidProject
 */
public class IdeNativeLibrary implements NativeLibrary, Serializable {
  @NotNull private final String myName;
  @NotNull private final String myAbi;
  @NotNull private final String myToolchainName;
  @NotNull private final List<File> myCIncludeDirs;
  @NotNull private final List<File> myCppIncludeDirs;
  @NotNull private final List<File> myCSystemIncludeDirs;
  @NotNull private final List<File> myCppSystemIncludeDirs;
  @NotNull private final List<String> myCDefines;
  @NotNull private final List<String> myCppDefines;
  @NotNull private final List<String> myCCompilerFlags;
  @NotNull private final List<String> myCppCompilerFlags;
  @NotNull private final List<File> myDebuggableLibraryFolders;

  public IdeNativeLibrary(@NotNull NativeLibrary library) {
    myName = library.getName();
    myAbi = library.getAbi();
    myToolchainName = library.getToolchainName();
    myCIncludeDirs = new ArrayList<>(library.getCIncludeDirs());
    myCppIncludeDirs = new ArrayList<>(library.getCppIncludeDirs());
    myCSystemIncludeDirs = new ArrayList<>(library.getCSystemIncludeDirs());
    myCppSystemIncludeDirs = new ArrayList<>(library.getCppSystemIncludeDirs());
    myCDefines = new ArrayList<>(library.getCDefines());
    myCppDefines = new ArrayList<>(library.getCppDefines());
    myCCompilerFlags = new ArrayList<>(library.getCCompilerFlags());
    myCppCompilerFlags = new ArrayList<>(library.getCppCompilerFlags());
    myDebuggableLibraryFolders = new ArrayList<>(library.getDebuggableLibraryFolders());
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public String getAbi() {
    return myAbi;
  }

  @Override
  @NotNull
  public String getToolchainName() {
    return myToolchainName;
  }

  @Override
  @NotNull
  public List<File> getCIncludeDirs() {
    return myCIncludeDirs;
  }

  @Override
  @NotNull
  public List<File> getCppIncludeDirs() {
    return myCppIncludeDirs;
  }

  @Override
  @NotNull
  public List<File> getCSystemIncludeDirs() {
    return myCSystemIncludeDirs;
  }

  @Override
  @NotNull
  public List<File> getCppSystemIncludeDirs() {
    return myCppSystemIncludeDirs;
  }

  @Override
  @NotNull
  public List<String> getCDefines() {
    return myCDefines;
  }

  @Override
  @NotNull
  public List<String> getCppDefines() {
    return myCppDefines;
  }

  @Override
  @NotNull
  public List<String> getCCompilerFlags() {
    return myCCompilerFlags;
  }

  @Override
  @NotNull
  public List<String> getCppCompilerFlags() {
    return myCppCompilerFlags;
  }

  @Override
  @NotNull
  public List<File> getDebuggableLibraryFolders() {
    return myDebuggableLibraryFolders;
  }
}
