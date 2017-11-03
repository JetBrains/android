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

import com.android.builder.model.NativeSettings;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class NativeSettingsStub extends BaseStub implements NativeSettings {
  private final String myName;
  private final List<String> myCompilerFlags;

  public NativeSettingsStub() {
    this("name", Arrays.asList("flag1", "flag2"));
  }

  public NativeSettingsStub(String name, List<String> flags) {
    myName = name;
    myCompilerFlags = flags;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public List<String> getCompilerFlags() {
    return myCompilerFlags;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NativeSettings)) {
      return false;
    }
    NativeSettings settings = (NativeSettings)o;
    return Objects.equals(getName(), settings.getName()) &&
           Objects.equals(getCompilerFlags(), settings.getCompilerFlags());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), getCompilerFlags());
  }
}
