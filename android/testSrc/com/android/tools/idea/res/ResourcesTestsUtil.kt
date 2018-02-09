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
@file:JvmName("ResourcesTestsUtil")

package com.android.tools.idea.res

import com.android.tools.idea.projectsystem.FilenameConstants
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.facet.AndroidFacet
import java.nio.file.Paths

const val AAR_LIBRARY_NAME = "com.test:test-library:1.0.0";

fun createTestAppResourceRepository(facet: AndroidFacet): AppResourceRepository {
  val moduleResources = ModuleResourceRepository.createForTest(facet, emptyList())
  val projectResources = ProjectResourceRepository.createForTest(facet, listOf<LocalResourceRepository>(moduleResources))
  val appResources = AppResourceRepository.createForTest(facet, listOf<LocalResourceRepository>(projectResources), emptyList())
  val aar = getTestAarRepository()
  appResources.updateRoots(listOf(projectResources, aar), mutableListOf(aar))
  return appResources
}


fun getTestAarRepository(): FileResourceRepository {
  return FileResourceRepository.get(
    Paths.get(AndroidTestBase.getTestDataPath(), "rendering", FilenameConstants.EXPLODED_AAR, "my_aar_lib", "res").toFile(),
    AAR_LIBRARY_NAME
  )
}
