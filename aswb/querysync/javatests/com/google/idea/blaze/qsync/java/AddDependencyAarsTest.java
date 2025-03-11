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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteSource;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.NoopContext;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.QuerySyncTestUtils;
import com.google.idea.blaze.qsync.TestDataSyncRunner;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.ArtifactTracker.State;
import com.google.idea.blaze.qsync.deps.DependencyBuildContext;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.AarResPackage;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.ArtifactDirectories;
import com.google.idea.blaze.qsync.project.ProjectProto.ExternalAndroidLibrary;
import com.google.idea.blaze.qsync.testdata.TestData;
import com.google.protobuf.TextFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class AddDependencyAarsTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private final TestDataSyncRunner syncer =
      new TestDataSyncRunner(new NoopContext(), QuerySyncTestUtils.PATH_INFERRING_PACKAGE_READER);

  private final AarPackageNameExtractor aarPackageMetadata = new AarPackageNameExtractor(null);

  private static ByteSource emptyAarFile() throws IOException {
    ByteArrayOutputStream aarContents = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(aarContents)) {
      // Note: we don't need to write any contents since the manifest reading is mocked out - it
      // just need to exist.
      zos.putNextEntry(new ZipEntry("AndroidManifest.xml"));
    }
    return ByteSource.wrap(aarContents.toByteArray());
  }

  @Test
  public void no_deps_built() throws Exception {
    QuerySyncProjectSnapshot original = syncer.sync(TestData.ANDROID_LIB_QUERY);

    AddDependencyAars addAars =
        new AddDependencyAars(original.queryData().projectDefinition(), aarPackageMetadata);

    ProjectProtoUpdate update =
        new ProjectProtoUpdate(original.project(), original.graph(), new NoopContext());

    addAars.update(update, State.EMPTY, new NoopContext());
    ProjectProto.Project newProject = update.build();

    assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList());
    assertThat(newProject.getModulesList()).isEqualTo(original.project().getModulesList());
    assertThat(newProject.getArtifactDirectories().getDirectoriesMap().keySet()).isEmpty();
  }

  @Test
  public void dep_aar_added() throws Exception {
    QuerySyncProjectSnapshot original = syncer.sync(TestData.ANDROID_LIB_QUERY);

    AddDependencyAars addAars =
        new AddDependencyAars(original.queryData().projectDefinition(), aarPackageMetadata);

    ProjectProtoUpdate update =
        new ProjectProtoUpdate(original.project(), original.graph(), new NoopContext());

    addAars.update(
        update,
        State.forTargets(
            TargetBuildInfo.forJavaTarget(
                JavaArtifactInfo.empty(Label.of("//path/to:dep")).toBuilder()
                    .setIdeAars(
                        ImmutableList.of(
                            BuildArtifact.create(
                                    "aardigest",
                                    Path.of("path/to/dep.aar"),
                                    Label.of("//path/to:dep"))
                                .withMetadata(
                                    new AarResPackage(
                                        "com.google.idea.blaze.qsync.testdata.android"))))
                    .build(),
                DependencyBuildContext.NONE)), new NoopContext());
    ProjectProto.Project newProject = update.build();

    assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList());
    assertThat(
            Iterables.getOnlyElement(
                Iterables.getOnlyElement(newProject.getModulesList())
                    .getAndroidExternalLibrariesList()))
        .isEqualTo(
            TextFormat.parse(
                Joiner.on("\n")
                    .join(
                        "name: \"path_to_dep.aar\"",
                        "  location {",
                        "    path: \".bazel/buildout/path/to/dep.aar\"",
                        "    base: PROJECT",
                        "  }",
                        "  manifest_file {",
                        "    path: \".bazel/buildout/path/to/dep.aar/AndroidManifest.xml\"",
                        "    base: PROJECT",
                        "  }",
                        "  res_folder {",
                        "    path: \".bazel/buildout/path/to/dep.aar/res\"",
                        "    base: PROJECT",
                        "  }",
                        "  symbol_file {",
                        "    path: \".bazel/buildout/path/to/dep.aar/R.txt\"",
                        "    base: PROJECT",
                        "  }",
                        "  package_name: \"com.google.idea.blaze.qsync.testdata.android\""),
                ExternalAndroidLibrary.class));

    assertThat(newProject.getArtifactDirectories())
        .isEqualTo(
            TextFormat.parse(
                Joiner.on("\n")
                    .join(
                        "directories {",
                        "  key: \".bazel/buildout\"",
                        "  value {",
                        "    contents {",
                        "      key: \"path/to/dep.aar\"",
                        "      value {",
                        "        transform: UNZIP",
                        "        build_artifact {",
                        "          digest: \"aardigest\"",
                        "        }",
                        "        target: \"//path/to:dep\"",
                        "      }",
                        "    }",
                        "  }",
                        "}"),
                ArtifactDirectories.class));
  }

  @Test
  public void dep_aar_no_package_name_added() throws Exception {
    QuerySyncProjectSnapshot original = syncer.sync(TestData.ANDROID_LIB_QUERY);

    AddDependencyAars addAars =
        new AddDependencyAars(original.queryData().projectDefinition(), aarPackageMetadata);

    ProjectProtoUpdate update =
        new ProjectProtoUpdate(original.project(), original.graph(), new NoopContext());

    addAars.update(
        update,
        State.forJavaArtifacts(
            ImmutableList.of(
                JavaArtifactInfo.empty(Label.of("//path/to:dep")).toBuilder()
                    .setIdeAars(
                        ImmutableList.of(
                            BuildArtifact.create(
                                "aardigest",
                                Path.of("path/to/dep.aar"),
                                Label.of("//path/to:dep"))))
                    .build())), new NoopContext());
    ProjectProto.Project newProject = update.build();

    assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList());
    assertThat(
            Iterables.getOnlyElement(
                    Iterables.getOnlyElement(newProject.getModulesList())
                        .getAndroidExternalLibrariesList())
                .getPackageName())
        .isEmpty();

    assertThat(newProject.getArtifactDirectories())
        .isEqualTo(
            TextFormat.parse(
                Joiner.on("\n")
                    .join(
                        "directories {",
                        "  key: \".bazel/buildout\"",
                        "  value {",
                        "    contents {",
                        "      key: \"path/to/dep.aar\"",
                        "      value {",
                        "        transform: UNZIP",
                        "        build_artifact {",
                        "          digest: \"aardigest\"",
                        "        }",
                        "        target: \"//path/to:dep\"",
                        "      }",
                        "    }",
                        "  }",
                        "}"),
                ArtifactDirectories.class));
  }
}
