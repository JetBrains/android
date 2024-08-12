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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.devtools.intellij.model.ProjectData;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import java.io.File;
import java.util.Collections;
import java.util.List;
import javax.annotation.concurrent.Immutable;

/** Corresponds to an IntelliJ content entry. */
@Immutable
public class BlazeContentEntry implements ProtoWrapper<ProjectData.BlazeContentEntry> {
  public final File contentRoot;
  public final ImmutableList<BlazeSourceDirectory> sources;

  public BlazeContentEntry(File contentRoot, ImmutableList<BlazeSourceDirectory> sources) {
    this.contentRoot = contentRoot;
    this.sources = sources;
  }

  public static BlazeContentEntry fromProto(ProjectData.BlazeContentEntry proto) {
    return new BlazeContentEntry(
        new File(proto.getContentRoot()),
        ProtoWrapper.map(proto.getSourcesList(), BlazeSourceDirectory::fromProto));
  }

  @Override
  public ProjectData.BlazeContentEntry toProto() {
    return ProjectData.BlazeContentEntry.newBuilder()
        .setContentRoot(contentRoot.getPath())
        .addAllSources(ProtoWrapper.mapToProtos(sources))
        .build();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BlazeContentEntry that = (BlazeContentEntry) o;
    return Objects.equal(contentRoot, that.contentRoot) && Objects.equal(sources, that.sources);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(contentRoot, sources);
  }

  @Override
  public String toString() {
    return "BlazeContentEntry {\n"
        + "  contentRoot: "
        + contentRoot
        + "\n"
        + "  sources: "
        + sources
        + "\n"
        + '}';
  }

  public static Builder builder(String contentRoot) {
    return new Builder(new File(contentRoot));
  }

  public static Builder builder(File contentRoot) {
    return new Builder(contentRoot);
  }

  /** Builder for content entries */
  public static class Builder {
    File contentRoot;
    List<BlazeSourceDirectory> sources = Lists.newArrayList();

    public Builder(File contentRoot) {
      this.contentRoot = contentRoot;
    }

    @CanIgnoreReturnValue
    public Builder addSource(BlazeSourceDirectory sourceDirectory) {
      this.sources.add(sourceDirectory);
      return this;
    }

    public BlazeContentEntry build() {
      Collections.sort(sources, BlazeSourceDirectory.COMPARATOR);
      return new BlazeContentEntry(contentRoot, ImmutableList.copyOf(sources));
    }
  }
}
