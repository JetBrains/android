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

// ATTENTION: This file has been automatically generated from Aidl.bnf. Do not edit it manually.

package com.android.tools.idea.lang.aidl.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.android.tools.idea.lang.aidl.psi.AidlElementType;
import com.android.tools.idea.lang.aidl.psi.impl.*;

public interface AidlTokenTypes {

  IElementType BODY = new AidlElementType("BODY");
  IElementType CLASS_OR_INTERFACE_TYPE = new AidlElementType("CLASS_OR_INTERFACE_TYPE");
  IElementType DECLARATION_NAME = new AidlElementType("DECLARATION_NAME");
  IElementType DIRECTION = new AidlElementType("DIRECTION");
  IElementType HEADERS = new AidlElementType("HEADERS");
  IElementType IMPORT_STATEMENT = new AidlElementType("IMPORT_STATEMENT");
  IElementType INTERFACE_DECLARATION = new AidlElementType("INTERFACE_DECLARATION");
  IElementType METHOD_DECLARATION = new AidlElementType("METHOD_DECLARATION");
  IElementType NAME_COMPONENT = new AidlElementType("NAME_COMPONENT");
  IElementType PACKAGE_STATEMENT = new AidlElementType("PACKAGE_STATEMENT");
  IElementType PARAMETER = new AidlElementType("PARAMETER");
  IElementType PARCELABLE_DECLARATION = new AidlElementType("PARCELABLE_DECLARATION");
  IElementType PRIMITIVE_TYPE = new AidlElementType("PRIMITIVE_TYPE");
  IElementType QUALIFIED_NAME = new AidlElementType("QUALIFIED_NAME");
  IElementType TYPE = new AidlElementType("TYPE");
  IElementType TYPE_ARGUMENTS = new AidlElementType("TYPE_ARGUMENTS");

  IElementType BLOCK_COMMENT = new AidlTokenType("BLOCK_COMMENT");
  IElementType BOOLEAN_KEYWORD = new AidlTokenType("boolean");
  IElementType BYTE_KEYWORD = new AidlTokenType("byte");
  IElementType CHAR_KEYWORD = new AidlTokenType("char");
  IElementType COMMA = new AidlTokenType(",");
  IElementType COMMENT = new AidlTokenType("COMMENT");
  IElementType DOUBLE_KEYWORD = new AidlTokenType("double");
  IElementType EQUALS = new AidlTokenType("=");
  IElementType FLATTENABLE_KEYWORD = new AidlTokenType("flattenable");
  IElementType FLOAT_KEYWORD = new AidlTokenType("float");
  IElementType GT = new AidlTokenType(">");
  IElementType IDENTIFIER = new AidlTokenType("IDENTIFIER");
  IElementType IDVALUE = new AidlTokenType("IDVALUE");
  IElementType IMPORT_KEYWORD = new AidlTokenType("import");
  IElementType INOUT_KEYWORD = new AidlTokenType("inout");
  IElementType INTERFACE_KEYWORD = new AidlTokenType("interface");
  IElementType INT_KEYWORD = new AidlTokenType("int");
  IElementType IN_KEYWORD = new AidlTokenType("in");
  IElementType LBRACKET = new AidlTokenType("[");
  IElementType LCURLY = new AidlTokenType("{");
  IElementType LONG_KEYWORD = new AidlTokenType("long");
  IElementType LPARENTH = new AidlTokenType("(");
  IElementType LT = new AidlTokenType("<");
  IElementType ONEWAY = new AidlTokenType("ONEWAY");
  IElementType ONEWAY_KEYWORD = new AidlTokenType("oneway");
  IElementType OUT_KEYWORD = new AidlTokenType("out");
  IElementType PACKAGE_KEYWORD = new AidlTokenType("package");
  IElementType PARCELABLE_KEYWORD = new AidlTokenType("parcelable");
  IElementType RBRACKET = new AidlTokenType("]");
  IElementType RCURLY = new AidlTokenType("}");
  IElementType RPARENTH = new AidlTokenType(")");
  IElementType RPC_KEYWORD = new AidlTokenType("rpc");
  IElementType SEMICOLON = new AidlTokenType(";");
  IElementType SHORT_KEYWORD = new AidlTokenType("short");
  IElementType VOID_KEYWORD = new AidlTokenType("void");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
       if (type == BODY) {
        return new AidlBodyImpl(node);
      }
      else if (type == CLASS_OR_INTERFACE_TYPE) {
        return new AidlClassOrInterfaceTypeImpl(node);
      }
      else if (type == DECLARATION_NAME) {
        return new AidlDeclarationNameImpl(node);
      }
      else if (type == DIRECTION) {
        return new AidlDirectionImpl(node);
      }
      else if (type == HEADERS) {
        return new AidlHeadersImpl(node);
      }
      else if (type == IMPORT_STATEMENT) {
        return new AidlImportStatementImpl(node);
      }
      else if (type == INTERFACE_DECLARATION) {
        return new AidlInterfaceDeclarationImpl(node);
      }
      else if (type == METHOD_DECLARATION) {
        return new AidlMethodDeclarationImpl(node);
      }
      else if (type == NAME_COMPONENT) {
        return new AidlNameComponentImpl(node);
      }
      else if (type == PACKAGE_STATEMENT) {
        return new AidlPackageStatementImpl(node);
      }
      else if (type == PARAMETER) {
        return new AidlParameterImpl(node);
      }
      else if (type == PARCELABLE_DECLARATION) {
        return new AidlParcelableDeclarationImpl(node);
      }
      else if (type == PRIMITIVE_TYPE) {
        return new AidlPrimitiveTypeImpl(node);
      }
      else if (type == QUALIFIED_NAME) {
        return new AidlQualifiedNameImpl(node);
      }
      else if (type == TYPE) {
        return new AidlTypeImpl(node);
      }
      else if (type == TYPE_ARGUMENTS) {
        return new AidlTypeArgumentsImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
