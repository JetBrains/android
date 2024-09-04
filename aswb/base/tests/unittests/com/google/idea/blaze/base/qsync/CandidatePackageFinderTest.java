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
package com.google.idea.blaze.base.qsync;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.when;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Expect;
import com.google.idea.blaze.base.bazel.BazelExitCodeException;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandRunner;
import com.google.idea.blaze.base.qsync.CandidatePackageFinder.CandidatePackage;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.openapi.project.Project;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class CandidatePackageFinderTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tempDir = new TemporaryFolder();
  @Rule public final Expect expect = Expect.create();

  private final ArgumentCaptor<BlazeCommand.Builder> commandCaptor =
      ArgumentCaptor.forClass(BlazeCommand.Builder.class);

  @Mock Project ideProject;
  @Mock BuildInvoker buildInvoker;
  @Mock BlazeCommandRunner commandRunner;

  @Before
  public void configureBuildInvoker() {
    when(buildInvoker.getCommandRunner()).thenReturn(commandRunner);
  }

  private static InputStream streamOfLines(String... lines) {
    return new ByteArrayInputStream(Joiner.on("\n").join(lines).getBytes(UTF_8));
  }

  @Test
  public void getCandidatePackages_checkQuery_dir() throws Exception {
    CandidatePackageFinder cpf =
        new CandidatePackageFinder(
            ideProject, buildInvoker, tempDir.getRoot().toPath(), BlazeContext.create());

    tempDir.newFolder("package", "path");
    when(commandRunner.runQuery(same(ideProject), commandCaptor.capture(), any(), any()))
        .thenReturn(streamOfLines("//package/path/a", "//package/path/b", "//package/path"));

    ImmutableList<CandidatePackage> unused =
        cpf.getCandidatePackages(Path.of("package/path/BUILD"), () -> {});

    ImmutableList<String> args = commandCaptor.getValue().build().toArgumentList();
    expect.that(args).containsAllOf("--output", "package", "//package/path/...").inOrder();
  }

  @Test
  public void getCandidatePackages_checkQuery_file() throws Exception {
    CandidatePackageFinder cpf =
        new CandidatePackageFinder(
            ideProject, buildInvoker, tempDir.getRoot().toPath(), BlazeContext.create());

    Files.write(tempDir.newFolder("package", "path").toPath().resolve("BUILD"), new byte[] {});

    when(commandRunner.runQuery(same(ideProject), commandCaptor.capture(), any(), any()))
        .thenReturn(streamOfLines("//package/path/a", "//package/path/b", "//package/path"));

    ImmutableList<CandidatePackage> unused =
        cpf.getCandidatePackages(Path.of("package/path/BUILD"), () -> {});

    ImmutableList<String> args = commandCaptor.getValue().build().toArgumentList();
    expect.that(args).containsAllOf("--output", "package", "//package/path/...").inOrder();
  }

  @Test
  public void getCandidatePackages_multipleCandidatesForPathProvided() throws Exception {
    CandidatePackageFinder cpf =
        new CandidatePackageFinder(
            ideProject, buildInvoker, tempDir.getRoot().toPath(), BlazeContext.create());

    tempDir.newFolder("package", "path");

    when(commandRunner.runQuery(same(ideProject), any(), any(), any()))
        .thenReturn(streamOfLines("//package/path/a", "//package/path/b", "//package/path"));

    ImmutableList<CandidatePackage> candidates =
        cpf.getCandidatePackages(Path.of("package/path"), () -> {});
    assertThat(candidates).hasSize(1);
    expect.that(candidates.get(0).packageCount).isEqualTo(3);
    expect.that(candidates.get(0).path.toString()).isEqualTo("package/path");
  }

  @Test
  public void getCandidatePackages_singleCandidateForPathProvided() throws Exception {
    CandidatePackageFinder cpf =
        new CandidatePackageFinder(
            ideProject, buildInvoker, tempDir.getRoot().toPath(), BlazeContext.create());

    tempDir.newFolder("package", "path", "subpath");

    when(commandRunner.runQuery(same(ideProject), commandCaptor.capture(), any(), any()))
        .thenReturn(streamOfLines("//package/path/subpath"))
        .thenReturn(streamOfLines("//package/path", "//package/path/subpath"));

    ImmutableList<CandidatePackage> candidates =
        cpf.getCandidatePackages(Path.of("package/path/subpath"), () -> {});

    assertThat(commandCaptor.getAllValues()).hasSize(2);
    assertThat(commandCaptor.getAllValues().get(0).build().toArgumentList())
        .contains("//package/path/subpath/...");
    assertThat(commandCaptor.getAllValues().get(1).build().toArgumentList())
        .contains("//package/path/...");

    assertThat(candidates).hasSize(2);

    expect.that(candidates.get(0).packageCount).isEqualTo(1);
    expect.that(candidates.get(0).path.toString()).isEqualTo("package/path/subpath");

    expect.that(candidates.get(1).packageCount).isEqualTo(2);
    expect.that(candidates.get(1).path.toString()).isEqualTo("package/path");
  }

  @Test
  public void getCandidatePackages_noCandidateForPathProvided() throws Exception {
    CandidatePackageFinder cpf =
        new CandidatePackageFinder(
            ideProject, buildInvoker, tempDir.getRoot().toPath(), BlazeContext.create());

    tempDir.newFolder("package", "path", "subpath");

    when(commandRunner.runQuery(same(ideProject), any(), any(), any()))
        .thenThrow(new BazelExitCodeException("query failed", 7 /* command failure */))
        .thenReturn(streamOfLines("//package/path", "//package/path/a"));

    ImmutableList<CandidatePackage> candidates =
        cpf.getCandidatePackages(Path.of("package/path/subpath"), () -> {});

    assertThat(candidates).hasSize(1);

    expect.that(candidates.get(0).packageCount).isEqualTo(2);
    expect.that(candidates.get(0).path.toString()).isEqualTo("package/path");
  }
}
