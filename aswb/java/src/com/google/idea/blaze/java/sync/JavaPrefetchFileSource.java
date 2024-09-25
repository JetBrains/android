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
package com.google.idea.blaze.java.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactResolver;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.prefetch.PrefetchFileSource;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.libraries.BlazeLibraryCollector;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.libraries.JarCache;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.Set;

/** Adds the jars to prefetch. */
public class JavaPrefetchFileSource implements PrefetchFileSource {
  @Override
  public void addFilesToPrefetch(
      Project project,
      ProjectViewSet projectViewSet,
      ImportRoots importRoots,
      BlazeProjectData blazeProjectData,
      Set<File> files) {
    BlazeJavaSyncData syncData = blazeProjectData.getSyncState().get(BlazeJavaSyncData.class);
    if (syncData == null) {
      return;
    }
    // If we have a local jar cache we don't need to prefetch anything
    if (JarCache.getInstance(project).isEnabled()) {
      return;
    }
    Collection<BlazeLibrary> libraries =
        BlazeLibraryCollector.getLibraries(projectViewSet, blazeProjectData);
    ArtifactLocationDecoder decoder = blazeProjectData.getArtifactLocationDecoder();
    for (BlazeLibrary library : libraries) {
      if (!(library instanceof BlazeJarLibrary)) {
        continue;
      }
      files.addAll(
          OutputArtifactResolver.resolveAll(
              project, decoder, jarArtifacts((BlazeJarLibrary) library)));
    }
  }

  private static Collection<ArtifactLocation> jarArtifacts(BlazeJarLibrary library) {
    return ImmutableList.<ArtifactLocation>builder()
        .add(library.libraryArtifact.jarForIntellijLibrary())
        .addAll(library.libraryArtifact.getSourceJars())
        .build();
  }

  @Override
  public Set<String> prefetchFileExtensions() {
    return ImmutableSet.of("java");
  }
}
