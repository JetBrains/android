/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.collect.Lists;
import com.intellij.testFramework.IdeaTestCase;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ArtifactRepositorySearch}.
 */
public class ArtifactRepositorySearchTest extends IdeaTestCase {
  public void testSearch() throws Exception {
    ArtifactRepository repository1 = mock(ArtifactRepository.class);
    ArtifactRepository repository2 = mock(ArtifactRepository.class);

    SearchResult result1 = mock(SearchResult.class);
    SearchResult result2 = mock(SearchResult.class);

    SearchRequest request = new SearchRequest("name", null, 50, 0);
    when(repository1.search(request)).thenReturn(result1);
    when(repository2.search(request)).thenReturn(result2);

    ArtifactRepositorySearch search = new ArtifactRepositorySearch(Lists.newArrayList(repository1, repository2));

    final CountDownLatch lock = new CountDownLatch(1);

    ArtifactRepositorySearch.Callback callback = search.start(request);
    callback.doWhenDone(lock::countDown);
    lock.await();

    List<SearchResult> results = callback.getSearchResults();
    assertThat(results).containsExactly(result1, result2);
  }
}