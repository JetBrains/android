/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.qsync

import com.google.idea.blaze.base.model.primitives.LanguageClass
import com.google.idea.blaze.base.model.primitives.WorkspaceType
import com.google.idea.blaze.base.projectview.ProjectViewSet
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings
import com.intellij.openapi.components.service
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.application

/**
 * Query sync configuration for the languages and frameworks supported by the query sync out of the box.
 */
interface QuerySyncLanguageSettings {
  /**
   * A provider that knows how to fetch configuration for Java, Kotlin and Android.
   *
   * A level of indirection is required to keep language support out of the base plugin apart from the core query sync that hardcodes
   * support for these frameworks and languages.
   */
  interface Provider {
    fun create(
      projectViewSet: ProjectViewSet,
      workspaceLanguageSettings: WorkspaceLanguageSettings,
    ): QuerySyncLanguageSettings
  }

  interface Java {
    val languageLevel: LanguageLevel

    /**
     * True if this is a pure Java workspace, i.e. the IDE will not be configured to support Android development.
     */
    val isJavaWorkspace: Boolean
  }

  sealed interface Kotlin {
    object NotSupported: Kotlin
    object Settings: Kotlin
  }

  sealed interface Android {
    object NotSupported: Android
    interface Settings: Android {
      val sdk: String?
      val minSdk: Int?
    }
  }

  val java: Java
  val kotlin: Kotlin
  val android: Android

  companion object {
    @JvmStatic
    fun from(
      projectViewSet: ProjectViewSet,
      workspaceLanguageSettings: WorkspaceLanguageSettings
    ): QuerySyncLanguageSettings {
      // Although query sync hardcodes languages it supports, Android support is in a different target and cannot be referred from here.
      return application.service<Provider>().create(projectViewSet, workspaceLanguageSettings)
    }

    @JvmStatic
    fun from(
      projectViewSet: ProjectViewSet,
      workspaceLanguageSettings: WorkspaceLanguageSettings,
      javaLanguageLevel: LanguageLevel,
      androidSdk: String?,
      androidMinSdk: Int?,
    ): QuerySyncLanguageSettings {
      return object: QuerySyncLanguageSettings {
        override val java: Java = object: Java {
          override val languageLevel: LanguageLevel = javaLanguageLevel
          override val isJavaWorkspace: Boolean = workspaceLanguageSettings.workspaceType == WorkspaceType.JAVA
        }

        override val kotlin: Kotlin =
          when (LanguageClass.KOTLIN in workspaceLanguageSettings.activeLanguages) {
            true -> Kotlin.Settings
            false -> Kotlin.NotSupported
          }

        override val android: Android =
          when(workspaceLanguageSettings.workspaceType) {
            WorkspaceType.ANDROID -> object: Android.Settings{
              override val sdk: String? = androidSdk
              override val minSdk: Int? = androidMinSdk
            }
            else -> Android.NotSupported
          }
      }
    }
  }
}

