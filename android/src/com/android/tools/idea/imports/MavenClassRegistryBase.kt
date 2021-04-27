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
package com.android.tools.idea.imports

/**
 * Lookup from class names to maven.google.com artifacts.
 */
abstract class MavenClassRegistryBase {
  /**
   * Library for each of the GMaven artifact.
   *
   * @property artifact maven coordinate: groupId:artifactId, please note version is not included here.
   * @property packageName fully qualified package name which is used for the following import purposes.
   * @property version the version of the [artifact].
   */
  data class Library(val artifact: String, val packageName: String, val version: String? = null)

  /**
   * Given a class name, returns the likely collection of [Library] objects for the following quick fixes purposes.
   */
  abstract fun findLibraryData(className: String, useAndroidX: Boolean): Collection<Library>

  /**
   * For the given runtime artifact, if it also requires an annotation processor, provide it
   */
  fun findAnnotationProcessor(artifact: String): String? {
    return when (artifact) {
      "androidx.room:room-runtime",
      "android.arch.persistence.room:runtime" -> "android.arch.persistence.room:compiler"
      "androidx.remotecallback:remotecallback" -> "androidx.remotecallback:remotecallback-processor"
      else -> null
    }
  }

  /**
   * For the given runtime artifact, if Kotlin is the adopted language, the corresponding ktx library is provided.
   */
  fun findKtxLibrary(artifact: String): String? {
    return when (artifact) {
      "android.arch.work:work-runtime" -> "android.arch.work:work-runtime-ktx"
      "android.arch.navigation:navigation-runtime" -> "android.arch.navigation:navigation-runtime-ktx"
      "android.arch.navigation:navigation-fragment" -> "android.arch.navigation:navigation-fragment-ktx"
      "android.arch.navigation:navigation-common" -> "android.arch.navigation:navigation-common-ktx"
      "android.arch.navigation:navigation-ui" -> "android.arch.navigation:navigation-ui-ktx"
      "androidx.activity:activity" -> "androidx.activity:activity-ktx"
      "androidx.collection:collection" -> "androidx.collection:collection-ktx"
      "androidx.core:core" -> "androidx.core:core-ktx"
      "androidx.dynamicanimation:dynamicanimation" -> "androidx.dynamicanimation:dynamicanimation-ktx"
      "androidx.fragment:fragment" -> "androidx.fragment:fragment-ktx"
      "androidx.lifecycle:lifecycle-livedata" -> "androidx.lifecycle:lifecycle-livedata-ktx"
      "androidx.lifecycle:lifecycle-livedata-core" -> "androidx.lifecycle:lifecycle-livedata-core-ktx"
      "androidx.lifecycle:lifecycle-reactivestreams" -> "androidx.lifecycle:lifecycle-reactivestreams-ktx"
      "androidx.lifecycle:lifecycle-viewmodel" -> "androidx.lifecycle:lifecycle-viewmodel-ktx"
      "androidx.navigation:navigation-runtime" -> "androidx.navigation:navigation-runtime-ktx"
      "androidx.navigation:navigation-fragment" -> "androidx.navigation:navigation-fragment-ktx"
      "androidx.navigation:navigation-ui" -> "androidx.navigation:navigation-ui-ktx"
      "androidx.paging:paging-runtime" -> "androidx.paging:paging-runtime-ktx"
      "androidx.paging:paging-common" -> "androidx.paging:paging-common-ktx"
      "androidx.paging:paging-rxjava2" -> "androidx.paging:paging-rxjava2-ktx"
      "androidx.palette:palette" -> "androidx.palette:palette-ktx"
      "androidx.preference:preference" -> "androidx.preference:preference-ktx"
      "androidx.slice:slice-builders" -> "androidx.slice:slice-builders-ktx"
      "androidx.sqlite:sqlite" -> "androidx.sqlite:sqlite-ktx"
      "androidx.work:work-runtime" -> "androidx.work:work-runtime-ktx"
      "com.google.android.play:core" -> "com.google.android.play:core-ktx"
      "com.google.firebase:firebase-common" -> "com.google.firebase:firebase-common-ktx"
      "com.google.firebase:firebase-config" -> "com.google.firebase:firebase-config-ktx"
      "com.google.firebase:firebase-crashlytics" -> "com.google.firebase:firebase-crashlytics-ktx"
      "com.google.firebase:firebase-database" -> "com.google.firebase:firebase-database-ktx"
      "com.google.firebase:firebase-dynamic-links" -> "com.google.firebase:firebase-dynamic-links-ktx"
      "com.google.firebase:firebase-firestore" -> "com.google.firebase:firebase-firestore-ktx"
      "com.google.firebase:firebase-functions" -> "com.google.firebase:firebase-functions-ktx"
      "com.google.firebase:firebase-inappmessaging" -> "com.google.firebase:firebase-inappmessaging-ktx"
      "com.google.firebase:firebase-inappmessaging-display" -> "com.google.firebase:firebase-inappmessaging-display-ktx"
      "com.google.firebase:firebase-perf" -> "com.google.firebase:firebase-perf-ktx"
      "com.google.firebase:firebase-storage" -> "com.google.firebase:firebase-storage-ktx"
      else -> null
    }
  }
}