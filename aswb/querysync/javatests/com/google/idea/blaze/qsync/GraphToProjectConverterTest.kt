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
package com.google.idea.blaze.qsync

import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.Label.Companion.of
import com.google.idea.blaze.common.NoopContext
import com.google.idea.blaze.common.Output
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.common.TargetPatternCollection
import com.google.idea.blaze.qsync.GraphToProjectConverter.Companion.initializeProjectStructureData
import com.google.idea.blaze.qsync.java.PackageReader
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectPath.Companion.workspaceRelativeForTests
import com.google.idea.blaze.qsync.project.ProjectPath.ExternalRepositoryFinder.Companion.createEmptyForTests
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.QuerySyncLanguage
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdate
import com.google.idea.blaze.qsync.query.PackageSet
import com.google.idea.blaze.qsync.testdata.BuildGraphs
import com.google.idea.blaze.qsync.testdata.TestData
import com.google.idea.common.experiments.ExperimentService
import com.google.idea.common.experiments.MockExperimentService
import com.google.idea.testing.IntellijRule
import java.nio.file.Path
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class GraphToProjectConverterTest {
  @get:Rule val intellij: IntellijRule = IntellijRule()

  @Before
  fun setUp() {
    intellij.registerApplicationService(ExperimentService::class.java, MockExperimentService())
  }

  private val context: Context<*> =
    object : NoopContext() {
      override fun setHasError(): Unit = throw AssertionError()

      override fun <T : Output?> output(output: T?) {
        if (output is PrintOutput && output.outputType == PrintOutput.OutputType.ERROR) {
          throw AssertionError()
        }
      }
    }

  val emptyRepositoryFinder: ProjectPath.ExternalRepositoryFinder = createEmptyForTests()

  private fun toPrefixReader(basicReader: (Path) -> String): JavaPackagePrefixReader {
    val packageReader = PackageReader { context, file -> basicReader(file) }
    return JavaPackagePrefixReaderImpl(
      workspaceRoot = Path.of("/"),
      packageReader = packageReader,
      parallelPackageReader = QuerySyncTestUtils.SIMPLE_PARALLEL_PACKAGE_READER,
    ) {
      true
    }
  }

  @Test
  fun testSplitByRoot() {
    val sourcePackages = mapOf(Path.of("java/com/test/Class1.java") to "com.test")

    val roots = setOf(Path.of("java"), Path.of("javatests"))
    val converter =
      GraphToProjectConvertersForTests.create(
        javaPackagePrefixReader = toPrefixReader { key -> sourcePackages[key] ?: "" },
        projectIncludes = roots,
        languageClasses = setOf(QuerySyncLanguage.JVM),
      )

    val prefixes =
      mapOf(
        Path.of("java/com/test") to "com.test",
        Path.of("java/com/test/nested") to "com.test.nested",
        Path.of("java/com/root") to "",
        Path.of("javatests/com/one") to "prefix.com",
        Path.of("javatests/com/two") to "other.prefix",
      )

    val split = converter.splitByRoot(prefixes)

    Truth.assertThat(split.keys).containsExactlyElementsIn(roots)
    Truth.assertThat(split.get(Path.of("java")))
      .isEqualTo(
        mapOf(
          Path.of("com/test") to "com.test",
          Path.of("com/test/nested") to "com.test.nested",
          Path.of("com/root") to "",
        )
      )
    Truth.assertThat(split.get(Path.of("javatests")))
      .isEqualTo(mapOf(Path.of("com/one") to "prefix.com", Path.of("com/two") to "other.prefix"))
  }

  @Test
  fun testMergeCompatibleSourceRoots() {
    var roots =
      mapOf(
        Path.of("java") to
          mapOf(
            Path.of("com/google/d") to "com.google.d",
            Path.of("com/google/e") to "com.google.e",
            Path.of("com/google/e/z") to "z",
            Path.of("com/google/e/z/y") to "com.y",
          ),
        Path.of("javatests") to
          mapOf(
            Path.of("com/google/d") to "com.google.d",
            Path.of("com/google/e") to "com.google.e",
            Path.of("com/google/e/some/nested/root/com/google/x") to "com.google.x",
            Path.of("com/google/e/some/nested/root/com/google/x/y") to "com.google.x.y",
            Path.of("incompatible/a") to "com.a",
            Path.of("incompatible/a/b") to "com.odd",
            Path.of("incompatible/a/b/c/d") to "com.a.b.c.d",
          ),
      )
    roots = GraphToProjectConverter.mergeCompatibleSourceRoots(roots)

    Truth.assertThat(roots)
      .isEqualTo(
        mapOf(
          Path.of("java") to
            mapOf(
              Path.of("") to "",
              Path.of("com/google/e/z") to "z",
              Path.of("com/google/e/z/y") to "com.y",
            ),
          Path.of("javatests") to
            mapOf(
              Path.of("") to "",
              Path.of("com/google/e/some/nested/root") to "",
              Path.of("incompatible") to "com",
              Path.of("incompatible/a/b") to "com.odd",
              Path.of("incompatible/a/b/c") to "com.a.b.c",
            ),
        )
      )
  }

  @Test
  fun testCalculateRootSources_singleSource_atImportRoot() {
    val packages = PackageSet.of(Path.of("java/com/test"))
    val sourcePackages = mapOf(Path.of("java/com/test/Class1.java") to "com.test")

    val converter =
      GraphToProjectConvertersForTests.create(
        javaPackagePrefixReader = toPrefixReader { key -> sourcePackages[key] ?: "" },
        projectIncludes = setOf(Path.of("java/com/test")),
        languageClasses = setOf(QuerySyncLanguage.JVM),
      )

    val rootSources = converter.calculateJavaRootSources(context, sourcePackages.keys, packages)
    Truth.assertThat(rootSources.keys).containsExactly(Path.of("java/com/test"))
    Truth.assertThat(rootSources.get(Path.of("java/com/test")))
      .containsExactly(Path.of(""), "com.test")
  }

  @Test
  fun testCalculateRootSources_singleSource_belowImportRoot() {
    val packages = PackageSet.of(Path.of("java/com/test"))
    val sourcePackages =
      mapOf(Path.of("java/com/test/subpackage/Class1.java") to "com.test.subpackage")

    val converter =
      GraphToProjectConvertersForTests.create(
        javaPackagePrefixReader = toPrefixReader { key -> sourcePackages[key] ?: "" },
        projectIncludes = setOf(Path.of("java/com/test")),
        languageClasses = setOf(QuerySyncLanguage.JVM),
      )

    val rootSources = converter.calculateJavaRootSources(context, sourcePackages.keys, packages)
    Truth.assertThat(rootSources.keys).containsExactly(Path.of("java/com/test"))
    Truth.assertThat(rootSources.get(Path.of("java/com/test")))
      .containsExactly(Path.of(""), "com.test")
  }

  @Test
  fun testCalculateRootSources_multiSource_belowImportRoot() {
    val packages = PackageSet.of(Path.of("java/com/test"))
    val sourcePackages =
      mapOf(
        Path.of("java/com/test/package1/Class1.java") to "com.test.package1",
        Path.of("java/com/test/package2/Class2.java") to "com.test.package2",
      )

    val converter =
      GraphToProjectConvertersForTests.create(
        javaPackagePrefixReader = toPrefixReader { key -> sourcePackages[key] ?: "" },
        projectIncludes = setOf(Path.of("java/com/test")),
        languageClasses = setOf(QuerySyncLanguage.JVM),
      )

    val rootSources = converter.calculateJavaRootSources(context, sourcePackages.keys, packages)
    Truth.assertThat(rootSources.keys).containsExactly(Path.of("java/com/test"))
    Truth.assertThat(rootSources.get(Path.of("java/com/test")))
      .containsExactly(Path.of(""), "com.test")
  }

  @Test
  fun testCalculateRootSources_multiRoots() {
    val packages = PackageSet.of(Path.of("java/com/app"), Path.of("java/com/lib"))
    val sourcePackages =
      mapOf(
        Path.of("java/com/app/AppClass.java") to "com.app",
        Path.of("java/com/lib/LibClass.java") to "com.lib",
      )

    val converter =
      GraphToProjectConvertersForTests.create(
        javaPackagePrefixReader = toPrefixReader { key -> sourcePackages[key] ?: "" },
        projectIncludes = setOf(Path.of("java/com/app"), Path.of("java/com/lib")),
        languageClasses = setOf(QuerySyncLanguage.JVM),
      )

    val rootSources = converter.calculateJavaRootSources(context, sourcePackages.keys, packages)
    Truth.assertThat(rootSources.keys)
      .containsExactly(Path.of("java/com/app"), Path.of("java/com/lib"))
    Truth.assertThat(rootSources.get(Path.of("java/com/app")))
      .containsExactly(Path.of(""), "com.app")
    Truth.assertThat(rootSources.get(Path.of("java/com/lib")))
      .containsExactly(Path.of(""), "com.lib")
  }

  @Test
  fun testCalculateRootSources_multiSource_packageMismatch() {
    val packages = PackageSet.of(Path.of("java/com/test"), Path.of("java/com/test/package1"))
    val sourcePackages =
      mapOf(
        Path.of("java/com/test/package2/Class1.java") to "com.test.package2",
        Path.of("java/com/test/package1/Class2.java") to "com.test.oddpackage",
      )

    val converter =
      GraphToProjectConvertersForTests.create(
        javaPackagePrefixReader = toPrefixReader { key -> sourcePackages[key] ?: "" },
        projectIncludes = setOf(Path.of("java/com/test")),
        languageClasses = setOf(QuerySyncLanguage.JVM),
      )

    val rootSources = converter.calculateJavaRootSources(context, sourcePackages.keys, packages)
    Truth.assertThat(rootSources.keys).containsExactly(Path.of("java/com/test"))
    Truth.assertThat(rootSources.get(Path.of("java/com/test")))
      .containsExactly(Path.of(""), "com.test", Path.of("package1"), "com.test.oddpackage")
  }

  @Test
  fun testCalculateRootSources_multiSource_samePrefix() {
    val packages =
      PackageSet.of(Path.of("java/com/test/package1"), Path.of("java/com/test/package2"))
    val sourcePackages =
      mapOf(
        Path.of("java/com/test/package2/Class1.java") to "com.test.package2",
        Path.of("java/com/test/package1/Class2.java") to "com.test.package1",
      )

    val converter =
      GraphToProjectConvertersForTests.create(
        javaPackagePrefixReader = toPrefixReader { key -> sourcePackages[key] ?: "" },
        projectIncludes = setOf(Path.of("java/com/test")),
        languageClasses = setOf(QuerySyncLanguage.JVM),
      )

    val rootSources = converter.calculateJavaRootSources(context, sourcePackages.keys, packages)
    Truth.assertThat(rootSources.keys).containsExactly(Path.of("java/com/test"))
    Truth.assertThat(rootSources.get(Path.of("java/com/test")))
      .containsExactly(Path.of(""), "com.test")
  }

  @Test
  fun testCalculateRootSources_multiSource_nextedPrefixCompatible() {
    val packages = PackageSet.of(Path.of("java/com/test"), Path.of("java/com/test/package"))
    val sourcePackages =
      mapOf(
        Path.of("java/com/test/Class1.java") to "com.test",
        Path.of("java/com/test/package/Class2.java") to "com.test.package",
      )

    val converter =
      GraphToProjectConvertersForTests.create(
        javaPackagePrefixReader = toPrefixReader { key -> sourcePackages[key] ?: "" },
        projectIncludes = setOf(Path.of("java/com/test")),
        languageClasses = setOf(QuerySyncLanguage.JVM),
      )

    val rootSources = converter.calculateJavaRootSources(context, sourcePackages.keys, packages)
    Truth.assertThat(rootSources.keys).containsExactly(Path.of("java/com/test"))
    Truth.assertThat(rootSources.get(Path.of("java/com/test")))
      .containsExactly(Path.of(""), "com.test")
  }

  @Test
  fun testCalculateRootSources_multiSource_nestedPrefixIncompatible() {
    val packages = PackageSet.of(Path.of("java/com/test"), Path.of("java/com/test/package"))
    val sourcePackages =
      mapOf(
        Path.of("java/com/test/Class1.java") to "com.test.odd",
        Path.of("java/com/test/package/Class2.java") to "com.test.package",
      )

    val converter =
      GraphToProjectConvertersForTests.create(
        javaPackagePrefixReader = toPrefixReader { key -> sourcePackages[key] ?: "" },
        projectIncludes = setOf(Path.of("java/com/test")),
        languageClasses = setOf(QuerySyncLanguage.JVM),
      )

    val rootSources = converter.calculateJavaRootSources(context, sourcePackages.keys, packages)
    Truth.assertThat(rootSources.keys).containsExactly(Path.of("java/com/test"))
    Truth.assertThat(rootSources.get(Path.of("java/com/test")))
      .containsExactly(Path.of(""), "com.test.odd", Path.of("package"), "com.test.package")
  }

  @Test
  fun testCalculateRootSources_multiSource_rootPrefix() {
    val packages = PackageSet.of(Path.of("third_party/java"), Path.of("third_party/javatests"))

    val sourcePackages =
      mapOf(
        Path.of("third_party/java/com/test/Class1.java") to "com.test",
        Path.of("third_party/javatests/com/test/Class2.java") to "com.test",
      )

    val converter =
      GraphToProjectConvertersForTests.create(
        javaPackagePrefixReader = toPrefixReader { key -> sourcePackages[key] ?: "" },
        projectIncludes = setOf(Path.of("third_party")),
        languageClasses = setOf(QuerySyncLanguage.JVM),
      )

    val rootSources = converter.calculateJavaRootSources(context, sourcePackages.keys, packages)
    Truth.assertThat(rootSources.keys).containsExactly(Path.of("third_party"))
    Truth.assertThat(rootSources.get(Path.of("third_party")))
      .containsExactly(Path.of("java"), "", Path.of("javatests"), "")
  }

  @Test
  fun testCalculateRootSources_multiSource_repackagedSource() {
    val packages = PackageSet.of(Path.of("java/com/test"), Path.of("java/com/test/repackaged"))
    val sourcePackages =
      mapOf(
        Path.of("java/com/test/repackaged/com/foo/Class1.java") to "com.foo",
        Path.of("java/com/test/somepackage/Class2.java") to "com.test.somepackage",
      )

    val converter =
      GraphToProjectConvertersForTests.create(
        javaPackagePrefixReader = toPrefixReader { key -> sourcePackages[key] ?: "" },
        projectIncludes = setOf(Path.of("java/com/test")),
        languageClasses = setOf(QuerySyncLanguage.JVM),
      )

    val rootSources = converter.calculateJavaRootSources(context, sourcePackages.keys, packages)
    Truth.assertThat(rootSources.keys).containsExactly(Path.of("java/com/test"))
    Truth.assertThat(rootSources.get(Path.of("java/com/test")))
      .containsExactly(Path.of("repackaged"), "", Path.of(""), "com.test")
  }

  @Test
  fun testCalculateAndroidResourceDirectories_single_directory() {
    val sourceFiles =
      ImmutableSet.of(
        of("//java/com/test:AndroidManifest.xml"),
        of("//java/com/test:res/values/strings.xml"),
      )

    val androidResourceDirectories =
      GraphToProjectConverter.computeAndroidResourceDirectories(sourceFiles)
    Truth.assertThat(androidResourceDirectories).containsExactly(Path.of("java/com/test/res"))
  }

  @Test
  fun testCalculateAndroidResourceDirectories_multiple_directories() {
    val sourceFiles =
      ImmutableSet.of(
        of("//java/com/test:AndroidManifest.xml"),
        of("//java/com/test:res/values/strings.xml"),
        of("//java/com/test2:AndroidManifest.xml"),
        of("//java/com/test2:res/layout/some-activity.xml"),
        of("//java/com/test2:res/layout/another-activity.xml"),
      )

    val androidResourceDirectories =
      GraphToProjectConverter.computeAndroidResourceDirectories(sourceFiles)
    Truth.assertThat(androidResourceDirectories)
      .containsExactly(Path.of("java/com/test/res"), Path.of("java/com/test2/res"))
  }

  @Test
  fun testCalculateAndroidResourceDirectories_manifest_without_res_directory() {
    val sourceFiles =
      ImmutableSet.of(of("//java/com/nores:AndroidManifest.xml"), of("//java/com/nores:Foo.java"))

    val androidResourceDirectories =
      GraphToProjectConverter.computeAndroidResourceDirectories(sourceFiles)
    Truth.assertThat(androidResourceDirectories).isEmpty()
  }

  @Test
  fun testCalculateAndroidSourcePackages_rootWithEmptyPrefix() {
    val converter =
      GraphToProjectConvertersForTests.create(languageClasses = setOf(QuerySyncLanguage.JVM))

    val androidSourceFiles =
      listOf(Path.of("java/com/example/foo/Foo.java"), Path.of("java/com/example/bar/Bar.java"))
    val rootToPrefix = mapOf(Path.of("java/com/example") to mapOf(Path.of("") to "com.example"))

    val androidResourcePackages =
      converter.computeAndroidSourcePackages(androidSourceFiles, rootToPrefix)
    Truth.assertThat(androidResourcePackages).containsExactly("com.example.foo", "com.example.bar")
  }

  @Test
  fun testCalculateAndroidSourcePackages_emptyRootWithPrefix() {
    val converter =
      GraphToProjectConvertersForTests.create(languageClasses = setOf(QuerySyncLanguage.JVM))

    val androidSourceFiles =
      listOf(
        Path.of("some_project/java/com/example/foo/Foo.java"),
        Path.of("some_project/java/com/example/bar/Bar.java"),
      )
    val rootToPrefix = mapOf(Path.of("some_project") to mapOf(Path.of("java") to ""))

    val androidResourcePackages =
      converter.computeAndroidSourcePackages(androidSourceFiles, rootToPrefix)
    Truth.assertThat(androidResourcePackages).containsExactly("com.example.foo", "com.example.bar")
  }

  @Test
  fun testCalculateAndroidSourcePackages_emptyRootAndNonEmptyRoot() {
    val converter = GraphToProjectConvertersForTests.create()

    val androidSourceFiles =
      listOf(
        Path.of("some_project/java/com/example/foo/Foo.java"),
        Path.of("java/com/example/bar/Bar.java"),
      )
    val rootToPrefix =
      mapOf(
        Path.of("some_project") to mapOf(Path.of("java") to ""),
        Path.of("java/com/example") to mapOf(Path.of("") to "com.example"),
      )

    val androidResourcePackages =
      converter.computeAndroidSourcePackages(androidSourceFiles, rootToPrefix)
    Truth.assertThat(androidResourcePackages).containsExactly("com.example.foo", "com.example.bar")
  }

  @Test
  fun testCalculateAndroidSourcePackages_pathPrefixOfAnotherPath() {
    val converter = GraphToProjectConvertersForTests.create()

    val androidSourceFiles =
      listOf(
        Path.of("project/MainActivity.java"),
        Path.of("project/modules/test/com/example/bar/Bar.java"),
      )
    val rootToPrefix =
      mapOf(
        Path.of("project") to
          mapOf(Path.of("") to "com.root.project", Path.of("modules", "test") to "")
      )

    val androidResourcePackages =
      converter.computeAndroidSourcePackages(androidSourceFiles, rootToPrefix)
    Truth.assertThat(androidResourcePackages).containsExactly("com.root.project", "com.example.bar")
  }

  @Test
  fun testConvertProject_emptyProject() {
    val converter = GraphToProjectConvertersForTests.create()
    val project = converter.configureProject(BuildGraphData.EMPTY, emptyRepositoryFinder)
    Truth.assertThat(project.modules.size).isEqualTo(1)

    val workspaceModule = project.modules.get(0)
    Truth.assertThat(workspaceModule.name).isEqualTo(".workspace")

    Truth.assertThat(workspaceModule.contentEntries.size).isEqualTo(0)
  }

  @Test
  fun testConvertProject_buildGraphWithSingleImportRoot() {
    val workspaceImportDirectory = TestData.ROOT.resolve("nodeps")
    val buildGraphData =
      BlazeQueryParser(
          TargetPatternCollection.create(emptyList()),
          QuerySyncTestUtils.getQuerySummary(TestData.JAVA_LIBRARY_NO_DEPS_QUERY),
          QuerySyncTestUtils.NOOP_CONTEXT,
          emptySet(),
          BuildGraphData.ProtoRules.forTests()
        )
        .parse()
    val converter =
      GraphToProjectConvertersForTests.create(
        javaPackagePrefixReader = QuerySyncTestUtils.PATH_INFERRING_PREFIX_READER,
        projectIncludes = setOf(workspaceImportDirectory),
        languageClasses = setOf(QuerySyncLanguage.JVM),
      )

    val project = converter.configureProject(buildGraphData, emptyRepositoryFinder)

    // Sanity check
    Truth.assertThat(project.modules.size).isEqualTo(1)
    val workspaceModule = project.modules.get(0)

    Truth.assertThat(workspaceModule.contentEntries.size).isEqualTo(1)

    val javaContentEntry = workspaceModule.contentEntries.values.iterator().next()
    Truth.assertThat(javaContentEntry.root)
      .isEqualTo(workspaceRelativeForTests(workspaceImportDirectory))
    Truth.assertThat(javaContentEntry.sourceFolders.size).isEqualTo(1)

    val javaSource = javaContentEntry.sourceFolders.get(0)
    Truth.assertThat(javaSource.projectPath)
      .isEqualTo(workspaceRelativeForTests(workspaceImportDirectory))
    Truth.assertThat(javaSource.isGenerated).isFalse()
    Truth.assertThat(javaSource.isTest).isFalse()
  }

  @Test
  fun testTestSources() {
    val sourcePackages =
      mapOf(
        TestData.ROOT.resolve("nodeps/TestClassNoDeps.java") to
          "com.google.idea.blaze.qsync.testdata.nodeps"
      )

    val converter =
      GraphToProjectConvertersForTests.create(
        javaPackagePrefixReader = toPrefixReader { key -> sourcePackages[key] ?: "" },
        projectIncludes = setOf(TestData.ROOT.resolve("nodeps")),
        languageClasses = setOf(QuerySyncLanguage.JVM),
        testSources = setOf("tools/adt/idea/aswb/querysync/javatests/*"),
      )
    val buildGraphData: BuildGraphData =
      BuildGraphs.forTestProject(TestData.JAVA_LIBRARY_NO_DEPS_QUERY)
    val project = converter.configureProject(buildGraphData, emptyRepositoryFinder)

    Truth.assertThat(project.modules.size).isEqualTo(1)
    Truth.assertThat(project.modules.get(0).contentEntries.size).isEqualTo(1)
    val contentEntry = project.modules.get(0).contentEntries.values.iterator().next()
    Truth.assertThat(contentEntry.sourceFolders.size).isEqualTo(1)
    val sourceFolder = contentEntry.sourceFolders.get(0)

    Truth.assertThat(sourceFolder.projectPath)
      .isEqualTo(workspaceRelativeForTests(TestData.ROOT.resolve("nodeps")))

    Truth.assertThat(sourceFolder.isTest).isTrue()
  }

  @Test
  fun testProtoSourceFolders_returnsParentDirectory() {
    val sourcePackages = mapOf(Path.of("myproject/java/com/test/Class1.java") to "com.test")

    val converter =
      GraphToProjectConvertersForTests.create(
        javaPackagePrefixReader = toPrefixReader { key -> sourcePackages[key] ?: "" },
        projectIncludes = setOf(Path.of("myproject")),
        languageClasses = setOf(QuerySyncLanguage.JVM),
      )

    val additionalProtoSourceFolders =
      converter.nonJavaSourceFolders(setOf(Path.of("myproject/protos/test.proto")))
    Truth.assertThat(additionalProtoSourceFolders)
      .containsExactly(Path.of("myproject"), listOf(Path.of("protos")))
  }

  @Test
  fun testProtoSourceFolders_whenDirectoryIsExcluded_returnsEmpty() {
    val sourcePackages = mapOf(Path.of("myproject/java/com/test/Class1.java") to "com.test")

    val converter =
      GraphToProjectConvertersForTests.create(
        javaPackagePrefixReader = toPrefixReader { key -> sourcePackages[key] ?: "" },
        projectIncludes = setOf(Path.of("myproject")),
        projectExcludes = setOf(Path.of("myproject/excluded")),
        languageClasses = setOf(QuerySyncLanguage.JVM),
      )

    val additionalProtoSourceFolders =
      converter.nonJavaSourceFolders(setOf(Path.of("myproject/excluded/protos/excluded.proto")))
    Truth.assertThat(additionalProtoSourceFolders).isEmpty()
  }

  @Test
  fun testActiveLanguages_emptyProject() {
    val converter = GraphToProjectConvertersForTests.create()
    val project = converter.configureProject(BuildGraphData.EMPTY, emptyRepositoryFinder)
    Truth.assertThat(project.activeLanguages).isEmpty()
  }

  @Test
  fun testActiveLanguages_java() {
    val workspaceImportDirectory = TestData.ROOT.resolve("nodeps")
    val converter =
      GraphToProjectConvertersForTests.create(
        projectIncludes = setOf(workspaceImportDirectory),
        languageClasses = setOf(QuerySyncLanguage.JVM),
      )

    val buildGraphData =
      BlazeQueryParser(
          TargetPatternCollection.create(emptyList()),
          QuerySyncTestUtils.getQuerySummary(TestData.JAVA_LIBRARY_NO_DEPS_QUERY),
          QuerySyncTestUtils.NOOP_CONTEXT,
          emptySet(),
          BuildGraphData.ProtoRules.forTests()
        )
        .parse()
    val project = converter.configureProject(buildGraphData, emptyRepositoryFinder)

    Truth.assertThat(project.activeLanguages).contains(QuerySyncLanguage.JVM)
  }

  @Test
  fun testCreateProject_cc() {
    val workspaceImportDirectory = TestData.ROOT.resolve("cc")
    val converter =
      GraphToProjectConvertersForTests.create(
        projectIncludes = setOf(workspaceImportDirectory),
        isAndroidWorkspace = false,
      )

    val buildGraphData =
      BlazeQueryParser(
          TargetPatternCollection.create(emptyList()),
          QuerySyncTestUtils.getQuerySummary(TestData.CC_LIBRARY_QUERY),
          QuerySyncTestUtils.NOOP_CONTEXT,
          emptySet(),
          BuildGraphData.ProtoRules.forTests()
        )
        .parse()

    val project = converter.configureProject(buildGraphData, createEmptyForTests())

    val expectedProject =
      ProjectProto.Project(
        modules =
          listOf(
            ProjectProto.Module(
              name = ".workspace",
              contentEntries =
                mapOf(
                  workspaceRelativeForTests(workspaceImportDirectory) to
                    ProjectProto.ContentEntry(
                      root = workspaceRelativeForTests(workspaceImportDirectory),
                      sourceFolders =
                        listOf(
                          ProjectProto.SourceFolder(
                            projectPath = workspaceRelativeForTests(workspaceImportDirectory),
                            packagePrefix = "",
                            isGenerated = false,
                            isTest = false,
                          )
                        ),
                      excludes = emptyList(),
                    )
                ),
              androidResourceDirectories = emptyList(),
              isAndroidModule = false,
              androidSourcePackages = emptyList(),
              androidCustomPackages = emptyList(),
              androidExternalLibraries = emptyList(),
            )
          ),
        libraries = emptyMap(),
        artifactDirectories = ProjectProto.ArtifactDirectories.getDefaultInstance(),
        ccWorkspace = ProjectProto.CcWorkspace.getDefaultInstance(),
        activeLanguages = setOf(QuerySyncLanguage.CC),
      )

    Truth.assertThat(project).isEqualTo(expectedProject)
  }

  @Test
  fun testCreateProject_Android() {
    val workspaceImportDirectory = TestData.ROOT.resolve("android")
    val converter =
      GraphToProjectConvertersForTests.create(
        javaPackagePrefixReader = QuerySyncTestUtils.PATH_INFERRING_PREFIX_READER,
        projectIncludes = setOf(workspaceImportDirectory),
        languageClasses = setOf(QuerySyncLanguage.JVM),
      )
    val buildGraphData =
      BlazeQueryParser(
          TargetPatternCollection.create(emptyList()),
          QuerySyncTestUtils.getQuerySummary(TestData.ANDROID_LIB_QUERY),
          QuerySyncTestUtils.NOOP_CONTEXT,
          emptySet(),
          BuildGraphData.ProtoRules.forTests()
        )
        .parse()

    val project = converter.configureProject(buildGraphData, createEmptyForTests())

    val expectedProject =
      ProjectProto.Project(
        modules =
          listOf(
            ProjectProto.Module(
              name = ".workspace",
              contentEntries =
                mapOf(
                  workspaceRelativeForTests(workspaceImportDirectory) to
                    ProjectProto.ContentEntry(
                      root = workspaceRelativeForTests(workspaceImportDirectory),
                      sourceFolders =
                        listOf(
                          ProjectProto.SourceFolder(
                            projectPath = workspaceRelativeForTests(workspaceImportDirectory),
                            packagePrefix = "com.google.idea.blaze.qsync.testdata.android",
                            isGenerated = false,
                            isTest = false,
                          )
                        ),
                      excludes = emptyList(),
                    )
                ),
              androidResourceDirectories =
                listOf(workspaceRelativeForTests(TestData.ROOT.resolve("android/res"))),
              isAndroidModule = true,
              androidSourcePackages =
                emptyList(), // Expected to be empty as Build Graph does not contain resource
              // packages
              androidCustomPackages = emptyList(),
              androidExternalLibraries = emptyList(),
            )
          ),
        libraries = emptyMap(),
        artifactDirectories = ProjectProto.ArtifactDirectories.getDefaultInstance(),
        ccWorkspace = ProjectProto.CcWorkspace.getDefaultInstance(),
        activeLanguages = setOf(QuerySyncLanguage.JVM),
      )

    Truth.assertThat(project).isEqualTo(expectedProject)
  }
}

private fun GraphToProjectConverter.configureProject(
  graph: BuildGraphData,
  externalRepositoryFinder: ProjectPath.ExternalRepositoryFinder,
): ProjectProto.Project {
  val update = ProjectProtoUpdate(ProjectProto.Project.getDefaultInstance())
  configureProject(initializeProjectStructureData(graph), externalRepositoryFinder, update)
  configureProject(graph, externalRepositoryFinder, update)
  return update.build()
}