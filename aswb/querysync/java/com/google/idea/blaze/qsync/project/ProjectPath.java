/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.project;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectPath.Base;
import java.nio.file.Path;

/** A path to a project artifact, either in the workspace or the project directory. */
@AutoValue
public abstract class ProjectPath {

  public static final ProjectPath WORKSPACE_ROOT = ProjectPath.create(Root.WORKSPACE, Path.of(""));

  /** The root that this path is relative to. */
  public enum Root {
    WORKSPACE,
    PROJECT,
    ABSOLUTE,
  }

  public abstract Root rootType();

  public abstract Path relativePath();

  /** The path within {@link #relativePath()} to the root. Only relevant to jar files. */
  public abstract Path innerJarPath();

  public ProjectPath withInnerJarPath(Path inner) {
    return create(rootType(), relativePath(), inner);
  }

  public ProjectPath resolveChild(Path child) {
    Preconditions.checkState(!child.isAbsolute(), child);
    return create(rootType(), relativePath().resolve(child));
  }

  public ProjectProto.ProjectPath toProto() {
    ProjectProto.ProjectPath.Builder proto = ProjectProto.ProjectPath.newBuilder();
    switch (rootType()) {
      case WORKSPACE:
        proto.setBase(Base.WORKSPACE);
        break;
      case PROJECT:
        proto.setBase(Base.PROJECT);
        break;
      case ABSOLUTE:
        proto.setBase(Base.ABSOLUTE);
        break;
    }
    return proto.setPath(relativePath().toString()).setInnerPath(innerJarPath().toString()).build();
  }

  public static ProjectPath workspaceRelative(Path path) {
    return create(Root.WORKSPACE, path);
  }

  public static ProjectPath workspaceRelative(String path) {
    return workspaceRelative(Path.of(path));
  }

  public static ProjectPath projectRelative(Path path) {
    return create(Root.PROJECT, path);
  }

  public static ProjectPath projectRelative(String path) {
    return projectRelative(Path.of(path));
  }

  public static ProjectPath absolute(Path path) {
    Preconditions.checkArgument(path.isAbsolute(), path);
    return create(Root.ABSOLUTE, path);
  }

  public static ProjectPath absolute(String path) {
    return absolute(Path.of(path));
  }

  static Root convertContentRootBase(ProjectProto.ProjectPath.Base base) {
    switch (base) {
      case PROJECT:
        return Root.PROJECT;
      case WORKSPACE:
        return Root.WORKSPACE;
      case ABSOLUTE:
        return Root.ABSOLUTE;
      default:
        throw new IllegalArgumentException(base.name());
    }
  }

  public static ProjectPath create(ProjectProto.ProjectPath path) {
    return create(
        convertContentRootBase(path.getBase()),
        Path.of(path.getPath()),
        Path.of(path.getInnerPath()));
  }

  private static ProjectPath create(Root rootType, Path relativePath) {
    return new AutoValue_ProjectPath(rootType, relativePath, Path.of(""));
  }

  private static ProjectPath create(Root rootType, Path relativePath, Path innerJarPath) {
    return new AutoValue_ProjectPath(rootType, relativePath, innerJarPath);
  }

  /** Resolves {@link com.google.idea.blaze.qsync.project.ProjectPath} to an absolute path. */
  @AutoValue
  public abstract static class Resolver {

    @VisibleForTesting
    public static final Resolver EMPTY_FOR_TESTING = create(Path.of(""), Path.of(""));

    abstract Path workspaceRoot();

    abstract Path projectRoot();

    public static Resolver create(Path workspaceRoot, Path projectRoot) {
      return new AutoValue_ProjectPath_Resolver(workspaceRoot, projectRoot);
    }

    public Path resolve(ProjectPath path) {
      switch (path.rootType()) {
        case WORKSPACE:
          return workspaceRoot().resolve(path.relativePath());
        case PROJECT:
          return projectRoot().resolve(path.relativePath());
        case ABSOLUTE:
          return path.relativePath();
      }
      throw new IllegalStateException(path.rootType().name());
    }
  }
}
