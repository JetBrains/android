/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.table.filter

import com.android.ide.common.resources.Locale
import com.android.tools.idea.rendering.FlagManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests the [LocaleColumnFilter] class. */
@RunWith(JUnit4::class)
class LocaleColumnFilterTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val localeColumnFilterAr = LocaleColumnFilter(ARABIC_LOCALE)
  private val localeColumnFilterEs = LocaleColumnFilter(US_SPANISH_LOCALE)

  @Test
  fun getDescription() {
    assertThat(localeColumnFilterAr.getDescription()).isEqualTo("Arabic (ar)")
    assertThat(localeColumnFilterEs.getDescription())
        .isEqualTo("Spanish (es) in United States (US)")
  }

  @Test
  fun getIcon() {
    assertThat(localeColumnFilterAr.getIcon()).isEqualTo(FlagManager.getFlagImage(ARABIC_LOCALE))
    assertThat(localeColumnFilterEs.getIcon())
        .isEqualTo(FlagManager.getFlagImage(US_SPANISH_LOCALE))
  }

  @Test
  fun include() {
    assertThat(localeColumnFilterAr.include(US_SPANISH_LOCALE)).isFalse()
    assertThat(localeColumnFilterAr.include(ARABIC_LOCALE)).isTrue()
    assertThat(localeColumnFilterEs.include(US_SPANISH_LOCALE)).isTrue()
    assertThat(localeColumnFilterEs.include(ARABIC_LOCALE)).isFalse()
  }

  companion object {
    private val ARABIC_LOCALE = Locale.create("ar")
    private val US_SPANISH_LOCALE = Locale.create("es-rUS")
  }
}
