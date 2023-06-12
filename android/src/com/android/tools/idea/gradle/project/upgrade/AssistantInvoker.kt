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
package com.android.tools.idea.gradle.project.upgrade

import com.android.annotations.concurrency.Slow
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

interface AssistantInvoker {
  /**
   * Perform a specialized "upgrade" of the build files to convert usages of the old, long-deprecated `compile` dependency configuration
   * (and its variants) to its replacement `api` or `implementation` (depending on the context in which that dependency is declared).
   */
  @Slow
  fun performDeprecatedConfigurationsUpgrade(project: Project, element: PsiElement)

  /**
   * If policy, preferences and available versions of the Android Gradle plugin allow, notify the user in some fashion to recommend
   * that they use the Upgrade Assistant to update their build files.
   */
  fun maybeRecommendPluginUpgrade(project: Project, info: AndroidPluginInfo)

  /**
   * Quietly expires all notifications related to Android Gradle plugin upgrade recommendations.
   */
  fun expireProjectUpgradeNotifications(project: Project)

  /**
   * Displays a message to alert the user if they have disabled forced upgrades
   */
  fun displayForceUpdatesDisabledMessage(project: Project)
}
