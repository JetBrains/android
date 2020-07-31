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
package com.android.tools.idea.lang.contentAccess

import com.android.tools.idea.flags.StudioFlags
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

abstract class ContentAccessTestCase : JavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    StudioFlags.CONTENT_ACCESS_SUPPORT_ENABLED.override(true)
    super.setUp()
    myFixture.addFileToProject(
      "androidx/contentaccess/Annotations.kt",
      // language=kotlin
      """
      package androidx.contentaccess

      import kotlin.reflect.KClass

      @Retention(AnnotationRetention.BINARY)
      @Target(AnnotationTarget.CLASS)
      annotation class ContentEntity(val uri: String = "")

      @Retention(AnnotationRetention.BINARY)
      @Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
      annotation class ContentPrimaryKey(val columnName: String)

      @Retention(AnnotationRetention.BINARY)
      @Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
      annotation class ContentColumn(val columnName: String)

      @Retention(AnnotationRetention.BINARY)
      @Target(AnnotationTarget.CLASS)
      annotation class ContentAccessObject(val contentEntity: KClass<*> = Void::class)

      @Retention(AnnotationRetention.BINARY)
      @Target(AnnotationTarget.FUNCTION)
      annotation class ContentQuery(
          val projection: Array<String> = arrayOf(),
          val selection: String = "",
          val orderBy: Array<String> = arrayOf(),
          val uri: String = "",
          val contentEntity: KClass<*> = Void::class
      )

      @Retention(AnnotationRetention.BINARY)
      @Target(AnnotationTarget.FUNCTION)
      annotation class ContentInsert(val uri: String)

      @Retention(AnnotationRetention.BINARY)
      @Target(AnnotationTarget.FUNCTION)
      annotation class ContentDelete(
          val selection: String,
          val uri: String
      )

      @Retention(AnnotationRetention.BINARY)
      @Target(AnnotationTarget.FUNCTION)
      annotation class ContentUpdate(
          val where: String = "",
          val uri: String = "",
          val contentEntity: KClass<*> = Void::class
      )
      """.trimIndent()
    )
  }

  override fun tearDown() {
    super.tearDown()
    StudioFlags.CONTENT_ACCESS_SUPPORT_ENABLED.clearOverride()
  }
}
