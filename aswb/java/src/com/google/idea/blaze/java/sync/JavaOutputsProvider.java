/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.OutputsProvider;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import java.util.Collection;
import javax.annotation.Nullable;

/** Used to track blaze artifacts relevant to java projects. */
class JavaOutputsProvider implements OutputsProvider {

  @Override
  public boolean isActive(WorkspaceLanguageSettings languageSettings) {
    return languageSettings.isLanguageActive(LanguageClass.JAVA);
  }

  @Override
  public Collection<ArtifactLocation> selectOutputsToCache(TargetIdeInfo target) {
    // everything other than the package manifest is cached separately to RemoteOutputsCache
    if (target.getJavaIdeInfo() == null || target.getJavaIdeInfo().getPackageManifest() == null) {
      return ImmutableList.of();
    }
    return ImmutableList.of(target.getJavaIdeInfo().getPackageManifest());
  }

  @Override
  public Collection<ArtifactLocation> selectAllRelevantOutputs(TargetIdeInfo target) {
    if (target.getJavaIdeInfo() == null) {
      return ImmutableList.of();
    }
    JavaIdeInfo javaInfo = target.getJavaIdeInfo();
    ImmutableList.Builder<ArtifactLocation> list = ImmutableList.builder();
    javaInfo.getSources().forEach(s -> addArtifact(list, s));
    javaInfo.getJars().forEach(l -> addLibrary(list, l));
    javaInfo.getGeneratedJars().forEach(l -> addLibrary(list, l));
    javaInfo.getPluginProcessorJars().forEach(l -> addLibrary(list, l));
    addLibrary(list, javaInfo.getFilteredGenJar());
    addArtifact(list, javaInfo.getPackageManifest());
    addArtifact(list, javaInfo.getJdepsFile());

    return list.build();
  }

  private static void addLibrary(
      ImmutableList.Builder<ArtifactLocation> list, @Nullable LibraryArtifact library) {
    if (library != null) {
      addArtifact(list, library.getInterfaceJar());
      addArtifact(list, library.getClassJar());
      library.getSourceJars().forEach(j -> addArtifact(list, j));
    }
  }

  private static void addArtifact(
      ImmutableList.Builder<ArtifactLocation> list, @Nullable ArtifactLocation artifact) {
    if (artifact != null) {
      list.add(artifact);
    }
  }
}
