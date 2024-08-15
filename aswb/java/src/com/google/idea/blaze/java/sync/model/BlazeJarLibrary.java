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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.LibraryFilesProvider;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.java.libraries.AttachedSourceJarManager;
import com.google.idea.blaze.java.libraries.JarCache;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/** An immutable reference to a .jar required by a rule. */
@Immutable
public final class BlazeJarLibrary extends BlazeLibrary {
  private static final Logger logger = Logger.getInstance(BlazeJarLibrary.class);

  public final LibraryArtifact libraryArtifact;
  @Nullable public final TargetKey targetKey;

  public BlazeJarLibrary(LibraryArtifact libraryArtifact, @Nullable TargetKey targetKey) {
    super(LibraryKey.fromArtifactLocation(libraryArtifact.jarForIntellijLibrary()));
    this.libraryArtifact = libraryArtifact;
    this.targetKey = targetKey;
  }

  public static BlazeJarLibrary fromProto(ProjectData.BlazeLibrary proto) {
    return new BlazeJarLibrary(
        LibraryArtifact.fromProto(proto.getBlazeJarLibrary().getLibraryArtifact()),
        proto.getBlazeJarLibrary().hasTargetKey()
            ? TargetKey.fromProto(proto.getBlazeJarLibrary().getTargetKey())
            : null);
  }

  @Override
  public ProjectData.BlazeLibrary toProto() {
    ProjectData.BlazeJarLibrary.Builder builder =
        ProjectData.BlazeJarLibrary.newBuilder().setLibraryArtifact(libraryArtifact.toProto());
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setTargetKey, targetKey);
    return super.toProto().toBuilder().setBlazeJarLibrary(builder).build();
  }

  @Override
  public LibraryFilesProvider getDefaultLibraryFilesProvider(Project project) {
    return new DefaultJarLibraryFilesProvider(project);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), libraryArtifact);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof BlazeJarLibrary)) {
      return false;
    }

    BlazeJarLibrary that = (BlazeJarLibrary) other;

    return super.equals(other) && Objects.equals(libraryArtifact, that.libraryArtifact);
  }

  @Override
  public String getExtension() {
    return ".jar";
  }

  /** An implementation of {@link LibraryFilesProvider} for {@link BlazeJarLibrary}. */
  private final class DefaultJarLibraryFilesProvider implements LibraryFilesProvider {
    private final Project project;

    DefaultJarLibraryFilesProvider(Project project) {
      this.project = project;
    }

    @Override
    public String getName() {
      return BlazeJarLibrary.this.key.getIntelliJLibraryName();
    }

    @Override
    public ImmutableList<File> getClassFiles(BlazeProjectData blazeProjectData) {
      File classJar =
          JarCache.getInstance(project)
              .getCachedJar(blazeProjectData.getArtifactLocationDecoder(), BlazeJarLibrary.this);
      if (classJar == null) {
        logger.warn("No local file found for " + libraryArtifact);
        return ImmutableList.of();
      }
      return ImmutableList.of(classJar);
    }

    @Override
    public ImmutableList<File> getSourceFiles(BlazeProjectData blazeProjectData) {
      AttachedSourceJarManager sourceJarManager = AttachedSourceJarManager.getInstance(project);
      JarCache jarCache = JarCache.getInstance(project);
      for (AttachSourcesFilter decider : AttachSourcesFilter.EP_NAME.getExtensions()) {
        if (decider.shouldAlwaysAttachSourceJar(BlazeJarLibrary.this)) {
          sourceJarManager.setHasSourceJarAttached(key, true);
        }
      }
      if (!sourceJarManager.hasSourceJarAttached(key)) {
        return ImmutableList.of();
      }
      return libraryArtifact.getSourceJars().stream()
          .map(
              srcJar ->
                  jarCache.getCachedSourceJar(
                      blazeProjectData.getArtifactLocationDecoder(), srcJar))
          .filter(Objects::nonNull)
          .collect(toImmutableList());
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof DefaultJarLibraryFilesProvider)) {
        return false;
      }

      DefaultJarLibraryFilesProvider that = (DefaultJarLibraryFilesProvider) other;
      return Objects.equals(project, that.project) && getName().equals(that.getName());
    }

    @Override
    public int hashCode() {
      return Objects.hash(project, getName());
    }
  }
}
