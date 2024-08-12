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
package com.google.idea.blaze.java.sync.model;

import com.google.common.base.Objects;
import com.google.devtools.intellij.model.ProjectData;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import java.io.File;
import java.util.Comparator;
import javax.annotation.concurrent.Immutable;

/** A source directory. */
@Immutable
public final class BlazeSourceDirectory implements ProtoWrapper<ProjectData.BlazeSourceDirectory> {
  public static final Comparator<BlazeSourceDirectory> COMPARATOR =
      (o1, o2) ->
          String.CASE_INSENSITIVE_ORDER.compare(
              o1.getDirectory().getPath(), o2.getDirectory().getPath());

  private final File directory;
  private final boolean isGenerated;
  private final boolean isResource;
  private final String packagePrefix;

  /** Bulider for source directory */
  public static class Builder {
    private final File directory;
    private String packagePrefix = "";
    private boolean isResource;
    private boolean isGenerated;

    private Builder(File directory) {
      this.directory = directory;
    }

    @CanIgnoreReturnValue
    public Builder setPackagePrefix(String packagePrefix) {
      this.packagePrefix = packagePrefix;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setResource(boolean isResource) {
      this.isResource = isResource;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setGenerated(boolean isGenerated) {
      this.isGenerated = isGenerated;
      return this;
    }

    public BlazeSourceDirectory build() {
      return new BlazeSourceDirectory(directory, isResource, isGenerated, packagePrefix);
    }
  }

  public static Builder builder(String directory) {
    return new Builder(new File(directory));
  }

  public static Builder builder(File directory) {
    return new Builder(directory);
  }

  private BlazeSourceDirectory(
      File directory, boolean isResource, boolean isGenerated, String packagePrefix) {
    this.directory = directory;
    this.isResource = isResource;
    this.isGenerated = isGenerated;
    this.packagePrefix = packagePrefix;
  }

  static BlazeSourceDirectory fromProto(ProjectData.BlazeSourceDirectory proto) {
    return new BlazeSourceDirectory(
        new File(proto.getDirectory()),
        proto.getIsResource(),
        proto.getIsGenerated(),
        proto.getPackagePrefix());
  }

  @Override
  public ProjectData.BlazeSourceDirectory toProto() {
    return ProjectData.BlazeSourceDirectory.newBuilder()
        .setDirectory(directory.getPath())
        .setIsResource(isResource)
        .setIsGenerated(isGenerated)
        .setPackagePrefix(packagePrefix)
        .build();
  }

  /** Returns the full path name of the root of a source directory. */
  public File getDirectory() {
    return directory;
  }

  /** Returns {@code true} if the directory contains resources. */
  public boolean isResource() {
    return isResource;
  }

  /** Returns {@code true} if the directory contains generated files. */
  public boolean isGenerated() {
    return isGenerated;
  }

  /**
   * Returns the package prefix for the directory. If the directory is a source root, such as a
   * "src" directory, then this returns an empty string.
   */
  public String getPackagePrefix() {
    return packagePrefix;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(directory, isResource, packagePrefix, isGenerated);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof BlazeSourceDirectory)) {
      return false;
    }
    BlazeSourceDirectory that = (BlazeSourceDirectory) other;
    return directory.equals(that.directory)
        && packagePrefix.equals(that.packagePrefix)
        && isResource == that.isResource
        && isGenerated == that.isGenerated;
  }

  @Override
  public String toString() {
    return "BlazeSourceDirectory {\n"
        + "  directory: "
        + directory
        + "\n"
        + "  isGenerated: "
        + isGenerated
        + "\n"
        + "  isResource: "
        + isResource
        + "\n"
        + "  packagePrefix: "
        + packagePrefix
        + "\n"
        + '}';
  }
}
