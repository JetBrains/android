/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.intellij.testFramework.fixtures.CodeInsightTestFixture

fun addDaggerAndHiltClasses(fixture: CodeInsightTestFixture) {
  fixture.addFileToProject(
    "dagger/Module.java",
    // language=JAVA
    """
      package dagger;

      public @interface Module {
        Class<?>[] includes() default {};
        Class<?>[] subcomponents() default {};
      }
      """
      .trimIndent()
  )
  fixture.addFileToProject(
    "dagger/Provides.java",
    // language=JAVA
    """
      package dagger;

      public @interface Provides {}
      """
      .trimIndent()
  )
  fixture.addFileToProject(
    "dagger/Binds.java",
    // language=JAVA
    """
      package dagger;

      public @interface Binds {}
      """
      .trimIndent()
  )
  fixture.addFileToProject(
    "dagger/BindsInstance.java",
    // language=JAVA
    """
      package dagger;

      public @interface BindsInstance {}
      """
      .trimIndent()
  )
  fixture.addFileToProject(
    "javax/inject/Inject.java",
    // language=JAVA
    """
      package javax.inject;

      public @interface Inject {}
      """
      .trimIndent()
  )

  fixture.addFileToProject(
    "javax/inject/Qualifier.java",
    // language=JAVA
    """
      package javax.inject;

      public @interface Qualifier {}
      """
      .trimIndent()
  )
  fixture.addFileToProject(
    "dagger/Component.java",
    // language=JAVA
    """
      package dagger;

      public @interface Component {
         Class<?>[] modules() default {};
         Class<?>[] dependencies() default {};
      }
      """
      .trimIndent()
  )
  fixture.addFileToProject(
    "dagger/Subcomponent.java",
    // language=JAVA
    """
      package dagger;

      public @interface Subcomponent {
         @interface Builder {}
         @interface Factory {}
         Class<?>[] modules() default {};
      }
      """
      .trimIndent()
  )
  fixture.addFileToProject(
    "dagger/hilt/EntryPoint.java",
    // language=JAVA
    """
      package dagger.hilt;

      public @interface EntryPoint {}
      """
      .trimIndent()
  )
  fixture.addFileToProject(
    "androidx/hilt/work/WorkerInject.java",
    // language=JAVA
    """
      package androidx.hilt.work;

      public @interface WorkerInject {}
      """
      .trimIndent()
  )
  fixture.addFileToProject(
    "androidx/hilt/lifecycle/ViewModelInject.java",
    // language=JAVA
    """
      package androidx.hilt.lifecycle;

      public @interface ViewModelInject {}
      """
      .trimIndent()
  )
  fixture.addFileToProject(
    "dagger/Lazy.java",
    // language=JAVA
    """
      package dagger;

      public interface Lazy<T> {
        T get();
      }
      """
      .trimIndent()
  )
  fixture.addFileToProject(
    "javax/inject/Provider.java",
    // language=JAVA
    """
      package javax.inject;

      public interface Provider<T> {
        T get();
      }
      """
      .trimIndent()
  )
  fixture.addFileToProject(
    "dagger/assisted/AssistedInject.java",
    // language=JAVA
    """
      package dagger.assisted;

      public @interface AssistedInject {}
      """
      .trimIndent()
  )
  fixture.addFileToProject(
    "dagger/assisted/AssistedFactory.java",
    // language=JAVA
    """
      package dagger.assisted;

      public @interface AssistedFactory {}
      """
      .trimIndent()
  )
}
