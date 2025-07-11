/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.dom.raw.expressions

import com.android.tools.wear.wff.WFFVersion.WFFVersion1
import com.android.tools.wear.wff.WFFVersion.WFFVersion4
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FunctionTest {

  @Test
  fun `finds by id`() {
    assertThat(findFunction("log10"))
      .isEqualTo(Function(id = "log10", requiredVersion = WFFVersion1))
    assertThat(findFunction("extractColorFromColors"))
      .isEqualTo(Function(id = "extractColorFromColors", requiredVersion = WFFVersion4))

    assertThat(findFunction("log100")).isNull()
    assertThat(findFunction("nonExistent")).isNull()
  }
}
