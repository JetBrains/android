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
package com.google.idea.blaze.qsync.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProjectDefinitionTest {

  @Test
  public void testGetIncludingContentRoot_returnsContentRoot() {
    ProjectDefinition projectDefinition =
        ProjectDefinition.builder()
            .setProjectIncludes(ImmutableSet.of(Path.of("contentroot1"), Path.of("contentroot2")))
            .setProjectExcludes(ImmutableSet.of())
            .setSystemExcludes(ImmutableSet.of())
            .setTestSources(ImmutableSet.of())
            .setLanguageClasses(ImmutableSet.of())
            .build();
    assertThat(projectDefinition.getIncludingContentRoot(Path.of("contentroot1/some/path")))
        .hasValue(Path.of("contentroot1"));
    assertThat(projectDefinition.getIncludingContentRoot(Path.of("contentroot2")))
        .hasValue(Path.of("contentroot2"));
  }

  @Test
  public void testGetIncludingContentRoot_externalPath_returnsEmpty() {
    ProjectDefinition projectDefinition =
        ProjectDefinition.builder()
            .setProjectIncludes(ImmutableSet.of(Path.of("contentroot1"), Path.of("contentroot2")))
            .setProjectExcludes(ImmutableSet.of())
            .setSystemExcludes(ImmutableSet.of())
            .setTestSources(ImmutableSet.of())
            .setLanguageClasses(ImmutableSet.of())
            .build();
    assertThat(projectDefinition.getIncludingContentRoot(Path.of("anotherRoot/some/path")))
        .isEmpty();
  }

  @Test
  public void testGetIncludingContentRoot_excludedPath_returnsEmpty() {
    ProjectDefinition projectDefinition =
        ProjectDefinition.builder()
        .setProjectIncludes(ImmutableSet.of(Path.of("contentroot1"), Path.of("contentroot2")))
        .setProjectExcludes(ImmutableSet.of(Path.of("contentroot1/excluded")))
        .setSystemExcludes(ImmutableSet.of())
        .setTestSources(ImmutableSet.of())
        .setLanguageClasses(ImmutableSet.of())
        .build();
    assertThat(projectDefinition.getIncludingContentRoot(Path.of("contentroot1/excluded/path")))
        .isEmpty();
  }
}
