/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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

package com.google.idea.blaze.kotlin.sync;

import static com.google.idea.blaze.kotlin.sync.KotlinUtils.findToolchain;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.KotlinToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.common.experiments.BoolExperiment;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

/**
 * {@link LibrarySource} for Kotlin specific additions. Typically, libraries consumed via
 * java_import or kt_jvm_import will have handled by the Java Sync plugin. This library source
 * provides libararies from the kotlin toolchain, such as the Kotlin stdlib
 */
public final class KotlinLibrarySource extends LibrarySource.Adapter {

  /** Experiment to toggle attaching class jars instead of ijars for Kotlin SDK */
  @VisibleForTesting
  public static final BoolExperiment dontUseSdkIjars =
      new BoolExperiment("kotlin.sdk.no.ijar", true);

  private final BlazeProjectData blazeProjectData;
  private ImmutableList<BlazeJarLibrary> kotlinSdkLibraries = null;

  KotlinLibrarySource(BlazeProjectData blazeProjectData) {
    this.blazeProjectData = blazeProjectData;
  }

  private ImmutableList<BlazeJarLibrary> getKotlinSdkLibraries() {
    if (kotlinSdkLibraries == null) {
      kotlinSdkLibraries = findKotlinSdkLibraries(blazeProjectData);
    }
    return kotlinSdkLibraries;
  }

  @Override
  public ImmutableList<? extends BlazeLibrary> getLibraries() {
    if (!dontUseSdkIjars.getValue()) {
      return getKotlinSdkLibraries();
    }

    // Returns repackaged BlazeJarLibraries for SDK targets without interface jar to force-attach
    // class jars to the project. Attaching interface JARs of Kotlin SDK targets can lead to bugs
    // like b/174045216
    return getKotlinSdkLibraries().stream()
        .map(
            jar ->
                new BlazeJarLibrary(
                    new LibraryArtifact(
                        /* interfaceJar= */ null,
                        jar.libraryArtifact.getClassJar(),
                        jar.libraryArtifact.getSourceJars()),
                    jar.targetKey))
        .collect(ImmutableList.toImmutableList());
  }

  @Nullable
  @Override
  public Predicate<BlazeLibrary> getLibraryFilter() {
    if (!dontUseSdkIjars.getValue()) {
      return null; // null = nothing will be filtered
    }

    ImmutableSet<TargetKey> sdkTargetKeys =
        getKotlinSdkLibraries().stream()
            .map(lib -> lib.targetKey)
            .filter(Objects::nonNull)
            .collect(ImmutableSet.toImmutableSet());
    // Filter any library corresponding to Kotlin SDK targets that contain interface JARs
    return lib -> {
      if (!(lib instanceof BlazeJarLibrary)) {
        return true;
      }

      BlazeJarLibrary jarLibrary = (BlazeJarLibrary) lib;
      if (jarLibrary.targetKey == null) {
        return true;
      }

      if (!sdkTargetKeys.contains(jarLibrary.targetKey)) {
        return true; // non-sdk target
      }

      // The target is a Kotlin SDK target, only attach if it does NOT have an interface jar. ijars
      // of Kotlin SDK targets can lead to bugs like b/174045216.
      return jarLibrary.libraryArtifact.getInterfaceJar() == null;
    };
  }

  /**
   * Returns the Kotlin SDK {@link BlazeJarLibrary} entries from the given {@code blazeProjectData}
   */
  private static ImmutableList<BlazeJarLibrary> findKotlinSdkLibraries(
      BlazeProjectData blazeProjectData) {
    KotlinToolchainIdeInfo toolchain = findToolchain(blazeProjectData.getTargetMap());
    if (toolchain == null) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<BlazeJarLibrary> libraries = ImmutableList.builder();
    for (Label label : toolchain.getSdkTargets()) {
      TargetIdeInfo target = blazeProjectData.getTargetMap().get(TargetKey.forPlainTarget(label));
      if (target == null || target.getJavaIdeInfo() == null) {
        continue;
      }
      libraries.addAll(
          target.getJavaIdeInfo().getJars().stream()
              .map(jar -> new BlazeJarLibrary(jar, target.getKey()))
              .collect(Collectors.toList()));
    }
    return libraries.build();
  }
}
