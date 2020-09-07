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
package com.android.tools.idea.dagger

import com.android.tools.idea.flags.StudioFlags
import org.jetbrains.android.JavaCodeInsightFixtureAdtTestCase

abstract class DaggerTestCase : JavaCodeInsightFixtureAdtTestCase() {
  override fun setUp() {
    StudioFlags.DAGGER_SUPPORT_ENABLED.override(true)
    super.setUp()
    myFixture.addClass(
      // language=JAVA
      """
      package dagger;

      public @interface Module {
        Class<?>[] includes() default {};
        Class<?>[] subcomponents() default {};
      }
      """.trimIndent()
    )
    myFixture.addClass(
      // language=JAVA
      """
      package dagger;

      public @interface Provides {}
      """.trimIndent()
    )
    myFixture.addClass(
      // language=JAVA
      """
      package dagger;

      public @interface Binds {}
      """.trimIndent()
    )
    myFixture.addClass(
      // language=JAVA
      """
      package dagger;

      public @interface BindsInstance {}
      """.trimIndent()
    )
    myFixture.addClass(
      // language=JAVA
      """
      package javax.inject;

      public @interface Inject {}
      """.trimIndent()
    )

    myFixture.addClass(
      // language=JAVA
      """
      package javax.inject;

      public @interface Qualifier {}
      """.trimIndent()
    )
    myFixture.addClass(
      // language=JAVA
      """
      package dagger;

      public @interface Component {
         Class<?>[] modules() default {};
         Class<?>[] dependencies() default {};
      }
      """.trimIndent()
    )
    myFixture.addClass(
      // language=JAVA
      """
      package dagger;

      public @interface Subcomponent {
         @interface Builder {}
         @interface Factory {}
         Class<?>[] modules() default {};
      }
      """.trimIndent()
    )
    myFixture.addClass(
      // language=JAVA
      """
      package dagger.hilt;

      public @interface EntryPoint {}
      """.trimIndent()
    )
    myFixture.addClass(
      // language=JAVA
      """
      package androidx.hilt.work;

      public @interface WorkerInject {}
      """.trimIndent()
    )
    myFixture.addClass(
      // language=JAVA
      """
      package androidx.hilt.lifecycle;

      public @interface ViewModelInject {}
      """.trimIndent()
    )
    myFixture.addClass(
      // language=JAVA
      """
      package dagger;

      public interface Lazy<T> {
        T get();
      }
      """.trimIndent()
    )
    myFixture.addClass(
      // language=JAVA
      """
      package javax.inject;

      public interface Provider<T> { 
        T get();
      }
      """.trimIndent()
    )
  }

  override fun tearDown() {
    StudioFlags.DAGGER_SUPPORT_ENABLED.clearOverride()
    super.tearDown()
  }
}