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

import com.google.idea.blaze.qsync.project.QuerySyncLanguage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LanguageClassesTest {

  @Test
  public void enum_map_is_exhaustive() {
    for (QuerySyncLanguage lc : QuerySyncLanguage.values()) {
      assertThat(LanguageClasses.QUERY_SYNC_TO_BASE_LANGUAGE_CLASS_MAP).containsKey(lc);
      assertThat(LanguageClasses.fromQuerySync(lc)).isNotNull();
    }
  }
}
