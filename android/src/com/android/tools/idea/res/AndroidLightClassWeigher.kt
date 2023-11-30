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
package com.android.tools.idea.res

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import com.intellij.psi.Weigher
import com.intellij.psi.util.ProximityLocation
import com.intellij.psi.util.proximity.ProximityWeigher
import org.jetbrains.android.augment.ManifestClass

/**
 * Custom [Weigher] that puts light classes from the same module before others.
 *
 * Normally this is done by [com.intellij.psi.util.proximity.ExplicitlyImportedWeigher] which gives
 * a premium to classes from the same module and package. Unfortunately its logic for finding the
 * [PsiPackage] of an [PsiElement] doesn't work for light classes.
 */
class AndroidLightClassWeigher : ProximityWeigher() {
  override fun weigh(element: PsiElement, location: ProximityLocation): Comparable<*> {
    return when (element) {
      is ModuleRClass -> element.facet.module == location.positionModule
      is ManifestClass -> element.facet.module == location.positionModule
      else -> false
    }
  }
}
