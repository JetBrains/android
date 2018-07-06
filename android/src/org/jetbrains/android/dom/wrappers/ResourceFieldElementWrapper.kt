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
package org.jetbrains.android.dom.wrappers

import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenameProcessor
import org.jetbrains.android.AndroidResourceRenameResourceProcessor
import org.jetbrains.android.augment.AndroidLightField

/**
 * Wrapper for [AndroidLightField] used during refactoring, to convince [RenameProcessor] the field should be passed to
 * [AndroidResourceRenameResourceProcessor].
 */
class ResourceFieldElementWrapper(
  private val wrappee: AndroidLightField
) : ResourceElementWrapper,
    PsiElement by wrappee,
    NavigationItem by wrappee {
  override fun getWrappee(): AndroidLightField = wrappee
  override fun isWritable(): Boolean = true
}
