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

// ATTENTION: This file has been automatically generated from something.bnf. Do not edit it manually.
package com.android.tools.idea.gradle.something.parser;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.android.tools.idea.gradle.something.psi.SomethingElementType;
import com.android.tools.idea.gradle.something.psi.impl.*;

public interface SomethingElementTypeHolder {

  IElementType ARGUMENTS_LIST = new SomethingElementType("ARGUMENTS_LIST");
  IElementType ASSIGNMENT = new SomethingElementType("ASSIGNMENT");
  IElementType BARE = new SomethingElementType("BARE");
  IElementType BLOCK = new SomethingElementType("BLOCK");
  IElementType BLOCK_GROUP = new SomethingElementType("BLOCK_GROUP");
  IElementType FACTORY = new SomethingElementType("FACTORY");
  IElementType IDENTIFIER = new SomethingElementType("IDENTIFIER");
  IElementType LITERAL = new SomethingElementType("LITERAL");
  IElementType PROPERTY = new SomethingElementType("PROPERTY");
  IElementType QUALIFIED = new SomethingElementType("QUALIFIED");

  IElementType BLOCK_COMMENT_CONTENTS = new SomethingTokenType("BLOCK_COMMENT_CONTENTS");
  IElementType BLOCK_COMMENT_END = new SomethingTokenType("*/");
  IElementType BLOCK_COMMENT_START = new SomethingTokenType("/*");
  IElementType BOOLEAN = new SomethingTokenType("boolean");
  IElementType LINE_COMMENT = new SomethingTokenType("line_comment");
  IElementType NULL = new SomethingTokenType("null");
  IElementType NUMBER = new SomethingTokenType("number");
  IElementType OP_COMMA = new SomethingTokenType(",");
  IElementType OP_DOT = new SomethingTokenType(".");
  IElementType OP_EQ = new SomethingTokenType("=");
  IElementType OP_LBRACE = new SomethingTokenType("{");
  IElementType OP_LPAREN = new SomethingTokenType("(");
  IElementType OP_RBRACE = new SomethingTokenType("}");
  IElementType OP_RPAREN = new SomethingTokenType(")");
  IElementType STRING = new SomethingTokenType("string");
  IElementType TOKEN = new SomethingTokenType("token");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == ARGUMENTS_LIST) {
        return new SomethingArgumentsListImpl(node);
      }
      else if (type == ASSIGNMENT) {
        return new SomethingAssignmentImpl(node);
      }
      else if (type == BARE) {
        return new SomethingBareImpl(node);
      }
      else if (type == BLOCK) {
        return new SomethingBlockImpl(node);
      }
      else if (type == BLOCK_GROUP) {
        return new SomethingBlockGroupImpl(node);
      }
      else if (type == FACTORY) {
        return new SomethingFactoryImpl(node);
      }
      else if (type == IDENTIFIER) {
        return new SomethingIdentifierImpl(node);
      }
      else if (type == LITERAL) {
        return new SomethingLiteralImpl(node);
      }
      else if (type == QUALIFIED) {
        return new SomethingQualifiedImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
