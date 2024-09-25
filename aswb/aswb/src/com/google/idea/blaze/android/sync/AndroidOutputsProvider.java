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
package com.google.idea.blaze.android.sync;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.OutputsProvider;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import java.util.Collection;
import javax.annotation.Nullable;

/** Used to track blaze artifacts relevant to android projects. */
public class AndroidOutputsProvider implements OutputsProvider {
  @Override
  public boolean isActive(WorkspaceLanguageSettings languageSettings) {
    return languageSettings.isLanguageActive(LanguageClass.ANDROID);
  }

  @Override
  public Collection<ArtifactLocation> selectAllRelevantOutputs(TargetIdeInfo target) {
    if (target.getJavaToolchainIdeInfo() != null) {
      return target.getJavaToolchainIdeInfo().getJavacJars();
    }
    if (target.getAndroidSdkIdeInfo() != null) {
      return ImmutableList.of(target.getAndroidSdkIdeInfo().getAndroidJar());
    }
    if (target.getAndroidAarIdeInfo() != null) {
      return ImmutableList.of(target.getAndroidAarIdeInfo().getAar());
    }

    if (target.getAndroidIdeInfo() == null) {
      return ImmutableList.of();
    }
    AndroidIdeInfo androidInfo = target.getAndroidIdeInfo();

    ImmutableList.Builder<ArtifactLocation> list = ImmutableList.builder();
    androidInfo
        .getResources()
        .forEach(
            f -> {
              if (f.getAar() != null) {
                addArtifact(list, f.getAar());
              }
              addArtifact(list, f.getRoot());
            });
    addLibrary(list, androidInfo.getResourceJar());
    addLibrary(list, androidInfo.getIdlJar());
    addArtifact(list, androidInfo.getManifest());
    addArtifact(list, androidInfo.getRenderResolveJar());
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

  @Override
  public Collection<ArtifactLocation> selectOutputsToCache(TargetIdeInfo target) {
    // other outputs are handled separately to RemoteOutputsCache
    if (target.getJavaToolchainIdeInfo() != null) {
      return target.getJavaToolchainIdeInfo().getJavacJars();
    }

    AndroidIdeInfo androidIdeInfo = target.getAndroidIdeInfo();
    if (androidIdeInfo == null) {
      return ImmutableList.of();
    }

    ArtifactLocation manifest = androidIdeInfo.getManifest();
    if (manifest == null) {
      return ImmutableList.of();
    }

    return ImmutableList.of(manifest);
  }
}
