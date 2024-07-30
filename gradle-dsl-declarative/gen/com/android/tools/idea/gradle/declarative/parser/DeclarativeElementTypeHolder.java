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
package com.android.tools.idea.gradle.declarative.parser;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.android.tools.idea.gradle.declarative.psi.DeclarativeElementType;
import com.android.tools.idea.gradle.declarative.psi.impl.*;
import com.intellij.psi.impl.source.tree.CompositePsiElement;

public interface DeclarativeElementTypeHolder {

  IElementType ARGUMENTS_LIST = new DeclarativeElementType("ARGUMENTS_LIST");
  IElementType ASSIGNMENT = new DeclarativeElementType("ASSIGNMENT");
  IElementType BARE = new DeclarativeElementType("BARE");
  IElementType BLOCK = new DeclarativeElementType("BLOCK");
  IElementType BLOCK_GROUP = new DeclarativeElementType("BLOCK_GROUP");
  IElementType FACTORY = new DeclarativeElementType("FACTORY");
  IElementType IDENTIFIER = new DeclarativeElementType("IDENTIFIER");
  IElementType LITERAL = new DeclarativeElementType("LITERAL");
  IElementType PROPERTY = new DeclarativeElementType("PROPERTY");
  IElementType QUALIFIED = new DeclarativeElementType("QUALIFIED");

  IElementType BLOCK_COMMENT = new DeclarativeTokenType("BLOCK_COMMENT");
  IElementType BOOLEAN = new DeclarativeTokenType("boolean");
  IElementType LINE_COMMENT = new DeclarativeTokenType("line_comment");
  IElementType MULTILINE_STRING = new DeclarativeTokenType("multiline_string");
  IElementType NULL = new DeclarativeTokenType("null");
  IElementType NUMBER = new DeclarativeTokenType("number");
  IElementType OP_COMMA = new DeclarativeTokenType(",");
  IElementType OP_DOT = new DeclarativeTokenType(".");
  IElementType OP_EQ = new DeclarativeTokenType("=");
  IElementType OP_LBRACE = new DeclarativeTokenType("{");
  IElementType OP_LPAREN = new DeclarativeTokenType("(");
  IElementType OP_RBRACE = new DeclarativeTokenType("}");
  IElementType OP_RPAREN = new DeclarativeTokenType(")");
  IElementType STRING = new DeclarativeTokenType("string");
  IElementType TOKEN = new DeclarativeTokenType("token");

  class Factory {
    public static CompositePsiElement createElement(IElementType type) {
       if (type == ARGUMENTS_LIST) {
        return new DeclarativeArgumentsListImpl(type);
      }
      else if (type == ASSIGNMENT) {
        return new DeclarativeAssignmentImpl(type);
      }
      else if (type == BARE) {
        return new DeclarativeBareImpl(type);
      }
      else if (type == BLOCK) {
        return new DeclarativeBlockImpl(type);
      }
      else if (type == BLOCK_GROUP) {
        return new DeclarativeBlockGroupImpl(type);
      }
      else if (type == FACTORY) {
        return new DeclarativeFactoryImpl(type);
      }
      else if (type == IDENTIFIER) {
        return new DeclarativeIdentifierImpl(type);
      }
      else if (type == LITERAL) {
        return new DeclarativeLiteralImpl(type);
      }
      else if (type == QUALIFIED) {
        return new DeclarativeQualifiedImpl(type);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
