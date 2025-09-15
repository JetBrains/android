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
package com.google.idea.blaze.qsync.java

import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables
import com.google.common.io.ByteSource
import com.google.common.truth.Truth
import com.google.idea.blaze.common.Label.Companion.of
import com.google.idea.blaze.common.NoopContext
import com.google.idea.blaze.qsync.QuerySyncTestUtils
import com.google.idea.blaze.qsync.TestDataSyncRunner
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.deps.DependencyBuildContext
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate
import com.google.idea.blaze.qsync.deps.TargetBuildInfo
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.AarResPackage
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.testdata.TestData
import com.google.protobuf.TextFormat
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

@RunWith(JUnit4::class)
class AddDependencyAarsTest {
  @get:Rule
  val mockito: MockitoRule = MockitoJUnit.rule()

  private val syncer =
    TestDataSyncRunner(NoopContext(), QuerySyncTestUtils.PATH_INFERRING_PACKAGE_READER)

  private val aarPackageMetadata = AarPackageNameExtractor(null)

  @Test
  @Throws(Exception::class)
  fun no_deps_built() {
    val original = syncer.sync(TestData.ANDROID_LIB_QUERY)

    val addAars =
      AddDependencyAars(original.queryData().projectDefinition(), aarPackageMetadata)

    val update =
      ProjectProtoUpdate(original.project(), original.graph(), NoopContext())

    addAars.update(update, ArtifactTracker.State.EMPTY, NoopContext())
    val newProject = update.build()

    Truth.assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList())
    Truth.assertThat(newProject.getModulesList()).isEqualTo(original.project().getModulesList())
    Truth.assertThat(newProject.getArtifactDirectories().getDirectoriesMap().keys).isEmpty()
  }

  @Test
  @Throws(Exception::class)
  fun dep_aar_added() {
    val original = syncer.sync(TestData.ANDROID_LIB_QUERY)

    val addAars =
      AddDependencyAars(original.queryData().projectDefinition(), aarPackageMetadata)

    val update =
      ProjectProtoUpdate(original.project(), original.graph(), NoopContext())

    addAars.update(
      update,
      ArtifactTracker.State.forTargets(
        TargetBuildInfo.forJavaTarget(
          JavaArtifactInfo.empty(of("//path/to:dep")).toBuilder()
            .setIdeAars(
              ImmutableList.of(
                BuildArtifact.create(
                  "aardigest",
                  Path.of("path/to/dep.aar"),
                  of("//path/to:dep")
                )
                  .withMetadata(
                    AarResPackage(
                      "com.google.idea.blaze.qsync.testdata.android"
                    )
                  )
              )
            )
            .build(),
          DependencyBuildContext.NONE
        )
      ), NoopContext()
    )
    val newProject = update.build()

    Truth.assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList())
    Truth.assertThat(
          newProject.getModulesList().singleOrNull()?.getAndroidExternalLibrariesList()?.singleOrNull()
    )
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
              "  package_name: \"com.google.idea.blaze.qsync.testdata.android\""
            ),
          ProjectProto.ExternalAndroidLibrary::class.java
        )
      )

    Truth.assertThat(newProject.getArtifactDirectories())
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
              "}"
            ),
          ProjectProto.ArtifactDirectories::class.java
        )
      )
  }

  @Test
  @Throws(Exception::class)
  fun dep_aar_no_package_name_added() {
    val original = syncer.sync(TestData.ANDROID_LIB_QUERY)

    val addAars =
      AddDependencyAars(original.queryData().projectDefinition(), aarPackageMetadata)

    val update =
      ProjectProtoUpdate(original.project(), original.graph(), NoopContext())

    addAars.update(
      update,
      ArtifactTracker.State.forJavaArtifacts(
        ImmutableList.of(
          JavaArtifactInfo.empty(of("//path/to:dep")).toBuilder()
            .setIdeAars(
              ImmutableList.of(
                BuildArtifact.create(
                  "aardigest",
                  Path.of("path/to/dep.aar"),
                  of("//path/to:dep")
                )
              )
            )
            .build()
        )
      ), NoopContext()
    )
    val newProject = update.build()

    Truth.assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList())
    Truth.assertThat(
      newProject.getModulesList().singleOrNull()?.getAndroidExternalLibrariesList()?.singleOrNull()?.getPackageName()
    ).isEmpty()

    Truth.assertThat(newProject.getArtifactDirectories())
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
              "}"
            ),
          ProjectProto.ArtifactDirectories::class.java
        )
      )
  }
}
