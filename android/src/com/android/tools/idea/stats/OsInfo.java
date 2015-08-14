/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.stats;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class OsInfo {
  @NotNull
  private String myOsName;
  @Nullable
  private String myOsVersion;

  public OsInfo setOsName(@NotNull String osName) {
    myOsName = osName;
    return this;
  }

  public OsInfo setOsVersion(@Nullable String osVersion) {
    myOsVersion = osVersion;
    return this;
  }

  @NotNull
  public String getOsName() {
    return myOsName;
  }

  @Nullable
  public String getOsVersion() {
    return myOsVersion;
  }

  public String getOsFull() {
    String os = myOsName;
    if (myOsVersion != null) {
      os += "-" + myOsVersion;
    }
    return os;
  }
}
