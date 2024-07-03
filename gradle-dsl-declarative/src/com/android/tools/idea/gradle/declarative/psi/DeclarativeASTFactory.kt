/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.declarative.psi

import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.ARGUMENTS_LIST
import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.ASSIGNMENT
import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.BARE
import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.BLOCK
import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.BLOCK_GROUP
import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.FACTORY
import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.IDENTIFIER
import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.LITERAL
import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.QUALIFIED
import com.android.tools.idea.gradle.declarative.psi.impl.DeclarativeArgumentsListImpl
import com.android.tools.idea.gradle.declarative.psi.impl.DeclarativeAssignmentImpl
import com.android.tools.idea.gradle.declarative.psi.impl.DeclarativeBareImpl
import com.android.tools.idea.gradle.declarative.psi.impl.DeclarativeBlockGroupImpl
import com.android.tools.idea.gradle.declarative.psi.impl.DeclarativeBlockImpl
import com.android.tools.idea.gradle.declarative.psi.impl.DeclarativeFactoryImpl
import com.android.tools.idea.gradle.declarative.psi.impl.DeclarativeIdentifierImpl
import com.android.tools.idea.gradle.declarative.psi.impl.DeclarativeLiteralImpl
import com.android.tools.idea.gradle.declarative.psi.impl.DeclarativeQualifiedImpl
import com.intellij.lang.ASTFactory
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.tree.IElementType

class DeclarativeASTFactory : ASTFactory() {
  override fun createComposite(type: IElementType): CompositeElement = when (type) {
    ARGUMENTS_LIST -> DeclarativeArgumentsListImpl(type)
    ASSIGNMENT -> DeclarativeAssignmentImpl(type)
    BARE -> DeclarativeBareImpl(type)
    BLOCK -> DeclarativeBlockImpl(type)
    BLOCK_GROUP -> DeclarativeBlockGroupImpl(type)
    FACTORY -> DeclarativeFactoryImpl(type)
    IDENTIFIER -> DeclarativeIdentifierImpl(type)
    LITERAL -> DeclarativeLiteralImpl(type)
    QUALIFIED -> DeclarativeQualifiedImpl(type)

    else -> error("Unknown Declarative element type: `$type`")
  }
}