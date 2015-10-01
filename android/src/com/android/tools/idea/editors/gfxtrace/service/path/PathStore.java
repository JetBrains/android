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
package com.android.tools.idea.editors.gfxtrace.service.path;

import org.jetbrains.annotations.Nullable;

public class PathStore<T extends Path> {
  private @Nullable T myPath;

  public boolean update(T path) {
    if (is(path)) {
      return false;
    }
    myPath = path;
    return true;
  }

  /**
   * Same as {@link #update(Path)}, but ignores passed null values.
   */
  public boolean updateIfNotNull(T path) {
    return path != null && update(path);
  }

  @Nullable
  public T getPath() {
    return myPath;
  }

  public boolean is(T path) {
    if (myPath == null) return path == null;
    return myPath.equals(path);
  }
}
