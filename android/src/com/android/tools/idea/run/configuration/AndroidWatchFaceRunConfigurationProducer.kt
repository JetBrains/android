/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.intellij.psi.PsiElement

/**
 * Producer of [AndroidWatchFaceConfiguration] for classes that extend `android.support.wearable.watchface.WatchFaceService` or
 * `androidx.wear.watchface.WatchFaceService`.
 */
class AndroidWatchFaceRunConfigurationProducer :
  AndroidWearRunConfigurationProducer<AndroidWatchFaceConfiguration>(AndroidWatchFaceConfigurationType::class.java) {

  override fun isValidService(psiElement: PsiElement): Boolean = psiElement.isValidWatchFaceService()
}

internal fun PsiElement.isValidWatchFaceService(): Boolean {
  return WearBaseClasses.WATCH_FACES.any { wearBase -> isSubtypeOf(wearBase) }
}
