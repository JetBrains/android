/*
 * Copyright (C) 2017 The Android Open Source Project
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

// ATTENTION: This file has been automatically generated from db.bnf. Do not edit it manually.

package com.android.tools.idea.lang.databinding.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.android.tools.idea.lang.databinding.psi.impl.*;

public interface DbTokenTypes {

  IElementType ADD_EXPR = new DbElementType("ADD_EXPR");
  IElementType BINARY_AND_EXPR = new DbElementType("BINARY_AND_EXPR");
  IElementType BINARY_OR_EXPR = new DbElementType("BINARY_OR_EXPR");
  IElementType BINARY_XOR_EXPR = new DbElementType("BINARY_XOR_EXPR");
  IElementType BIT_SHIFT_EXPR = new DbElementType("BIT_SHIFT_EXPR");
  IElementType BRACKET_EXPR = new DbElementType("BRACKET_EXPR");
  IElementType CALL_EXPR = new DbElementType("CALL_EXPR");
  IElementType CAST_EXPR = new DbElementType("CAST_EXPR");
  IElementType CLASS_EXTRACTION_EXPR = new DbElementType("CLASS_EXTRACTION_EXPR");
  IElementType CLASS_OR_INTERFACE_TYPE = new DbElementType("CLASS_OR_INTERFACE_TYPE");
  IElementType CONSTANT_VALUE = new DbElementType("CONSTANT_VALUE");
  IElementType DEFAULTS = new DbElementType("DEFAULTS");
  IElementType EQ_COMPARISON_EXPR = new DbElementType("EQ_COMPARISON_EXPR");
  IElementType EXPR = new DbElementType("EXPR");
  IElementType EXPRESSION_LIST = new DbElementType("EXPRESSION_LIST");
  IElementType FUNCTION_REF_EXPR = new DbElementType("FUNCTION_REF_EXPR");
  IElementType ID = new DbElementType("ID");
  IElementType INEQ_COMPARISON_EXPR = new DbElementType("INEQ_COMPARISON_EXPR");
  IElementType INFERRED_FORMAL_PARAMETER_LIST = new DbElementType("INFERRED_FORMAL_PARAMETER_LIST");
  IElementType INSTANCE_OF_EXPR = new DbElementType("INSTANCE_OF_EXPR");
  IElementType LAMBDA_EXPRESSION = new DbElementType("LAMBDA_EXPRESSION");
  IElementType LAMBDA_PARAMETERS = new DbElementType("LAMBDA_PARAMETERS");
  IElementType LITERAL_EXPR = new DbElementType("LITERAL_EXPR");
  IElementType LOGICAL_AND_EXPR = new DbElementType("LOGICAL_AND_EXPR");
  IElementType LOGICAL_OR_EXPR = new DbElementType("LOGICAL_OR_EXPR");
  IElementType MUL_EXPR = new DbElementType("MUL_EXPR");
  IElementType NEGATION_EXPR = new DbElementType("NEGATION_EXPR");
  IElementType NULL_COALESCE_EXPR = new DbElementType("NULL_COALESCE_EXPR");
  IElementType PAREN_EXPR = new DbElementType("PAREN_EXPR");
  IElementType PRIMITIVE_TYPE = new DbElementType("PRIMITIVE_TYPE");
  IElementType REF_EXPR = new DbElementType("REF_EXPR");
  IElementType RESOURCES_EXPR = new DbElementType("RESOURCES_EXPR");
  IElementType RESOURCE_PARAMETERS = new DbElementType("RESOURCE_PARAMETERS");
  IElementType SIGN_CHANGE_EXPR = new DbElementType("SIGN_CHANGE_EXPR");
  IElementType TERNARY_EXPR = new DbElementType("TERNARY_EXPR");
  IElementType TYPE = new DbElementType("TYPE");
  IElementType TYPE_ARGUMENTS = new DbElementType("TYPE_ARGUMENTS");
  IElementType VOID_EXPR = new DbElementType("VOID_EXPR");

  IElementType AND = new DbTokenType("&");
  IElementType ANDAND = new DbTokenType("&&");
  IElementType ASTERISK = new DbTokenType("*");
  IElementType BOOLEAN_KEYWORD = new DbTokenType("boolean");
  IElementType BYTE_KEYWORD = new DbTokenType("firstPackageId");
  IElementType CHARACTER_LITERAL = new DbTokenType("CHARACTER_LITERAL");
  IElementType CHAR_KEYWORD = new DbTokenType("char");
  IElementType CLASS_KEYWORD = new DbTokenType("class");
  IElementType COLON = new DbTokenType(":");
  IElementType COLONCOLON = new DbTokenType("::");
  IElementType COMMA = new DbTokenType(",");
  IElementType DEFAULT_KEYWORD = new DbTokenType("default");
  IElementType DIV = new DbTokenType("/");
  IElementType DOT = new DbTokenType(".");
  IElementType DOUBLE_KEYWORD = new DbTokenType("double");
  IElementType DOUBLE_LITERAL = new DbTokenType("DOUBLE_LITERAL");
  IElementType EQ = new DbTokenType("=");
  IElementType EQEQ = new DbTokenType("==");
  IElementType EXCL = new DbTokenType("!");
  IElementType FALSE = new DbTokenType("false");
  IElementType FLOAT_KEYWORD = new DbTokenType("float");
  IElementType FLOAT_LITERAL = new DbTokenType("FLOAT_LITERAL");
  IElementType GT = new DbTokenType(">");
  IElementType GTEQ = new DbTokenType(">=");
  IElementType GTGT = new DbTokenType(">>");
  IElementType GTGTGT = new DbTokenType(">>>");
  IElementType IDENTIFIER = new DbTokenType("IDENTIFIER");
  IElementType INSTANCEOF_KEYWORD = new DbTokenType("instanceof");
  IElementType INTEGER_LITERAL = new DbTokenType("INTEGER_LITERAL");
  IElementType INT_KEYWORD = new DbTokenType("int");
  IElementType LAMBDA = new DbTokenType("->");
  IElementType LBRACKET = new DbTokenType("[");
  IElementType LE = new DbTokenType("<=");
  IElementType LONG_KEYWORD = new DbTokenType("long");
  IElementType LONG_LITERAL = new DbTokenType("LONG_LITERAL");
  IElementType LPARENTH = new DbTokenType("(");
  IElementType LT = new DbTokenType("<");
  IElementType LTLT = new DbTokenType("<<");
  IElementType MINUS = new DbTokenType("-");
  IElementType NE = new DbTokenType("!=");
  IElementType NULL = new DbTokenType("null");
  IElementType OR = new DbTokenType("|");
  IElementType OROR = new DbTokenType("||");
  IElementType PERC = new DbTokenType("%");
  IElementType PLUS = new DbTokenType("+");
  IElementType QUEST = new DbTokenType("?");
  IElementType QUESTQUEST = new DbTokenType("??");
  IElementType RBRACKET = new DbTokenType("]");
  IElementType RESOURCE_REFERENCE = new DbTokenType("RESOURCE_REFERENCE");
  IElementType RPARENTH = new DbTokenType(")");
  IElementType SHORT_KEYWORD = new DbTokenType("short");
  IElementType STRING_LITERAL = new DbTokenType("STRING_LITERAL");
  IElementType TILDE = new DbTokenType("~");
  IElementType TRUE = new DbTokenType("true");
  IElementType VOID_KEYWORD = new DbTokenType("void");
  IElementType XOR = new DbTokenType("^");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
       if (type == ADD_EXPR) {
        return new PsiDbAddExprImpl(node);
      }
      else if (type == BINARY_AND_EXPR) {
        return new PsiDbBinaryAndExprImpl(node);
      }
      else if (type == BINARY_OR_EXPR) {
        return new PsiDbBinaryOrExprImpl(node);
      }
      else if (type == BINARY_XOR_EXPR) {
        return new PsiDbBinaryXorExprImpl(node);
      }
      else if (type == BIT_SHIFT_EXPR) {
        return new PsiDbBitShiftExprImpl(node);
      }
      else if (type == BRACKET_EXPR) {
        return new PsiDbBracketExprImpl(node);
      }
      else if (type == CALL_EXPR) {
        return new PsiDbCallExprImpl(node);
      }
      else if (type == CAST_EXPR) {
        return new PsiDbCastExprImpl(node);
      }
      else if (type == CLASS_EXTRACTION_EXPR) {
        return new PsiDbClassExtractionExprImpl(node);
      }
      else if (type == CLASS_OR_INTERFACE_TYPE) {
        return new PsiDbClassOrInterfaceTypeImpl(node);
      }
      else if (type == CONSTANT_VALUE) {
        return new PsiDbConstantValueImpl(node);
      }
      else if (type == DEFAULTS) {
        return new PsiDbDefaultsImpl(node);
      }
      else if (type == EQ_COMPARISON_EXPR) {
        return new PsiDbEqComparisonExprImpl(node);
      }
      else if (type == EXPRESSION_LIST) {
        return new PsiDbExpressionListImpl(node);
      }
      else if (type == FUNCTION_REF_EXPR) {
        return new PsiDbFunctionRefExprImpl(node);
      }
      else if (type == ID) {
        return new PsiDbIdImpl(node);
      }
      else if (type == INEQ_COMPARISON_EXPR) {
        return new PsiDbIneqComparisonExprImpl(node);
      }
      else if (type == INFERRED_FORMAL_PARAMETER_LIST) {
        return new PsiDbInferredFormalParameterListImpl(node);
      }
      else if (type == INSTANCE_OF_EXPR) {
        return new PsiDbInstanceOfExprImpl(node);
      }
      else if (type == LAMBDA_EXPRESSION) {
        return new PsiDbLambdaExpressionImpl(node);
      }
      else if (type == LAMBDA_PARAMETERS) {
        return new PsiDbLambdaParametersImpl(node);
      }
      else if (type == LITERAL_EXPR) {
        return new PsiDbLiteralExprImpl(node);
      }
      else if (type == LOGICAL_AND_EXPR) {
        return new PsiDbLogicalAndExprImpl(node);
      }
      else if (type == LOGICAL_OR_EXPR) {
        return new PsiDbLogicalOrExprImpl(node);
      }
      else if (type == MUL_EXPR) {
        return new PsiDbMulExprImpl(node);
      }
      else if (type == NEGATION_EXPR) {
        return new PsiDbNegationExprImpl(node);
      }
      else if (type == NULL_COALESCE_EXPR) {
        return new PsiDbNullCoalesceExprImpl(node);
      }
      else if (type == PAREN_EXPR) {
        return new PsiDbParenExprImpl(node);
      }
      else if (type == PRIMITIVE_TYPE) {
        return new PsiDbPrimitiveTypeImpl(node);
      }
      else if (type == REF_EXPR) {
        return new PsiDbRefExprImpl(node);
      }
      else if (type == RESOURCES_EXPR) {
        return new PsiDbResourcesExprImpl(node);
      }
      else if (type == RESOURCE_PARAMETERS) {
        return new PsiDbResourceParametersImpl(node);
      }
      else if (type == SIGN_CHANGE_EXPR) {
        return new PsiDbSignChangeExprImpl(node);
      }
      else if (type == TERNARY_EXPR) {
        return new PsiDbTernaryExprImpl(node);
      }
      else if (type == TYPE) {
        return new PsiDbTypeImpl(node);
      }
      else if (type == TYPE_ARGUMENTS) {
        return new PsiDbTypeArgumentsImpl(node);
      }
      else if (type == VOID_EXPR) {
        return new PsiDbVoidExprImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
