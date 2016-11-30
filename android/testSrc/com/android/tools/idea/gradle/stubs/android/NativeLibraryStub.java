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
package com.android.tools.idea.gradle.stubs.android;

import com.android.annotations.NonNull;
import com.android.builder.model.NativeLibrary;

import java.io.File;
import java.util.List;

/**
 * Stub implementation of {@link NativeLibrary} for tests.
 */
public class NativeLibraryStub implements NativeLibrary {

  private final String myName;

  public NativeLibraryStub(String name) {
    myName = name;
  }
  @NonNull
  @Override
  public String getName() {
    return myName;
  }

  @NonNull
  @Override
  public String getAbi() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public String getToolchainName() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public List<File> getCIncludeDirs() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public List<File> getCppIncludeDirs() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public List<File> getCSystemIncludeDirs() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public List<File> getCppSystemIncludeDirs() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public List<String> getCDefines() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public List<String> getCppDefines() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public List<String> getCCompilerFlags() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public List<String> getCppCompilerFlags() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public List<File> getDebuggableLibraryFolders() {
    throw new UnsupportedOperationException();
  }
}
