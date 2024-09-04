/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.projectview.stubs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.stubs.BinaryFileStubBuilder;
import com.intellij.psi.stubs.Stub;
import com.intellij.util.indexing.FileContent;
import javax.annotation.Nullable;

/** Empty stub builder to suppress errors when IntelliJ is looking for stubs. */
public class ProjectViewFileStubBuilder implements BinaryFileStubBuilder {
  private static final int STUB_VERSION = 0;

  @Override
  public boolean acceptsFile(VirtualFile file) {
    return false;
  }

  @Nullable
  @Override
  public Stub buildStubTree(FileContent fileContent) {
    return null;
  }

  @Override
  public int getStubVersion() {
    return STUB_VERSION;
  }
}
