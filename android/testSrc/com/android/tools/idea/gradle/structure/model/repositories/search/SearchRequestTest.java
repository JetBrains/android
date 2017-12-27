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

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SearchRequestTest {
  @Test
  public void equalsAndHashCode() throws Exception {
    EqualsVerifier.forClass(SearchRequest.class).verify();
  }

  @Test
  public void testToString() throws Exception {
    SearchRequest request = new SearchRequest("name", "group", 1, 2);
    assertEquals("{artifact='name', group='group', rowCount=1, start=2}", request.toString());
  }
}