/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.roots.ex;

import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Shim for #api222 compat */
public abstract class ProjectRootManagerExWrapper extends ProjectRootManagerEx {
  @Override
  public void makeRootsChange(
      @NotNull Runnable runnable, @NotNull RootsChangeRescanningInfo rootsChangeRescanningInfo) {}

  @Override
  public @NotNull AutoCloseable withRootsChange(
      @NotNull RootsChangeRescanningInfo rootsChangeRescanningInfo) {
    return new AutoCloseable() {
      @Override
      public void close() throws Exception {}
    };
  }

  @Override
  public List<VirtualFile> markRootsForRefresh() {
    return null;
  }
}
