/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.dagger.index.concepts

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DaggerConceptTest {
  @Test
  fun indexers() {
    // This doesn't verify the exact number, since that can change and isn't really important. This test is just validating that the code
    // to gather the indexers is running and finding appropriate values.
    assertThat(DaggerConceptIndexers.ALL_INDEXERS.fieldIndexers).isNotEmpty()
    assertThat(DaggerConceptIndexers.ALL_INDEXERS.methodIndexers).isNotEmpty()
  }
}
