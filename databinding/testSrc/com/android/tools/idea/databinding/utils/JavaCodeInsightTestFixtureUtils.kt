/*
 * Copyright (C) 2019 The Android Open Source Project
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
@file:JvmName("JavaCodeInsightTestFixtureUtils")

package com.android.tools.idea.databinding.utils

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.android.tools.idea.databinding.finders.BindingScopeEnlarger
/**
 * Finds class with the given name in the [PsiElement.resolveScope] of the context element.
 *
 * This means using the same scope as the real code editor will use and also makes this method work with light classes, since
 * [PsiElement.resolveScope] is subject to [ResolveScopeEnlarger]s.
 *
 * @see JavaCodeInsightTestFixture.findClass
 * @see PsiElement.resolveScope
 * @see BindingScopeEnlarger
 */
fun JavaCodeInsightTestFixture.findClass(name: String, context: PsiElement): PsiClass? {
  return javaFacade.findClass(name, context.resolveScope)
}