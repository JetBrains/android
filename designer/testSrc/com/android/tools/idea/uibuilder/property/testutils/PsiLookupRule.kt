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
package com.android.tools.idea.uibuilder.property.testutils

import com.android.tools.idea.uibuilder.property.NlPsiLookup
import com.intellij.psi.PsiClass
import org.jetbrains.android.facet.AndroidFacet
import org.junit.rules.ExternalResource

/**
 * Rule for PsiClass lookup.
 */
class PsiLookupRule(private val facet: () -> AndroidFacet?): ExternalResource() {
  private var lookup: NlPsiLookup? = null

  fun classOf(tagName: String): PsiClass? = lookup?.classOf(tagName)

  override fun before() {
    lookup = facet()?.let { NlPsiLookup(it) }
  }

  override fun after() {
    lookup = null
  }
}
