/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui.stdui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContentTypeTest {
  @Test
  fun fromMimeType_returnsExpectedContentType() {
    assertThat(ContentType.fromMimeType("image/bmp")).isEqualTo(ContentType.BMP)
    assertThat(ContentType.fromMimeType("text/html")).isEqualTo(ContentType.HTML)

    assertThat(ContentType.fromMimeType("text/vnd.api+json")).isEqualTo(ContentType.JSON)
    assertThat(ContentType.fromMimeType("application/xhtml+xml")).isEqualTo(
      ContentType.XML)

    assertThat(ContentType.fromMimeType("")).isEqualTo(ContentType.DEFAULT)
    assertThat(ContentType.fromMimeType("application")).isEqualTo(ContentType.DEFAULT)
    assertThat(ContentType.fromMimeType("application/unknown")).isEqualTo(
      ContentType.DEFAULT)
  }

  @Test
  fun fromMimeType_parsesTypeAndSubtype() {
    val empty = ContentType.fromMimeType("")
    assertThat(empty).isEqualTo(ContentType.DEFAULT)
    assertThat(empty.type).isEmpty()
    assertThat(empty.isSupportedTextType).isFalse()

    val video = ContentType.fromMimeType("video")
    assertThat(video).isEqualTo(ContentType.DEFAULT)
    assertThat(video.type).isEqualTo("video")
    assertThat(video.isSupportedTextType).isFalse()

    val audioMpeg = ContentType.fromMimeType("audio/mpeg")
    assertThat(audioMpeg).isEqualTo(ContentType.DEFAULT)
    assertThat(audioMpeg.type).isEqualTo("audio")
    assertThat(audioMpeg.isSupportedTextType).isFalse()

    val textPlain = ContentType.fromMimeType("text/plain")
    assertThat(textPlain).isEqualTo(ContentType.DEFAULT)
    assertThat(textPlain.type).isEqualTo("text")
    assertThat(textPlain.isSupportedTextType).isTrue()

    // XML is a known text subtype so we consider it a supported text type.
    val applicationXml = ContentType.fromMimeType("application/xml")
    assertThat(applicationXml).isEqualTo(ContentType.XML)
    assertThat(applicationXml.type).isEqualTo("application")
    assertThat(applicationXml.isSupportedTextType).isTrue()
  }
}