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
package com.android.tools.idea.gradle.project.model.ide.android.stubs;

import com.android.builder.model.NativeLibrary;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Objects;

public final class NativeLibraryStub extends BaseStub implements NativeLibrary {
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

  public NativeLibraryStub() {
    this("name", "abi", "toolchain", Lists.newArrayList(new File("cInclude")), Lists.newArrayList(new File("cppInclude")),
         Lists.newArrayList(new File("cSystemInclude")), Lists.newArrayList(new File("cppSystemInclude")), Lists.newArrayList("cDefine"),
         Lists.newArrayList("cppDefine"), Lists.newArrayList("cCompilerFlag"), Lists.newArrayList("cppCompilerFlag"),
         Lists.newArrayList(new File("debuggableLibrary")));
  }

  public NativeLibraryStub(@NotNull String name,
                           @NotNull String abi,
                           @NotNull String toolchainName,
                           @NotNull List<File> cIncludeDirs,
                           @NotNull List<File> cppIncludeDirs,
                           @NotNull List<File> cSystemIncludeDirs,
                           @NotNull List<File> cppSystemIncludeDirs,
                           @NotNull List<String> cDefines,
                           @NotNull List<String> cppDefines,
                           @NotNull List<String> cCompilerFlags,
                           @NotNull List<String> cppCompilerFlags,
                           @NotNull List<File> debuggableLibraryFolders) {
    myName = name;
    myAbi = abi;
    myToolchainName = toolchainName;
    myCIncludeDirs = cIncludeDirs;
    myCppIncludeDirs = cppIncludeDirs;
    myCSystemIncludeDirs = cSystemIncludeDirs;
    myCppSystemIncludeDirs = cppSystemIncludeDirs;
    myCDefines = cDefines;
    myCppDefines = cppDefines;
    myCCompilerFlags = cCompilerFlags;
    myCppCompilerFlags = cppCompilerFlags;
    myDebuggableLibraryFolders = debuggableLibraryFolders;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NativeLibrary)) {
      return false;
    }
    NativeLibrary stub = (NativeLibrary)o;
    return Objects.equals(getName(), stub.getName()) &&
           Objects.equals(getAbi(), stub.getAbi()) &&
           Objects.equals(getToolchainName(), stub.getToolchainName()) &&
           Objects.equals(getCIncludeDirs(), stub.getCIncludeDirs()) &&
           Objects.equals(getCppIncludeDirs(), stub.getCppIncludeDirs()) &&
           Objects.equals(getCSystemIncludeDirs(), stub.getCSystemIncludeDirs()) &&
           Objects.equals(getCppSystemIncludeDirs(), stub.getCppSystemIncludeDirs()) &&
           Objects.equals(getCDefines(), stub.getCDefines()) &&
           Objects.equals(getCppDefines(), stub.getCppDefines()) &&
           Objects.equals(getCCompilerFlags(), stub.getCCompilerFlags()) &&
           Objects.equals(getCppCompilerFlags(), stub.getCppCompilerFlags()) &&
           Objects.equals(getDebuggableLibraryFolders(), stub.getDebuggableLibraryFolders());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), getAbi(), getToolchainName(), getCIncludeDirs(), getCppIncludeDirs(), getCSystemIncludeDirs(),
                        getCppSystemIncludeDirs(), getCDefines(), getCppDefines(), getCCompilerFlags(), getCppCompilerFlags(),
                        getDebuggableLibraryFolders());
  }

  @Override
  public String toString() {
    return "NativeLibraryStub{" +
           "myName='" + myName + '\'' +
           ", myAbi='" + myAbi + '\'' +
           ", myToolchainName='" + myToolchainName + '\'' +
           ", myCIncludeDirs=" + myCIncludeDirs +
           ", myCppIncludeDirs=" + myCppIncludeDirs +
           ", myCSystemIncludeDirs=" + myCSystemIncludeDirs +
           ", myCppSystemIncludeDirs=" + myCppSystemIncludeDirs +
           ", myCDefines=" + myCDefines +
           ", myCppDefines=" + myCppDefines +
           ", myCCompilerFlags=" + myCCompilerFlags +
           ", myCppCompilerFlags=" + myCppCompilerFlags +
           ", myDebuggableLibraryFolders=" + myDebuggableLibraryFolders +
           "}";
  }
}
