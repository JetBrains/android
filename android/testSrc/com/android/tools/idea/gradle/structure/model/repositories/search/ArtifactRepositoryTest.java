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

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.*;


public class ArtifactRepositoryTest {
  private static final SearchResult TEST_RESULT = new SearchResult("resultName", ImmutableList.of(), 0);
  private ArtifactRepository myRepo;

  @Before
  public void before() {
    myRepo = new TestArtifactRepository();
  }

  @Test
  public void search_success() throws Exception {
    SearchResult result = myRepo.search(new SearchRequest("artifact", "groupId", 0, 0));

    assertNull(result.getError());
    assertEquals(TEST_RESULT, result);
  }

  @Test
  public void search_failure() throws Exception {
    SearchResult result = myRepo.search(new SearchRequest("fail", "groupId", 0, 0));

    assertThat(result.getArtifacts()).isEmpty();
    assertNotNull(result.getError());
    assertEquals("failure", result.getError().getMessage());
  }

  private static class TestArtifactRepository extends ArtifactRepository {

    @NotNull
    @Override
    public String getName() {
      return "testName";
    }

    @Override
    public boolean isRemote() {
      return false;
    }

    @NotNull
    @Override
    protected SearchResult doSearch(@NotNull SearchRequest request) throws Exception {
      if (request.getArtifactName().equals("fail")) {
        throw new Exception("failure");
      }
      return TEST_RESULT;
    }
  }
}