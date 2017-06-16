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

import com.android.builder.model.NativeToolchain;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Objects;

public class NativeToolchainStub extends BaseStub implements NativeToolchain {
  @NotNull private final String myName;
  @Nullable private final File myCCompilerExecutable;
  @Nullable private final File myCppCompilerExecutable;

  public NativeToolchainStub() {
    this("name", new File("cCompilerExecutable"), new File("cppCompilerExecutable"));
  }

  public NativeToolchainStub(@NotNull String name, @Nullable File cCompilerExecutable, @Nullable File cppCompilerExecutable) {
    myName = name;
    myCCompilerExecutable = cCompilerExecutable;
    myCppCompilerExecutable = cppCompilerExecutable;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NativeToolchain)) {
      return false;
    }
    NativeToolchain stub = (NativeToolchain)o;
    return Objects.equals(getName(), stub.getName()) &&
           Objects.equals(getCCompilerExecutable(), stub.getCCompilerExecutable()) &&
           Objects.equals(getCppCompilerExecutable(), stub.getCppCompilerExecutable());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), getCCompilerExecutable(), getCppCompilerExecutable());
  }

  @Override
  public String toString() {
    return "NativeToolchainStub{" +
           "myName='" + myName + '\'' +
           ", myCCompilerExecutable=" + myCCompilerExecutable +
           ", myCppCompilerExecutable=" + myCppCompilerExecutable +
           "}";
  }
}
