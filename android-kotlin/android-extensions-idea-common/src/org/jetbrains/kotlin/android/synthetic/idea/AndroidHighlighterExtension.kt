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

package org.jetbrains.kotlin.android.synthetic.idea

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.android.synthetic.res.AndroidSyntheticProperty
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingVisitorExtension

class AndroidHighlighterExtension : KotlinHighlightingVisitorExtension() {
    override fun highlightDeclaration(
        elementToHighlight: PsiElement,
        descriptor: DeclarationDescriptor
    ) = when (descriptor) {
        is AndroidSyntheticProperty -> KotlinHighlightInfoTypeSemanticNames.ANDROID_EXTENSIONS_PROPERTY_CALL
        else -> null
    }
}
