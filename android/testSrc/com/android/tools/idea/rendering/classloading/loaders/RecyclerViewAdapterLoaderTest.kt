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
package com.android.tools.idea.rendering.classloading.loaders

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Arrays

class RecyclerViewAdapterLoaderTest {
  @Test
  fun `check all adapters are found and are different`() {
    val loader = RecyclerViewAdapterLoader()
    val supportAdapter = loader.loadClass("com.android.layoutlib.bridge.android.support.Adapter")
    assertNotNull(supportAdapter)
    val supportAdapterViewHolder = loader.loadClass("com.android.layoutlib.bridge.android.support.Adapter\$ViewHolder")
    assertNotNull(supportAdapterViewHolder)
    val androidxAdapter = loader.loadClass("com.android.layoutlib.bridge.android.androidx.Adapter")
    assertNotNull(androidxAdapter)
    val androidxAdapterViewHolder = loader.loadClass("com.android.layoutlib.bridge.android.androidx.Adapter\$ViewHolder")
    assertNotNull(androidxAdapterViewHolder)

    assertFalse(Arrays.equals(supportAdapter, supportAdapterViewHolder))
    assertFalse(Arrays.equals(supportAdapter, androidxAdapter))
    assertFalse(Arrays.equals(androidxAdapter, androidxAdapterViewHolder))
    assertFalse(Arrays.equals(supportAdapterViewHolder, androidxAdapterViewHolder))
  }
}