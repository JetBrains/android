/*
 * Copyright (C) 2020 The Android Open Source Project
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
package org.jetbrains.android.actions.widgets

import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider
import com.android.tools.idea.projectsystem.sourceProviders
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.buildMainSourceProviderStub
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.module.Module
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SourceSetItemTest {

  private lateinit var sourceProvider: NamedIdeaSourceProvider
  private lateinit var module: Module

  @get:Rule
  val rule = AndroidProjectRule.withAndroidModels(
    JavaModuleModelBuilder.rootModuleBuilder,
    AndroidModuleModelBuilder(":app", "debug",
                              AndroidProjectBuilder(mainSourceProvider = {
                                buildMainSourceProviderStub()
                                  .appendDirectories(
                                    resDirectories = listOf(
                                      moduleBasePath.resolve("myResDir"),
                                      moduleBasePath.resolve("foo/bar/deep/resources/android/res")
                                    )
                                  )
                              })))

  @Before
  fun setup() {
    module = rule.project.gradleModule(":app")!!
    sourceProvider = module.androidFacet!!.sourceProviders.mainIdeaSourceProvider
  }

  @Test
  fun displayableResourceDir() {
    val resDirectoryUrls = sourceProvider.resDirectoryUrls.toList()
    assertEquals(3, resDirectoryUrls.size)

    val resDirUrl1 = resDirectoryUrls[0]
    val resDirUrl2 = resDirectoryUrls[1]
    val resDirUrl3 = resDirectoryUrls[2]

    assertEquals("src/main/res", SourceSetItem.create(sourceProvider, module, resDirUrl1)?.displayableResDir)
    assertEquals("myResDir", SourceSetItem.create(sourceProvider, module, resDirUrl2)?.displayableResDir)
    assertEquals("...bar/deep/resources/android/res", SourceSetItem.create(sourceProvider, module, resDirUrl3)?.displayableResDir)
  }

  @Test
  fun ignoreGeneratedResourceFolders() {
    assertNotNull(SourceSetItem.create(sourceProvider, module, "src/main/res"))
    assertNull(SourceSetItem.create(sourceProvider, module, "src/main/generated/res"))
    assertNull(SourceSetItem.create(sourceProvider, module, "src/flavor2/generated/res"))
  }
}
