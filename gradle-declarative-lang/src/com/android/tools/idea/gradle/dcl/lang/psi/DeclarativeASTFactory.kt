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
package com.android.tools.idea.gradle.dcl.lang.psi

import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.ARGUMENT
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.ARGUMENTS_LIST
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.ASSIGNABLE_BARE
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.ASSIGNABLE_QUALIFIED
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.ASSIGNMENT
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.BARE
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.FACTORY_PROPERTY_RECEIVER
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.BARE_RECEIVER
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.BLOCK
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.BLOCK_GROUP
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.EMBEDDED_FACTORY
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.IDENTIFIER
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.LITERAL
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.QUALIFIED
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.QUALIFIED_RECEIVER
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.RECEIVER_PREFIXED_FACTORY
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.SIMPLE_FACTORY
import com.android.tools.idea.gradle.dcl.lang.psi.impl.DeclarativeArgumentImpl
import com.android.tools.idea.gradle.dcl.lang.psi.impl.DeclarativeArgumentsListImpl
import com.android.tools.idea.gradle.dcl.lang.psi.impl.DeclarativeAssignableBareImpl
import com.android.tools.idea.gradle.dcl.lang.psi.impl.DeclarativeAssignableQualifiedImpl
import com.android.tools.idea.gradle.dcl.lang.psi.impl.DeclarativeAssignmentImpl
import com.android.tools.idea.gradle.dcl.lang.psi.impl.DeclarativeBareImpl
import com.android.tools.idea.gradle.dcl.lang.psi.impl.DeclarativeBareReceiverImpl
import com.android.tools.idea.gradle.dcl.lang.psi.impl.DeclarativeBlockGroupImpl
import com.android.tools.idea.gradle.dcl.lang.psi.impl.DeclarativeBlockImpl
import com.android.tools.idea.gradle.dcl.lang.psi.impl.DeclarativeEmbeddedFactoryImpl
import com.android.tools.idea.gradle.dcl.lang.psi.impl.DeclarativeIdentifierImpl
import com.android.tools.idea.gradle.dcl.lang.psi.impl.DeclarativeLiteralImpl
import com.android.tools.idea.gradle.dcl.lang.psi.impl.DeclarativeFactoryPropertyReceiverImpl
import com.android.tools.idea.gradle.dcl.lang.psi.impl.DeclarativeQualifiedImpl
import com.android.tools.idea.gradle.dcl.lang.psi.impl.DeclarativeQualifiedReceiverImpl
import com.android.tools.idea.gradle.dcl.lang.psi.impl.DeclarativeReceiverPrefixedFactoryImpl
import com.android.tools.idea.gradle.dcl.lang.psi.impl.DeclarativeSimpleFactoryImpl
import com.intellij.lang.ASTFactory
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.tree.IElementType

class DeclarativeASTFactory : ASTFactory() {
  override fun createComposite(type: IElementType): CompositeElement = when (type) {
    ARGUMENTS_LIST -> DeclarativeArgumentsListImpl(type)
    ASSIGNABLE_BARE -> DeclarativeAssignableBareImpl(type)
    ASSIGNABLE_QUALIFIED -> DeclarativeAssignableQualifiedImpl(type)
    ASSIGNMENT -> DeclarativeAssignmentImpl(type)
    BARE -> DeclarativeBareImpl(type)
    BARE_RECEIVER -> DeclarativeBareReceiverImpl(type)
    BLOCK -> DeclarativeBlockImpl(type)
    BLOCK_GROUP -> DeclarativeBlockGroupImpl(type)
    FACTORY_PROPERTY_RECEIVER -> DeclarativeFactoryPropertyReceiverImpl(type)
    SIMPLE_FACTORY -> DeclarativeSimpleFactoryImpl(type)
    QUALIFIED_RECEIVER -> DeclarativeQualifiedReceiverImpl(type)
    RECEIVER_PREFIXED_FACTORY -> DeclarativeReceiverPrefixedFactoryImpl(type)
    EMBEDDED_FACTORY -> DeclarativeEmbeddedFactoryImpl(type)
    IDENTIFIER -> DeclarativeIdentifierImpl(type)
    LITERAL -> DeclarativeLiteralImpl(type)
    QUALIFIED -> DeclarativeQualifiedImpl(type)
    ARGUMENT -> DeclarativeArgumentImpl(type)

    else -> error("Unknown Declarative element type: `$type`")
  }
}