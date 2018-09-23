/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContentTypeTest {
  @Test
  fun fromMimeType_returnsExpectedContentType() {
    assertThat(ContentType.fromMimeType("image/bmp")).isEqualTo(ContentType.BMP)
    assertThat(ContentType.fromMimeType("text/html")).isEqualTo(ContentType.HTML)

    assertThat(ContentType.fromMimeType("text/vnd.api+json")).isEqualTo(ContentType.JSON)
    assertThat(ContentType.fromMimeType("application/xhtml+xml")).isEqualTo(ContentType.XML)

    assertThat(ContentType.fromMimeType("")).isEqualTo(ContentType.DEFAULT)
    assertThat(ContentType.fromMimeType("application")).isEqualTo(ContentType.DEFAULT)
    assertThat(ContentType.fromMimeType("application/unknown")).isEqualTo(ContentType.DEFAULT)
  }
}