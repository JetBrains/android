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
package com.android.tools.idea.gradle.structure.model.repositories.search;

import com.android.ide.common.repository.GradleVersion;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.util.Collections;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

public class SearchResultTest {

  @Test
  public void getArtifactCoordinates() throws Exception {
    FoundArtifact artifactA = new FoundArtifact("test", "group", "artifactA", GradleVersion.parse("1.0.2.+"));
    FoundArtifact artifactB = new FoundArtifact("test", "group", "artifactB", GradleVersion.parse("2.0.2.+"));
    FoundArtifact artifactC = new FoundArtifact("test", "group", "artifactB", GradleVersion.parse("3.0.2.+"));
    SearchResult searchResult = new SearchResult("test", ImmutableList.of(artifactA, artifactB, artifactC), 100);
    assertThat(searchResult.getArtifactCoordinates())
      .containsExactly("group:artifactA:1.0.2.+", "group:artifactB:2.0.2.+", "group:artifactB:3.0.2.+");
  }

  @Test
  public void testToString_empty() throws Exception {
    SearchResult searchResult = new SearchResult("test", Collections.emptyList(), 0);
    assertEquals("{repository='test', artifacts=[], error=null, totalFound=0}", searchResult.toString());
  }

  @Test
  public void testToString_withError() throws Exception {
    SearchResult searchResult = new SearchResult("test", new Exception("testException"));
    assertEquals("{repository='test', artifacts=[], error=java.lang.Exception: testException, totalFound=0}", searchResult.toString());
  }

  @Test
  public void testToString_withArtifacts() throws Exception {
    FoundArtifact artifactA = new FoundArtifact("test", "group", "artifactA", GradleVersion.parse("1.0.2.+"));
    FoundArtifact artifactB = new FoundArtifact("test", "group", "artifactB", GradleVersion.parse("2.0.2.+"));
    FoundArtifact artifactC = new FoundArtifact("test", "group", "artifactB", GradleVersion.parse("3.0.2.+"));
    SearchResult searchResult = new SearchResult("test", ImmutableList.of(artifactA, artifactB, artifactC), 100);
    assertEquals("{repository='test', " +
                 "artifacts=[" +
                 "{repository='test', group='group', name='artifactA', versions=[1.0.2.+]}, " +
                 "{repository='test', group='group', name='artifactB', versions=[2.0.2.+]}, " +
                 "{repository='test', group='group', name='artifactB', versions=[3.0.2.+]}" +
                 "], " +
                 "error=null, " +
                 "totalFound=100}", searchResult.toString());
  }
}