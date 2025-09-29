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
package com.google.idea.blaze.qsync;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.NOOP_CONTEXT;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.getQuerySummary;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.NoopContext;
import com.google.idea.blaze.common.Output;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.java.PackageReader;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.QuerySyncLanguage;
import com.google.idea.blaze.qsync.query.PackageSet;
import com.google.idea.blaze.qsync.testdata.BuildGraphs;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.nio.file.Path;
import java.util.function.Function;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@SuppressWarnings("DuplicateExpressions")
@RunWith(JUnit4.class)
public class GraphToProjectConverterTest {

  private final Context<?> context =
      new NoopContext() {
        @Override
        public void setHasError() {
          throw new AssertionError();
        }

        @Override
        public <T extends Output> void output(T output) {
          if (output instanceof PrintOutput po && po.outputType() == PrintOutput.OutputType.ERROR) {
            throw new AssertionError();
          }
        }
      };

  private JavaPackagePrefixReader toPrefixReader(Function<Path, String> basicReader) {
    PackageReader packageReader = (context, file) -> basicReader.apply(file);
    return new JavaPackagePrefixReaderImpl(
        Path.of("/"), packageReader, QuerySyncTestUtils.SIMPLE_PARALLEL_PACKAGE_READER, (p) -> true);
  }

  @Test
  public void testSplitByRoot() {
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(Path.of("java/com/test/Class1.java"), "com.test");

    ImmutableSet<Path> roots = ImmutableSet.of(Path.of("java"), Path.of("javatests"));
    GraphToProjectConverter converter =
        GraphToProjectConvertersForTests.builder()
            .setPrefixReader(toPrefixReader(sourcePackages::get))
            .setProjectIncludes(roots)
            .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JVM))
            .build();

    ImmutableMap<Path, String> prefixes =
        ImmutableMap.of(
            Path.of("java/com/test"), "com.test",
            Path.of("java/com/test/nested"), "com.test.nested",
            Path.of("java/com/root"), "",
            Path.of("javatests/com/one"), "prefix.com",
            Path.of("javatests/com/two"), "other.prefix");

    ImmutableMap<Path, ImmutableMap<Path, String>> split = converter.splitByRoot(prefixes);

    assertThat(split.keySet()).containsExactlyElementsIn(roots);
    assertThat(split.get(Path.of("java")))
        .containsExactly(
            Path.of("com/test"), "com.test",
            Path.of("com/test/nested"), "com.test.nested",
            Path.of("com/root"), "");
    assertThat(split.get(Path.of("javatests")))
        .containsExactly(
            Path.of("com/one"), "prefix.com",
            Path.of("com/two"), "other.prefix");
  }

  @Test
  public void testMergeCompatibleSourceRoots() {
    ImmutableMap<Path, ImmutableMap<Path, String>> roots =
        ImmutableMap.of(
            Path.of("java"),
            ImmutableMap.of(
                Path.of("com/google/d"), "com.google.d",
                Path.of("com/google/e"), "com.google.e",
                Path.of("com/google/e/z"), "z",
                Path.of("com/google/e/z/y"), "com.y"),
            Path.of("javatests"),
            ImmutableMap.of(
                Path.of("com/google/d"), "com.google.d",
                Path.of("com/google/e"), "com.google.e",
                Path.of("com/google/e/some/nested/root/com/google/x"), "com.google.x",
                Path.of("com/google/e/some/nested/root/com/google/x/y"), "com.google.x.y",
                Path.of("incompatible/a"), "com.a",
                Path.of("incompatible/a/b"), "com.odd",
                Path.of("incompatible/a/b/c/d"), "com.a.b.c.d"));
    roots = GraphToProjectConverter.mergeCompatibleSourceRoots(roots);

    assertThat(roots)
        .isEqualTo(
            ImmutableMap.of(
                Path.of("java"),
                ImmutableMap.of(
                    Path.of(""), "",
                    Path.of("com/google/e/z"), "z",
                    Path.of("com/google/e/z/y"), "com.y"),
                Path.of("javatests"),
                ImmutableMap.of(
                    Path.of(""), "",
                    Path.of("com/google/e/some/nested/root"), "",
                    Path.of("incompatible"), "com",
                    Path.of("incompatible/a/b"), "com.odd",
                    Path.of("incompatible/a/b/c"), "com.a.b.c")));
  }

  @Test
  public void testCalculateRootSources_singleSource_atImportRoot() throws Exception {
    PackageSet packages = PackageSet.of(Path.of("java/com/test"));
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(Path.of("java/com/test/Class1.java"), "com.test");

    GraphToProjectConverter converter =
        GraphToProjectConvertersForTests.builder()
            .setPrefixReader(toPrefixReader(sourcePackages::get))
            .setProjectIncludes(ImmutableSet.of(Path.of("java/com/test")))
            .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JVM))
            .build();

    final var rootSources =
        converter.calculateJavaRootSources(context, sourcePackages.keySet(), packages);
    assertThat(rootSources.keySet()).containsExactly(Path.of("java/com/test"));
    assertThat(rootSources.get(Path.of("java/com/test"))).containsExactly(Path.of(""), "com.test");
  }

  @Test
  public void testCalculateRootSources_singleSource_belowImportRoot() throws Exception {
    PackageSet packages = PackageSet.of(Path.of("java/com/test"));
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(Path.of("java/com/test/subpackage/Class1.java"), "com.test.subpackage");

    GraphToProjectConverter converter =
        GraphToProjectConvertersForTests.builder()
            .setPrefixReader(toPrefixReader(sourcePackages::get))
            .setProjectIncludes(ImmutableSet.of(Path.of("java/com/test")))
            .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JVM))
            .build();

    final var rootSources =
        converter.calculateJavaRootSources(context, sourcePackages.keySet(), packages);
    assertThat(rootSources.keySet()).containsExactly(Path.of("java/com/test"));
    assertThat(rootSources.get(Path.of("java/com/test"))).containsExactly(Path.of(""), "com.test");
  }

  @Test
  public void testCalculateRootSources_multiSource_belowImportRoot() throws Exception {
    PackageSet packages = PackageSet.of(Path.of("java/com/test"));
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("java/com/test/package1/Class1.java"), "com.test.package1",
            Path.of("java/com/test/package2/Class2.java"), "com.test.package2");

    GraphToProjectConverter converter =
        GraphToProjectConvertersForTests.builder()
            .setPrefixReader(toPrefixReader(sourcePackages::get))
            .setProjectIncludes(ImmutableSet.of(Path.of("java/com/test")))
            .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JVM))
            .build();

    final var rootSources =
        converter.calculateJavaRootSources(context, sourcePackages.keySet(), packages);
    assertThat(rootSources.keySet()).containsExactly(Path.of("java/com/test"));
    assertThat(rootSources.get(Path.of("java/com/test"))).containsExactly(Path.of(""), "com.test");
  }

  @Test
  public void testCalculateRootSources_multiRoots() throws Exception {
    PackageSet packages = PackageSet.of(Path.of("java/com/app"), Path.of("java/com/lib"));
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("java/com/app/AppClass.java"), "com.app",
            Path.of("java/com/lib/LibClass.java"), "com.lib");

    GraphToProjectConverter converter =
        GraphToProjectConvertersForTests.builder()
            .setPrefixReader(toPrefixReader(sourcePackages::get))
            .setProjectIncludes(ImmutableSet.of(Path.of("java/com/app"), Path.of("java/com/lib")))
            .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JVM))
            .build();

    final var rootSources =
        converter.calculateJavaRootSources(context, sourcePackages.keySet(), packages);
    assertThat(rootSources.keySet())
        .containsExactly(Path.of("java/com/app"), Path.of("java/com/lib"));
    assertThat(rootSources.get(Path.of("java/com/app"))).containsExactly(Path.of(""), "com.app");
    assertThat(rootSources.get(Path.of("java/com/lib"))).containsExactly(Path.of(""), "com.lib");
  }

  @Test
  public void testCalculateRootSources_multiSource_packageMismatch() throws Exception {
    PackageSet packages =
        PackageSet.of(Path.of("java/com/test"), Path.of("java/com/test/package1"));
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("java/com/test/package2/Class1.java"), "com.test.package2",
            Path.of("java/com/test/package1/Class2.java"), "com.test.oddpackage");

    GraphToProjectConverter converter =
        GraphToProjectConvertersForTests.builder()
            .setPrefixReader(toPrefixReader(sourcePackages::get))
            .setProjectIncludes(ImmutableSet.of(Path.of("java/com/test")))
            .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JVM))
            .build();

    final var rootSources =
        converter.calculateJavaRootSources(context, sourcePackages.keySet(), packages);
    assertThat(rootSources.keySet()).containsExactly(Path.of("java/com/test"));
    assertThat(rootSources.get(Path.of("java/com/test")))
        .containsExactly(
            Path.of(""), "com.test",
            Path.of("package1"), "com.test.oddpackage");
  }

  @Test
  public void testCalculateRootSources_multiSource_samePrefix() throws Exception {
    PackageSet packages =
        PackageSet.of(Path.of("java/com/test/package1"), Path.of("java/com/test/package2"));
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("java/com/test/package2/Class1.java"), "com.test.package2",
            Path.of("java/com/test/package1/Class2.java"), "com.test.package1");

    GraphToProjectConverter converter =
        GraphToProjectConvertersForTests.builder()
            .setPrefixReader(toPrefixReader(sourcePackages::get))
            .setProjectIncludes(ImmutableSet.of(Path.of("java/com/test")))
            .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JVM))
            .build();

    final var rootSources =
        converter.calculateJavaRootSources(context, sourcePackages.keySet(), packages);
    assertThat(rootSources.keySet()).containsExactly(Path.of("java/com/test"));
    assertThat(rootSources.get(Path.of("java/com/test"))).containsExactly(Path.of(""), "com.test");
  }

  @Test
  public void testCalculateRootSources_multiSource_nextedPrefixCompatible() throws Exception {
    PackageSet packages = PackageSet.of(Path.of("java/com/test"), Path.of("java/com/test/package"));
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("java/com/test/Class1.java"), "com.test",
            Path.of("java/com/test/package/Class2.java"), "com.test.package");

    GraphToProjectConverter converter =
        GraphToProjectConvertersForTests.builder()
            .setPrefixReader(toPrefixReader(sourcePackages::get))
            .setProjectIncludes(ImmutableSet.of(Path.of("java/com/test")))
            .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JVM))
            .build();

    final var rootSources =
        converter.calculateJavaRootSources(context, sourcePackages.keySet(), packages);
    assertThat(rootSources.keySet()).containsExactly(Path.of("java/com/test"));
    assertThat(rootSources.get(Path.of("java/com/test"))).containsExactly(Path.of(""), "com.test");
  }

  @Test
  public void testCalculateRootSources_multiSource_nestedPrefixIncompatible() throws Exception {
    PackageSet packages = PackageSet.of(Path.of("java/com/test"), Path.of("java/com/test/package"));
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("java/com/test/Class1.java"), "com.test.odd",
            Path.of("java/com/test/package/Class2.java"), "com.test.package");

    GraphToProjectConverter converter =
        GraphToProjectConvertersForTests.builder()
            .setPrefixReader(toPrefixReader(sourcePackages::get))
            .setProjectIncludes(ImmutableSet.of(Path.of("java/com/test")))
            .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JVM))
            .build();

    final var rootSources =
        converter.calculateJavaRootSources(context, sourcePackages.keySet(), packages);
    assertThat(rootSources.keySet()).containsExactly(Path.of("java/com/test"));
    assertThat(rootSources.get(Path.of("java/com/test")))
        .containsExactly(
            Path.of(""), "com.test.odd",
            Path.of("package"), "com.test.package");
  }

  @Test
  public void testCalculateRootSources_multiSource_rootPrefix() throws Exception {
    PackageSet packages =
        PackageSet.of(Path.of("third_party/java"), Path.of("third_party/javatests"));

    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("third_party/java/com/test/Class1.java"), "com.test",
            Path.of("third_party/javatests/com/test/Class2.java"), "com.test");

    GraphToProjectConverter converter =
        GraphToProjectConvertersForTests.builder()
            .setPrefixReader(toPrefixReader(sourcePackages::get))
            .setProjectIncludes(ImmutableSet.of(Path.of("third_party")))
            .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JVM))
            .build();

    final var rootSources =
        converter.calculateJavaRootSources(context, sourcePackages.keySet(), packages);
    assertThat(rootSources.keySet()).containsExactly(Path.of("third_party"));
    assertThat(rootSources.get(Path.of("third_party")))
        .containsExactly(
            Path.of("java"), "",
            Path.of("javatests"), "");
  }

  @Test
  public void testCalculateRootSources_multiSource_repackagedSource() throws Exception {
    PackageSet packages =
        PackageSet.of(Path.of("java/com/test"), Path.of("java/com/test/repackaged"));
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            Path.of("java/com/test/repackaged/com/foo/Class1.java"), "com.foo",
            Path.of("java/com/test/somepackage/Class2.java"), "com.test.somepackage");

    GraphToProjectConverter converter =
        GraphToProjectConvertersForTests.builder()
            .setPrefixReader(toPrefixReader(sourcePackages::get))
            .setProjectIncludes(ImmutableSet.of(Path.of("java/com/test")))
            .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JVM))
            .build();

    final var rootSources =
        converter.calculateJavaRootSources(context, sourcePackages.keySet(), packages);
    assertThat(rootSources.keySet()).containsExactly(Path.of("java/com/test"));
    assertThat(rootSources.get(Path.of("java/com/test")))
        .containsExactly(
            Path.of("repackaged"), "",
            Path.of(""), "com.test");
  }

  @Test
  public void testCalculateAndroidResourceDirectories_single_directory() {
    final var sourceFiles =
        ImmutableSet.of(
            Label.of("//java/com/test:AndroidManifest.xml"),
            Label.of("//java/com/test:res/values/strings.xml"));

    ImmutableSet<Path> androidResourceDirectories =
        GraphToProjectConverter.computeAndroidResourceDirectories(sourceFiles);
    assertThat(androidResourceDirectories).containsExactly(Path.of("java/com/test/res"));
  }

  @Test
  public void testCalculateAndroidResourceDirectories_multiple_directories() {
    final var sourceFiles =
        ImmutableSet.of(
            Label.of("//java/com/test:AndroidManifest.xml"),
            Label.of("//java/com/test:res/values/strings.xml"),
            Label.of("//java/com/test2:AndroidManifest.xml"),
            Label.of("//java/com/test2:res/layout/some-activity.xml"),
            Label.of("//java/com/test2:res/layout/another-activity.xml"));

    ImmutableSet<Path> androidResourceDirectories =
        GraphToProjectConverter.computeAndroidResourceDirectories(sourceFiles);
    assertThat(androidResourceDirectories)
        .containsExactly(Path.of("java/com/test/res"), Path.of("java/com/test2/res"));
  }

  @Test
  public void testCalculateAndroidResourceDirectories_manifest_without_res_directory() {
    final var sourceFiles =
        ImmutableSet.of(
            Label.of("//java/com/nores:AndroidManifest.xml"),
            Label.of("//java/com/nores:Foo.java"));

    ImmutableSet<Path> androidResourceDirectories =
        GraphToProjectConverter.computeAndroidResourceDirectories(sourceFiles);
    assertThat(androidResourceDirectories).isEmpty();
  }

  @Test
  public void testCalculateAndroidSourcePackages_rootWithEmptyPrefix() {
    GraphToProjectConverter converter =
        GraphToProjectConvertersForTests.builder()
            .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JVM))
            .build();

    ImmutableList<Path> androidSourceFiles =
        ImmutableList.of(
            Path.of("java/com/example/foo/Foo.java"), Path.of("java/com/example/bar/Bar.java"));
    ImmutableMap<Path, ImmutableMap<Path, String>> rootToPrefix =
        ImmutableMap.of(Path.of("java/com/example"), ImmutableMap.of(Path.of(""), "com.example"));

    ImmutableSet<String> androidResourcePackages =
        converter.computeAndroidSourcePackages(androidSourceFiles, rootToPrefix);
    assertThat(androidResourcePackages).containsExactly("com.example.foo", "com.example.bar");
  }

  @Test
  public void testCalculateAndroidSourcePackages_emptyRootWithPrefix() {
    GraphToProjectConverter converter =
        GraphToProjectConvertersForTests.builder()
            .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JVM))
            .build();

    ImmutableList<Path> androidSourceFiles =
        ImmutableList.of(
            Path.of("some_project/java/com/example/foo/Foo.java"),
            Path.of("some_project/java/com/example/bar/Bar.java"));
    ImmutableMap<Path, ImmutableMap<Path, String>> rootToPrefix =
        ImmutableMap.of(Path.of("some_project"), ImmutableMap.of(Path.of("java"), ""));

    ImmutableSet<String> androidResourcePackages =
        converter.computeAndroidSourcePackages(androidSourceFiles, rootToPrefix);
    assertThat(androidResourcePackages).containsExactly("com.example.foo", "com.example.bar");
  }

  @Test
  public void testCalculateAndroidSourcePackages_emptyRootAndNonEmptyRoot() {
    GraphToProjectConverter converter = GraphToProjectConvertersForTests.builder().build();

    ImmutableList<Path> androidSourceFiles =
        ImmutableList.of(
            Path.of("some_project/java/com/example/foo/Foo.java"),
            Path.of("java/com/example/bar/Bar.java"));
    ImmutableMap<Path, ImmutableMap<Path, String>> rootToPrefix =
        ImmutableMap.of(
            Path.of("some_project"),
            ImmutableMap.of(Path.of("java"), ""),
            Path.of("java/com/example"),
            ImmutableMap.of(Path.of(""), "com.example"));

    ImmutableSet<String> androidResourcePackages =
        converter.computeAndroidSourcePackages(androidSourceFiles, rootToPrefix);
    assertThat(androidResourcePackages).containsExactly("com.example.foo", "com.example.bar");
  }

  @Test
  public void testCalculateAndroidSourcePackages_pathPrefixOfAnotherPath() {
    GraphToProjectConverter converter = GraphToProjectConvertersForTests.builder().build();

    ImmutableList<Path> androidSourceFiles =
        ImmutableList.of(
            Path.of("project/MainActivity.java"),
            Path.of("project/modules/test/com/example/bar/Bar.java"));
    ImmutableMap<Path, ImmutableMap<Path, String>> rootToPrefix =
        ImmutableMap.of(
            Path.of("project"),
            ImmutableMap.of(Path.of(""), "com.root.project", Path.of("modules", "test"), ""));

    ImmutableSet<String> androidResourcePackages =
        converter.computeAndroidSourcePackages(androidSourceFiles, rootToPrefix);
    assertThat(androidResourcePackages).containsExactly("com.root.project", "com.example.bar");
  }

  @Test
  public void testConvertProject_emptyProject() throws Exception {
    GraphToProjectConverter converter = GraphToProjectConvertersForTests.builder().build();
    ProjectProto.Project project = converter.createProject(BuildGraphData.EMPTY);
    assertThat(project.getModules().size()).isEqualTo(1);

    ProjectProto.Module workspaceModule = project.getModules().get(0);
    assertThat(workspaceModule.getName()).isEqualTo(".workspace");

    assertThat(workspaceModule.getContentEntries().size()).isEqualTo(0);
  }

  @Test
  public void testConvertProject_buildGraphWithSingleImportRoot() throws Exception {
    Path workspaceImportDirectory = TestData.ROOT.resolve("nodeps");
    BuildGraphData buildGraphData =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_NO_DEPS_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parse();
    GraphToProjectConverter converter =
        GraphToProjectConvertersForTests.builder()
            .setPrefixReader(QuerySyncTestUtils.PATH_INFERRING_PREFIX_READER)
            .setProjectIncludes(ImmutableSet.of(workspaceImportDirectory))
            .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JVM))
            .build();

    ProjectProto.Project project = converter.createProject(buildGraphData);

    // Sanity check
    assertThat(project.getModules().size()).isEqualTo(1);
    ProjectProto.Module workspaceModule = project.getModules().get(0);

    assertThat(workspaceModule.getContentEntries().size()).isEqualTo(1);

    ProjectProto.ContentEntry javaContentEntry =
        workspaceModule.getContentEntries().values().iterator().next();
    assertThat(javaContentEntry.getRoot())
        .isEqualTo(ProjectPath.workspaceRelative(workspaceImportDirectory));
    assertThat(javaContentEntry.getSourceFolders().size()).isEqualTo(1);

    ProjectProto.SourceFolder javaSource = javaContentEntry.getSourceFolders().get(0);
    assertThat(javaSource.getProjectPath())
        .isEqualTo(ProjectPath.workspaceRelative(workspaceImportDirectory));
    assertThat(javaSource.isGenerated()).isFalse();
    assertThat(javaSource.isTest()).isFalse();
  }

  @Test
  public void testTestSources() throws Exception {
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            TestData.ROOT.resolve("nodeps/TestClassNoDeps.java"),
            "com.google.idea.blaze.qsync.testdata.nodeps");

    GraphToProjectConverter converter =
        GraphToProjectConvertersForTests.builder()
            .setPrefixReader(toPrefixReader(sourcePackages::get))
            .setProjectIncludes(ImmutableSet.of(TestData.ROOT.resolve("nodeps")))
            .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JVM))
            .setTestSources(ImmutableSet.of("tools/adt/idea/aswb/querysync/javatests/*"))
            .build();
    BuildGraphData buildGraphData = BuildGraphs.forTestProject(TestData.JAVA_LIBRARY_NO_DEPS_QUERY);
    ProjectProto.Project project = converter.createProject(buildGraphData);

    assertThat(project.getModules().size()).isEqualTo(1);
    assertThat(project.getModules().get(0).getContentEntries().size()).isEqualTo(1);
    ProjectProto.ContentEntry contentEntry =
        project.getModules().get(0).getContentEntries().values().iterator().next();
    assertThat(contentEntry.getSourceFolders().size()).isEqualTo(1);
    ProjectProto.SourceFolder sourceFolder = contentEntry.getSourceFolders().get(0);

    assertThat(sourceFolder.getProjectPath())
        .isEqualTo(ProjectPath.workspaceRelative(TestData.ROOT.resolve("nodeps")));

    assertThat(sourceFolder.isTest()).isTrue();
  }

  @Test
  @Ignore("b/336967556")
  public void testCreateProject_protoInStandaloneFolder_createsSourceFolder() throws Exception {
    ImmutableMap<Path, String> sourcePackages = ImmutableMap.of();

    GraphToProjectConverter converter =
        GraphToProjectConvertersForTests.builder()
            .setPrefixReader(toPrefixReader(sourcePackages::get))
            .setProjectIncludes(ImmutableSet.of(TestData.ROOT.resolve("protoonly")))
            .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JVM))
            .build();

    ProjectProto.Project project =
        converter.createProject(BuildGraphs.forTestProject(TestData.PROTO_ONLY_QUERY));
    assertThat(project.getModules().size()).isEqualTo(1);
    ProjectProto.Module module = project.getModules().get(0);

    assertThat(module.getContentEntries().size()).isEqualTo(1);
    ProjectProto.ContentEntry contentEntry = module.getContentEntries().values().iterator().next();
    assertThat(contentEntry.getRoot())
        .isEqualTo(ProjectPath.workspaceRelative(TestData.ROOT.resolve("protoonly")));

    assertThat(contentEntry.getSourceFolders().size()).isEqualTo(1);
    ProjectProto.SourceFolder sourceFolder = contentEntry.getSourceFolders().get(0);

    assertThat(sourceFolder.getProjectPath())
        .isEqualTo(ProjectPath.workspaceRelative(TestData.ROOT.resolve("protoonly")));
  }

  @Test
  public void testProtoSourceFolders_returnsParentDirectory() throws Exception {
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(Path.of("myproject/java/com/test/Class1.java"), "com.test");

    GraphToProjectConverter converter =
        GraphToProjectConvertersForTests.builder()
            .setPrefixReader(toPrefixReader(sourcePackages::get))
            .setProjectIncludes(ImmutableSet.of(Path.of("myproject")))
            .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JVM))
            .build();

    final var additionalProtoSourceFolders =
        converter.nonJavaSourceFolders(ImmutableSet.of(Path.of("myproject/protos/test.proto")));
    assertThat(additionalProtoSourceFolders)
        .containsExactly(Path.of("myproject"), ImmutableList.of(Path.of("protos")));
  }

  @Test
  public void testProtoSourceFolders_whenDirectoryIsExcluded_returnsEmpty() throws Exception {
    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(Path.of("myproject/java/com/test/Class1.java"), "com.test");

    GraphToProjectConverter converter =
        GraphToProjectConvertersForTests.builder()
            .setPrefixReader(toPrefixReader(sourcePackages::get))
            .setProjectIncludes(ImmutableSet.of(Path.of("myproject")))
            .setProjectExcludes(ImmutableSet.of(Path.of("myproject/excluded")))
            .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JVM))
            .build();

    final var additionalProtoSourceFolders =
        converter.nonJavaSourceFolders(
            ImmutableSet.of(Path.of("myproject/excluded/protos/excluded.proto")));
    assertThat(additionalProtoSourceFolders).isEmpty();
  }

  @Test
  @Ignore("b/336967556")
  public void testProtoSourceFolders_whenSubfolderOfJavaRoot_notCreated() throws Exception {

    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            TestData.ROOT.resolve(
                "nestedproto/java/com/testdata/nestedproto/NestedProtoConsumer.java"),
            "com.testdata.nestedproto");

    GraphToProjectConverter converter =
        GraphToProjectConvertersForTests.builder()
            .setPrefixReader(toPrefixReader(sourcePackages::get))
            .setProjectIncludes(ImmutableSet.of(TestData.ROOT.resolve("nestedproto")))
            .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JVM))
            .build();
    BuildGraphData buildGraphData = BuildGraphs.forTestProject(TestData.NESTED_PROTO_QUERY);

    ProjectProto.Project projectProto = converter.createProject(buildGraphData);
    assertThat(projectProto.getModules().size()).isEqualTo(1);
    ProjectProto.Module workspaceModule = projectProto.getModules().get(0);

    ProjectProto.ContentEntry contentEntry =
        workspaceModule.getContentEntries().values().iterator().next();
    assertThat(contentEntry.getSourceFolders())
        .containsExactly(
            new ProjectProto.SourceFolder(
                ProjectPath.workspaceRelative(TestData.ROOT.resolve("nestedproto/java")),
                false,
                false,
                ""));
  }

  @Test
  @Ignore("b/336967556")
  public void testNonJavaSourceFolders_whenSubfolderOfJavaRootAtContentEntry_notCreated()
      throws Exception {

    ImmutableMap<Path, String> sourcePackages =
        ImmutableMap.of(
            TestData.ROOT.resolve("protodep/TestClassProtoDep.java"),
            "com.google.idea.blaze.qsync.testdata.protodep");

    GraphToProjectConverter converter =
        GraphToProjectConvertersForTests.builder()
            .setPrefixReader(toPrefixReader(sourcePackages::get))
            .setProjectIncludes(ImmutableSet.of(TestData.ROOT))
            .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JVM))
            .build();
    BuildGraphData buildGraphData =
        BuildGraphs.forTestProject(TestData.JAVA_LIBRARY_PROTO_DEP_QUERY);

    ProjectProto.Project projectProto = converter.createProject(buildGraphData);
    ProjectProto.Module workspaceModule = Iterables.getOnlyElement(projectProto.getModules());
    ProjectProto.ContentEntry contentEntry =
        Iterables.getOnlyElement(workspaceModule.getContentEntries().values());
    assertThat(contentEntry.getSourceFolders())
        .containsExactly(ProjectPath.workspaceRelative(TestData.ROOT));
  }

  @Test
  public void testActiveLanguages_emptyProject() throws Exception {
    GraphToProjectConverter converter = GraphToProjectConvertersForTests.builder().build();
    ProjectProto.Project project = converter.createProject(BuildGraphData.EMPTY);
    assertThat(project.getActiveLanguages()).isEmpty();
  }

  @Test
  public void testActiveLanguages_java() throws Exception {
    Path workspaceImportDirectory = TestData.ROOT.resolve("nodeps");
    GraphToProjectConverter converter =
        GraphToProjectConvertersForTests.builder()
            .setProjectIncludes(ImmutableSet.of(workspaceImportDirectory))
            .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JVM))
            .build();

    BuildGraphData buildGraphData =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_NO_DEPS_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parse();
    ProjectProto.Project project = converter.createProject(buildGraphData);

    assertThat(project.getActiveLanguages()).contains(QuerySyncLanguage.JVM);
  }

  @Test
  public void testActiveLanguages_cc() throws Exception {
    Path workspaceImportDirectory = TestData.ROOT.resolve("cc");
    GraphToProjectConverter converter =
        GraphToProjectConvertersForTests.builder()
            .setProjectIncludes(ImmutableSet.of(workspaceImportDirectory))
            .build();

    BuildGraphData buildGraphData =
        new BlazeQueryParser(
                getQuerySummary(TestData.CC_LIBRARY_QUERY), NOOP_CONTEXT, ImmutableSet.of())
            .parse();
    ProjectProto.Project project = converter.createProject(buildGraphData);

    assertThat(project.getActiveLanguages()).contains(QuerySyncLanguage.CC);
  }
}
