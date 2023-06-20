/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.repositories.search;

import com.android.ide.common.gradle.Version;
import org.junit.Test;

import static com.android.tools.idea.gradle.repositories.search.AndroidSdkRepositories.ANDROID_REPOSITORY_NAME;
import static com.android.tools.idea.gradle.repositories.search.AndroidSdkRepositories.GOOGLE_REPOSITORY_NAME;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

public class FoundArtifactTest {
  @Test
  public void getVersions() throws Exception {
  }

  @Test
  public void compareTo_same() throws Exception {
    FoundArtifact artifactA = new FoundArtifact("repository", "group", "artifact", Version.Companion.parse("1.0.2.+"));
    FoundArtifact artifactB = new FoundArtifact("repository", "group", "artifact", Version.Companion.parse("1.0.2.+"));

    assertEquals(0, artifactA.compareTo(artifactB));
    assertEquals(0, artifactB.compareTo(artifactA));
  }

  @Test
  public void compareTo_differentVersion() throws Exception {
    FoundArtifact artifactA = new FoundArtifact("repository", "group", "artifact", Version.Companion.parse("1.0.2.+"));
    FoundArtifact artifactB = new FoundArtifact("repository", "group", "artifact", Version.Companion.parse("2.0.2.+"));

    assertEquals(0, artifactA.compareTo(artifactB));
    assertEquals(0, artifactB.compareTo(artifactA));
  }

  @Test
  public void compareTo_differentNames() throws Exception {
    FoundArtifact artifactA = new FoundArtifact("repository", "group", "artifactA", Version.Companion.parse("1.0.2.+"));
    FoundArtifact artifactB = new FoundArtifact("repository", "group", "artifactB", Version.Companion.parse("1.0.2.+"));

    assertThat(artifactA.compareTo(artifactB)).isLessThan(0);
    assertThat(artifactB.compareTo(artifactA)).isGreaterThan(0);
  }

  @Test
  public void compareTo_differentGroups() throws Exception {
    FoundArtifact artifactA = new FoundArtifact("repository", "groupA", "artifact", Version.Companion.parse("1.0.2.+"));
    FoundArtifact artifactB = new FoundArtifact("repository", "groupB", "artifact", Version.Companion.parse("1.0.2.+"));

    assertThat(artifactA.compareTo(artifactB)).isLessThan(0);
    assertThat(artifactB.compareTo(artifactA)).isGreaterThan(0);
  }

  @Test
  public void compareTo_androidPackage() throws Exception {
    FoundArtifact artifactA = new FoundArtifact("repository", "com.android.test", "artifact", Version.Companion.parse("1.0.2.+"));
    FoundArtifact artifactB = new FoundArtifact("repository", "AAAAA", "artifact", Version.Companion.parse("1.0.2.+"));

    assertThat(artifactA.compareTo(artifactB)).isLessThan(0);
    assertThat(artifactB.compareTo(artifactA)).isGreaterThan(0);
  }

  @Test
  public void compareTo_googlePackage() throws Exception {
    FoundArtifact artifactA = new FoundArtifact("repository", "com.google.test", "artifact", Version.Companion.parse("1.0.2.+"));
    FoundArtifact artifactB = new FoundArtifact("repository", "AAAAA", "artifact", Version.Companion.parse("1.0.2.+"));

    assertThat(artifactA.compareTo(artifactB)).isLessThan(0);
    assertThat(artifactB.compareTo(artifactA)).isGreaterThan(0);
  }

  @Test
  public void compareTo_androidAndGooglePackage() throws Exception {
    FoundArtifact artifactA = new FoundArtifact("repository", "com.android.test", "artifact", Version.Companion.parse("1.0.2.+"));
    FoundArtifact artifactB = new FoundArtifact("repository", "com.google.test", "artifact", Version.Companion.parse("1.0.2.+"));

    assertThat(artifactA.compareTo(artifactB)).isLessThan(0);
    assertThat(artifactB.compareTo(artifactA)).isGreaterThan(0);
  }

  @Test
  public void compareTo_differentRepos() throws Exception {
    FoundArtifact artifactA = new FoundArtifact("AAAAA", "group", "artifact", Version.Companion.parse("1.0.2.+"));
    FoundArtifact artifactB = new FoundArtifact("BBBBB", "group", "artifact", Version.Companion.parse("1.0.2.+"));

    assertThat(artifactA.compareTo(artifactB)).isEqualTo(0);
  }

  @Test
  public void compareTo_androidRepository() throws Exception {
    FoundArtifact artifactA = new FoundArtifact(ANDROID_REPOSITORY_NAME, "group", "artifact", Version.Companion.parse("1.0.2.+"));
    FoundArtifact artifactB = new FoundArtifact("AAAAA", "group", "artifact", Version.Companion.parse("1.0.2.+"));

    assertThat(artifactA.compareTo(artifactB)).isLessThan(0);
    assertThat(artifactB.compareTo(artifactA)).isGreaterThan(0);
  }

  @Test
  public void compareTo_googleRepository() throws Exception {
    FoundArtifact artifactA = new FoundArtifact(GOOGLE_REPOSITORY_NAME, "group", "artifact", Version.Companion.parse("1.0.2.+"));
    FoundArtifact artifactB = new FoundArtifact("AAAAA", "group", "artifact", Version.Companion.parse("1.0.2.+"));

    assertThat(artifactA.compareTo(artifactB)).isLessThan(0);
    assertThat(artifactB.compareTo(artifactA)).isGreaterThan(0);
  }

  @Test
  public void compareTo_androidAndGoogleRepository() throws Exception {
    FoundArtifact artifactA = new FoundArtifact(ANDROID_REPOSITORY_NAME, "group", "artifact", Version.Companion.parse("1.0.2.+"));
    FoundArtifact artifactB = new FoundArtifact(GOOGLE_REPOSITORY_NAME, "group", "artifact", Version.Companion.parse("1.0.2.+"));

    assertThat(artifactA.compareTo(artifactB)).isLessThan(0);
    assertThat(artifactB.compareTo(artifactA)).isGreaterThan(0);
  }

  @Test
  public void compareTo_repositoryDominatesGroup() throws Exception {
    FoundArtifact artifactA = new FoundArtifact(ANDROID_REPOSITORY_NAME, "com.google.test", "artifact", Version.Companion.parse("1.0.2.+"));
    FoundArtifact artifactB = new FoundArtifact(GOOGLE_REPOSITORY_NAME, "com.android.test", "artifact", Version.Companion.parse("1.0.2.+"));

    assertThat(artifactA.compareTo(artifactB)).isLessThan(0);
    assertThat(artifactB.compareTo(artifactA)).isGreaterThan(0);
  }
}