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
package com.android.tools.idea.fonts;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class DownloadableFontCacheServiceImplTest {

  @Test
  public void testConvertNameToFilename() {
    assertThat(DownloadableFontCacheServiceImpl.convertNameToFilename("ABeeZee")).isEqualTo("abeezee");
    assertThat(DownloadableFontCacheServiceImpl.convertNameToFilename("Alegreya Sans SC")).isEqualTo("alegreya_sans_sc");
    assertThat(DownloadableFontCacheServiceImpl.convertNameToFilename("Alfa Slab One")).isEqualTo("alfa_slab_one");
    assertThat(DownloadableFontCacheServiceImpl.convertNameToFilename("ALFA SLAB ONE")).isEqualTo("alfa_slab_one");
    assertThat(
      DownloadableFontCacheServiceImpl.convertNameToFilename("Alfa Slab One Regular Italic")).isEqualTo("alfa_slab_one_regular_italic");
  }
}
