/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.streaming.uisettings.data

import com.android.ide.common.resources.configuration.LocaleQualifier
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppLanguageTest {
  @Test
  fun languagesAreSortedWithPseudoLanguagesLast() {
    val languages = convertFromLocaleConfig(setOf(
      LocaleQualifier(null, "es", "ES", null),
      LocaleQualifier(null, "en", "XA", null),
      LocaleQualifier(null, "es", "AR", null),
      LocaleQualifier(null, "da", null, null),
      LocaleQualifier(null, "it", null, null),
      LocaleQualifier(null, "ar", "XB", null),
      LocaleQualifier(null, "es", null, null),
    ))
    assertThat(languages.map { it.name }).containsExactly(
      "System default",
      "Danish",
      "Italian",
      "Spanish",
      "Spanish in Argentina",
      "Spanish in Spain",
      "Pseudo English",
      "Pseudo Arabic",
    ).inOrder()
  }
}
