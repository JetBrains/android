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

import com.android.annotations.Nullable;
import com.android.builder.model.NativeToolchain;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;

/**
 * Creates a deep copy of {@link NativeToolchain}.
 *
 * @see IdeAndroidProject
 */
public class IdeNativeToolchain implements NativeToolchain, Serializable {
  @NotNull private String myName;
  @Nullable private File myCCompilerExecutable;
  @Nullable private File myCppCompilerExecutable;

  public IdeNativeToolchain(@NotNull NativeToolchain toolchain) {
    myName = toolchain.getName();
    myCCompilerExecutable = toolchain.getCCompilerExecutable();
    myCppCompilerExecutable = toolchain.getCppCompilerExecutable();
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @Nullable
  public File getCCompilerExecutable() {
    return myCCompilerExecutable;
  }

  @Override
  @Nullable
  public File getCppCompilerExecutable() {
    return myCppCompilerExecutable;
  }
}
