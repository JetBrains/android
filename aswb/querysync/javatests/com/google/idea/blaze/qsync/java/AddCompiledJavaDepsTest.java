/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.java;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.NoopContext;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.ArtifactTracker;
import com.google.idea.blaze.qsync.deps.ArtifactTracker.State;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectArtifact.ArtifactTransform;
import com.google.idea.blaze.qsync.testdata.ProjectProtos;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AddCompiledJavaDepsTest {
  @Test
  public void enable_library_provider_no_deps_built() throws Exception {
    AddCompiledJavaDeps javaDeps = new AddCompiledJavaDeps(true);
    no_deps_built(javaDeps);
  }

  @Test
  public void disable_library_provider_no_deps_built() throws Exception {
    AddCompiledJavaDeps javaDeps = new AddCompiledJavaDeps(false);
    no_deps_built(javaDeps);
  }

  private void no_deps_built(AddCompiledJavaDeps javaDeps) throws Exception {
    ProjectProto.Project original =
      ProjectProtos.forTestProject(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY);

    ProjectProtoUpdate update =
      new ProjectProtoUpdate(original, BuildGraphData.EMPTY, new NoopContext());
    javaDeps.update(update, State.EMPTY, new NoopContext());
    ProjectProto.Project newProject = update.build();
    assertThat(newProject.getLibraryList()).isEqualTo(original.getLibraryList());
    assertThat(newProject.getModulesList()).isEqualTo(original.getModulesList());
    assertThat(newProject.getArtifactDirectories().getDirectoriesMap().keySet())
      .containsExactly(".bazel/javadeps");
    assertThat(
      newProject
        .getArtifactDirectories()
        .getDirectoriesMap()
        .get(".bazel/javadeps")
        .getContentsMap())
      .isEmpty();
  }

  @Test
  public void enable_library_provider_dep_built() throws Exception {
    AddCompiledJavaDeps javaDeps = new AddCompiledJavaDeps(true);
    dep_built(javaDeps,
              ProjectProto.Library.newBuilder().setName("//java/com/google/common/collect:collect")
                .addClassesJar(
                  ProjectProto.JarDirectory.newBuilder().setPath(".bazel/javadeps/build-out/java/com/google/common/collect/libcollect.jar")
                    .setRecursive(false).build()).build());
  }

  @Test
  public void disable_library_provider_dep_built() throws Exception {
    AddCompiledJavaDeps javaDeps = new AddCompiledJavaDeps(false);
    dep_built(javaDeps,
              ProjectProto.Library.newBuilder().setName(".dependencies")
                .addClassesJar(ProjectProto.JarDirectory.newBuilder().setPath(".bazel/javadeps").setRecursive(true).build()).build());
  }

  private void dep_built(AddCompiledJavaDeps javaDeps, ProjectProto.Library... expectedLibraries) throws Exception {
    ProjectProto.Project original =
      ProjectProtos.forTestProject(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY);

    ArtifactTracker.State artifactState =
      ArtifactTracker.State.forJavaArtifacts(
        JavaArtifactInfo.empty(Label.of("//java/com/google/common/collect:collect")).toBuilder()
          .setJars(
            ImmutableList.of(
              BuildArtifact.create(
                "jardigest",
                Path.of("build-out/java/com/google/common/collect/libcollect.jar"),
                Label.of("//java/com/google/common/collect:collect"))))
          .build());

    ProjectProtoUpdate update =
      new ProjectProtoUpdate(original, BuildGraphData.EMPTY, new NoopContext());
    javaDeps.update(update, artifactState, new NoopContext());
    ProjectProto.Project newProject = update.build();
    assertThat(newProject.getLibraryList()).containsExactly(expectedLibraries);
    assertThat(newProject.getArtifactDirectories().getDirectoriesMap().keySet())
      .containsExactly(".bazel/javadeps");
    assertThat(
      newProject
        .getArtifactDirectories()
        .getDirectoriesMap()
        .get(".bazel/javadeps")
        .getContentsMap())
      .containsExactly(
        "build-out/java/com/google/common/collect/libcollect.jar",
        ProjectProto.ProjectArtifact.newBuilder()
          .setBuildArtifact(ProjectProto.BuildArtifact.newBuilder().setDigest("jardigest"))
          .setTransform(ArtifactTransform.COPY)
          .setTarget("//java/com/google/common/collect:collect")
          .build());
  }
}
