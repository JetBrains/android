// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.resourceExplorer.importer

import com.android.tools.idea.resourceExplorer.plugin.RasterResourceImporter
import com.android.tools.idea.resourceExplorer.plugin.ResourceImporter
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.extensions.Extensions
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ImportersProviderTest {

  @Suppress("unused") // Needed to initialize extension points
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun extensionPointExists() {
    assertNotNull(Extensions.getArea(null).getExtensionPoint<ResourceImporter>("com.android.resourceImporter"))
    assertTrue { ImportersProvider().importers.filterIsInstance<RasterResourceImporter>().any() }
  }

  @Test
  fun getSupportedFileTypes() {
    assertTrue(ImportersProvider().getImportersForExtension("png").any { it is RasterResourceImporter })
  }
}