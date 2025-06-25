/*
 * Copyright (C) 2025 The Android Open Source Project
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

// ATTENTION: This file has been automatically generated from
// wear-dwf/src/com/android/tools/idea/wear/dwf/dom/raw/expressions/wff_expressions.bnf
// Do not edit it manually.
package com.android.tools.idea.wear.dwf.dom.raw.expressions;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.android.tools.idea.wear.dwf.dom.raw.expressions.impl.*;

public interface WFFExpressionTypes {

  IElementType AND_EXPR = new WFFExpressionElementType("AND_EXPR");
  IElementType ARG_LIST = new WFFExpressionElementType("ARG_LIST");
  IElementType BIT_COMPL_EXPR = new WFFExpressionElementType("BIT_COMPL_EXPR");
  IElementType CALL_EXPR = new WFFExpressionElementType("CALL_EXPR");
  IElementType CONDITIONAL_EXPR = new WFFExpressionElementType("CONDITIONAL_EXPR");
  IElementType CONDITIONAL_OP = new WFFExpressionElementType("CONDITIONAL_OP");
  IElementType CONFIGURATION = new WFFExpressionElementType("CONFIGURATION");
  IElementType CONFIGURATION_ID = new WFFExpressionElementType("CONFIGURATION_ID");
  IElementType DATA_SOURCE = new WFFExpressionElementType("DATA_SOURCE");
  IElementType DIV_EXPR = new WFFExpressionElementType("DIV_EXPR");
  IElementType ELVIS_EXPR = new WFFExpressionElementType("ELVIS_EXPR");
  IElementType EXPR = new WFFExpressionElementType("EXPR");
  IElementType FUNCTION_ID = new WFFExpressionElementType("FUNCTION_ID");
  IElementType LITERAL_EXPR = new WFFExpressionElementType("LITERAL_EXPR");
  IElementType MINUS_EXPR = new WFFExpressionElementType("MINUS_EXPR");
  IElementType MOD_EXPR = new WFFExpressionElementType("MOD_EXPR");
  IElementType MUL_EXPR = new WFFExpressionElementType("MUL_EXPR");
  IElementType OR_EXPR = new WFFExpressionElementType("OR_EXPR");
  IElementType PAREN_EXPR = new WFFExpressionElementType("PAREN_EXPR");
  IElementType PLUS_EXPR = new WFFExpressionElementType("PLUS_EXPR");
  IElementType UNARY_MIN_EXPR = new WFFExpressionElementType("UNARY_MIN_EXPR");
  IElementType UNARY_NOT_EXPR = new WFFExpressionElementType("UNARY_NOT_EXPR");
  IElementType UNARY_PLUS_EXPR = new WFFExpressionElementType("UNARY_PLUS_EXPR");

  IElementType CLOSE_BRACKET = new WFFExpressionTokenType("]");
  IElementType CLOSE_PAREN = new WFFExpressionTokenType(")");
  IElementType COMMA = new WFFExpressionTokenType(",");
  IElementType DOT = new WFFExpressionTokenType(".");
  IElementType ID = new WFFExpressionTokenType("ID");
  IElementType NULL = new WFFExpressionTokenType("null");
  IElementType NUMBER = new WFFExpressionTokenType("NUMBER");
  IElementType OPEN_BRACKET = new WFFExpressionTokenType("[");
  IElementType OPEN_PAREN = new WFFExpressionTokenType("(");
  IElementType OPERATORS = new WFFExpressionTokenType("OPERATORS");
  IElementType STRING = new WFFExpressionTokenType("STRING");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == AND_EXPR) {
        return new WFFExpressionAndExprImpl(node);
      }
      else if (type == ARG_LIST) {
        return new WFFExpressionArgListImpl(node);
      }
      else if (type == BIT_COMPL_EXPR) {
        return new WFFExpressionBitComplExprImpl(node);
      }
      else if (type == CALL_EXPR) {
        return new WFFExpressionCallExprImpl(node);
      }
      else if (type == CONDITIONAL_EXPR) {
        return new WFFExpressionConditionalExprImpl(node);
      }
      else if (type == CONDITIONAL_OP) {
        return new WFFExpressionConditionalOpImpl(node);
      }
      else if (type == CONFIGURATION) {
        return new WFFExpressionConfigurationImpl(node);
      }
      else if (type == CONFIGURATION_ID) {
        return new WFFExpressionConfigurationIdImpl(node);
      }
      else if (type == DATA_SOURCE) {
        return new WFFExpressionDataSourceImpl(node);
      }
      else if (type == DIV_EXPR) {
        return new WFFExpressionDivExprImpl(node);
      }
      else if (type == ELVIS_EXPR) {
        return new WFFExpressionElvisExprImpl(node);
      }
      else if (type == FUNCTION_ID) {
        return new WFFExpressionFunctionIdImpl(node);
      }
      else if (type == LITERAL_EXPR) {
        return new WFFExpressionLiteralExprImpl(node);
      }
      else if (type == MINUS_EXPR) {
        return new WFFExpressionMinusExprImpl(node);
      }
      else if (type == MOD_EXPR) {
        return new WFFExpressionModExprImpl(node);
      }
      else if (type == MUL_EXPR) {
        return new WFFExpressionMulExprImpl(node);
      }
      else if (type == OR_EXPR) {
        return new WFFExpressionOrExprImpl(node);
      }
      else if (type == PAREN_EXPR) {
        return new WFFExpressionParenExprImpl(node);
      }
      else if (type == PLUS_EXPR) {
        return new WFFExpressionPlusExprImpl(node);
      }
      else if (type == UNARY_MIN_EXPR) {
        return new WFFExpressionUnaryMinExprImpl(node);
      }
      else if (type == UNARY_NOT_EXPR) {
        return new WFFExpressionUnaryNotExprImpl(node);
      }
      else if (type == UNARY_PLUS_EXPR) {
        return new WFFExpressionUnaryPlusExprImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
