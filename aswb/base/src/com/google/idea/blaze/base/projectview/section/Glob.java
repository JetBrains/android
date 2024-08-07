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
package com.google.idea.blaze.base.projectview.section;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;

/** Glob matcher. */
public final class Glob implements ProtoWrapper<String>, Serializable {
  // still Serializable as part of ProjectViewSet
  private static final long serialVersionUID = 1L;

  private String pattern;
  private transient FileNameMatcher matcher;

  public Glob(String pattern) {
    this.pattern = pattern;
  }

  /** A set of globs */
  public static final class GlobSet {

    private final ImmutableList<Glob> globs;

    public GlobSet(Collection<Glob> globs) {
      this.globs = ImmutableList.copyOf(globs);
    }

    public boolean isEmpty() {
      return globs.isEmpty();
    }

    public boolean matches(String string) {
      for (Glob glob : globs) {
        if (glob.matches(string)) {
          return true;
        }
      }
      return false;
    }

    public static GlobSet fromProto(List<String> proto) {
      return new Glob.GlobSet(ProtoWrapper.map(proto, Glob::fromProto));
    }

    public ImmutableList<String> toProto() {
      return ProtoWrapper.mapToProtos(globs);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      GlobSet globSet = (GlobSet) o;
      return java.util.Objects.equals(globs, globSet.globs);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(globs);
    }
  }

  public boolean matches(String string) {
    if (matcher == null) {
      matcher = FileNameMatcherFactory.getInstance().createMatcher(pattern);
    }
    return matcher.accept(string);
  }

  @Override
  public String toString() {
    return pattern;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Glob glob = (Glob) o;
    return Objects.equal(pattern, glob.pattern);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(pattern);
  }

  public static Glob fromProto(String proto) {
    return new Glob(proto);
  }

  @Override
  public String toProto() {
    return pattern;
  }
}
