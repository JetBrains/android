/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.model;

import com.google.common.base.Objects;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.intellij.openapi.project.Project;
import javax.annotation.concurrent.Immutable;

/** A model object for something that will map to an IntelliJ library. */
@Immutable
public abstract class BlazeLibrary implements ProtoWrapper<ProjectData.BlazeLibrary> {
  public final LibraryKey key;

  protected BlazeLibrary(LibraryKey key) {
    this.key = key;
  }

  @Override
  public ProjectData.BlazeLibrary toProto() {
    return ProjectData.BlazeLibrary.newBuilder().setLibraryKey(key.toProto()).build();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(key);
  }

  @Override
  public String toString() {
    return key.toString();
  }

  /**
   * Returns a {@link LibraryFilesProvider} that can be used to update library model content. Many
   * BlazeLibrary instances may return the same LibraryFilesProvider, and that all BlazeLibraries
   * with the same FilesProvider will be mapped to the same library in the IJ project model.
   */
  public abstract LibraryFilesProvider getDefaultLibraryFilesProvider(Project project);

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof BlazeLibrary)) {
      return false;
    }

    BlazeLibrary that = (BlazeLibrary) other;
    return Objects.equal(key, that.key);
  }

  public abstract String getExtension();
}
