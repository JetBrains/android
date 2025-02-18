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

// ATTENTION: This file has been automatically generated from declarative.bnf. Do not edit it manually.
package com.android.tools.idea.gradle.dcl.lang.parser;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeElementType;
import com.android.tools.idea.gradle.dcl.lang.psi.impl.*;
import com.intellij.psi.impl.source.tree.CompositePsiElement;

public interface DeclarativeElementTypeHolder {

  IElementType ARGUMENT = new DeclarativeElementType("ARGUMENT");
  IElementType ARGUMENTS_LIST = new DeclarativeElementType("ARGUMENTS_LIST");
  IElementType ASSIGNABLE_BARE = new DeclarativeElementType("ASSIGNABLE_BARE");
  IElementType ASSIGNABLE_PROPERTY = new DeclarativeElementType("ASSIGNABLE_PROPERTY");
  IElementType ASSIGNABLE_QUALIFIED = new DeclarativeElementType("ASSIGNABLE_QUALIFIED");
  IElementType ASSIGNMENT = new DeclarativeElementType("ASSIGNMENT");
  IElementType BARE = new DeclarativeElementType("BARE");
  IElementType BARE_RECEIVER = new DeclarativeElementType("BARE_RECEIVER");
  IElementType BLOCK = new DeclarativeElementType("BLOCK");
  IElementType BLOCK_GROUP = new DeclarativeElementType("BLOCK_GROUP");
  IElementType EMBEDDED_FACTORY = new DeclarativeElementType("EMBEDDED_FACTORY");
  IElementType FACTORY_PROPERTY_RECEIVER = new DeclarativeElementType("FACTORY_PROPERTY_RECEIVER");
  IElementType FACTORY_RECEIVER = new DeclarativeElementType("FACTORY_RECEIVER");
  IElementType IDENTIFIER = new DeclarativeElementType("IDENTIFIER");
  IElementType LITERAL = new DeclarativeElementType("LITERAL");
  IElementType PAIR = new DeclarativeElementType("PAIR");
  IElementType PROPERTY = new DeclarativeElementType("PROPERTY");
  IElementType PROPERTY_RECEIVER = new DeclarativeElementType("PROPERTY_RECEIVER");
  IElementType QUALIFIED = new DeclarativeElementType("QUALIFIED");
  IElementType QUALIFIED_RECEIVER = new DeclarativeElementType("QUALIFIED_RECEIVER");
  IElementType RECEIVER_PREFIXED_FACTORY = new DeclarativeElementType("RECEIVER_PREFIXED_FACTORY");
  IElementType SIMPLE_FACTORY = new DeclarativeElementType("SIMPLE_FACTORY");

  IElementType BLOCK_COMMENT = new DeclarativeTokenType("BLOCK_COMMENT");
  IElementType BOOLEAN = new DeclarativeTokenType("boolean");
  IElementType DOUBLE_LITERAL = new DeclarativeTokenType("double_literal");
  IElementType INTEGER_LITERAL = new DeclarativeTokenType("integer_literal");
  IElementType LINE_COMMENT = new DeclarativeTokenType("line_comment");
  IElementType LONG_LITERAL = new DeclarativeTokenType("long_literal");
  IElementType MULTILINE_STRING_LITERAL = new DeclarativeTokenType("multiline_string_literal");
  IElementType NULL = new DeclarativeTokenType("null");
  IElementType ONE_LINE_STRING_LITERAL = new DeclarativeTokenType("one_line_string_literal");
  IElementType OP_COMMA = new DeclarativeTokenType(",");
  IElementType OP_DOT = new DeclarativeTokenType(".");
  IElementType OP_EQ = new DeclarativeTokenType("=");
  IElementType OP_LBRACE = new DeclarativeTokenType("{");
  IElementType OP_LPAREN = new DeclarativeTokenType("(");
  IElementType OP_PLUS_EQ = new DeclarativeTokenType("+=");
  IElementType OP_RBRACE = new DeclarativeTokenType("}");
  IElementType OP_RPAREN = new DeclarativeTokenType(")");
  IElementType OP_TO = new DeclarativeTokenType("to");
  IElementType SEMI = new DeclarativeTokenType(";");
  IElementType TOKEN = new DeclarativeTokenType("token");
  IElementType UNSIGNED_INTEGER = new DeclarativeTokenType("unsigned_integer");
  IElementType UNSIGNED_LONG = new DeclarativeTokenType("unsigned_long");

  class Factory {
    public static CompositePsiElement createElement(IElementType type) {
       if (type == ARGUMENT) {
        return new DeclarativeArgumentImpl(type);
      }
      else if (type == ARGUMENTS_LIST) {
        return new DeclarativeArgumentsListImpl(type);
      }
      else if (type == ASSIGNABLE_BARE) {
        return new DeclarativeAssignableBareImpl(type);
      }
      else if (type == ASSIGNABLE_QUALIFIED) {
        return new DeclarativeAssignableQualifiedImpl(type);
      }
      else if (type == ASSIGNMENT) {
        return new DeclarativeAssignmentImpl(type);
      }
      else if (type == BARE) {
        return new DeclarativeBareImpl(type);
      }
      else if (type == BARE_RECEIVER) {
        return new DeclarativeBareReceiverImpl(type);
      }
      else if (type == BLOCK) {
        return new DeclarativeBlockImpl(type);
      }
      else if (type == BLOCK_GROUP) {
        return new DeclarativeBlockGroupImpl(type);
      }
      else if (type == EMBEDDED_FACTORY) {
        return new DeclarativeEmbeddedFactoryImpl(type);
      }
      else if (type == FACTORY_PROPERTY_RECEIVER) {
        return new DeclarativeFactoryPropertyReceiverImpl(type);
      }
      else if (type == IDENTIFIER) {
        return new DeclarativeIdentifierImpl(type);
      }
      else if (type == LITERAL) {
        return new DeclarativeLiteralImpl(type);
      }
      else if (type == PAIR) {
        return new DeclarativePairImpl(type);
      }
      else if (type == QUALIFIED) {
        return new DeclarativeQualifiedImpl(type);
      }
      else if (type == QUALIFIED_RECEIVER) {
        return new DeclarativeQualifiedReceiverImpl(type);
      }
      else if (type == RECEIVER_PREFIXED_FACTORY) {
        return new DeclarativeReceiverPrefixedFactoryImpl(type);
      }
      else if (type == SIMPLE_FACTORY) {
        return new DeclarativeSimpleFactoryImpl(type);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
