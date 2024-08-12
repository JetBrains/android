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
package com.google.idea.blaze.android.sync.model;


import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import javax.annotation.concurrent.Immutable;

/** The result of a blaze import operation. */
@Immutable
public final class BlazeAndroidImportResult
    implements ProtoWrapper<ProjectData.BlazeAndroidImportResult> {
  public final ImmutableList<AndroidResourceModule> androidResourceModules;
  // map from library key to AarLibrary.
  // Key is generated according to ArtifactLocation of aar file location
  public final ImmutableMap<String, AarLibrary> aarLibraries;
  public final ImmutableList<BlazeJarLibrary> javacJarLibraries;
  public final ImmutableList<BlazeJarLibrary> resourceJars;

  public BlazeAndroidImportResult(
      ImmutableList<AndroidResourceModule> androidResourceModules,
      ImmutableMap<String, AarLibrary> aarLibraries,
      ImmutableList<BlazeJarLibrary> javacJarLibraries,
      ImmutableList<BlazeJarLibrary> resourcesJars) {
    this.androidResourceModules = androidResourceModules;
    this.aarLibraries = aarLibraries;
    this.javacJarLibraries = javacJarLibraries;
    this.resourceJars = resourcesJars;
  }

  static BlazeAndroidImportResult fromProto(ProjectData.BlazeAndroidImportResult proto) {
    ImmutableList<BlazeJarLibrary> javacJarLibraries;
    if (proto.getJavacJarLibrariesCount() > 0) {
      javacJarLibraries =
          ProtoWrapper.map(proto.getJavacJarLibrariesList(), BlazeJarLibrary::fromProto);
    } else if (proto.getJavacJarsCount() > 0) {
      javacJarLibraries =
          ProtoWrapper.map(
              proto.getJavacJarsList(),
              javacJar -> toBlazeJarLibrary(ArtifactLocation.fromProto(javacJar)));
    } else {
      javacJarLibraries =
          proto.hasJavacJar()
              ? ImmutableList.of(toBlazeJarLibrary(ArtifactLocation.fromProto(proto.getJavacJar())))
              : ImmutableList.of();
    }
    return new BlazeAndroidImportResult(
        ProtoWrapper.map(proto.getAndroidResourceModulesList(), AndroidResourceModule::fromProto),
        proto.getAarLibrariesList().stream()
            .map(AarLibrary::fromProto)
            .collect(
                ImmutableMap.toImmutableMap(
                    library -> LibraryKey.libraryNameFromArtifactLocation(library.aarArtifact),
                    Functions.identity())),
        javacJarLibraries,
        ProtoWrapper.map(proto.getResourceJarsList(), BlazeJarLibrary::fromProto));
  }

  private static BlazeJarLibrary toBlazeJarLibrary(ArtifactLocation classJar) {
    return new BlazeJarLibrary(
        new LibraryArtifact(null, classJar, ImmutableList.of()), /* targetKey= */ null);
  }

  @Override
  public ProjectData.BlazeAndroidImportResult toProto() {
    return ProjectData.BlazeAndroidImportResult.newBuilder()
        .addAllAndroidResourceModules(ProtoWrapper.mapToProtos(androidResourceModules))
        .addAllAarLibraries(ProtoWrapper.mapToProtos(aarLibraries.values()))
        .addAllJavacJarLibraries(ProtoWrapper.mapToProtos(javacJarLibraries))
        .addAllResourceLibraries(ProtoWrapper.mapToProtos(resourceJars))
        .build();
  }
}
