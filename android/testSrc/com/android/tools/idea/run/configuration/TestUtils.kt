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
package com.android.tools.idea.run.configuration

import com.intellij.psi.PsiClass
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

internal fun JavaCodeInsightTestFixture.addWatchFace(): PsiClass {
  addFileToProject(
    "src/lib/WatchFace.kt",
    """
      package androidx.wear.watchface

      open class WatchFaceService
    """.trimIndent())

  addFileToProject(
    "src/com/example/MyWatchFace.kt",
    """
      package com.example

      class MyWatchFace : androidx.wear.watchface.WatchFaceService
    """.trimIndent())

  return javaFacade.findClass("com.example.MyWatchFace")
}

internal fun JavaCodeInsightTestFixture.addComplication(): PsiClass {
  addFileToProject(
    "src/lib/ComplicationDataSourceService.kt",
    """
      package androidx.wear.watchface.complications.datasource

      open class ComplicationDataSourceService
    """.trimIndent())

  addFileToProject(
    "src/com/example/MyComplication.kt",
    """
      package com.example

      class MyComplication : androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
    """.trimIndent())

  return javaFacade.findClass("com.example.MyComplication")
}