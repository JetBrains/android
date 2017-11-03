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

// Generated from Smali.bnf, do not modify
package com.android.tools.idea.smali.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.android.tools.idea.smali.psi.impl.*;

public interface SmaliTypes {

  IElementType ACCESS_MODIFIER = new SmaliElementType("ACCESS_MODIFIER");
  IElementType ANNOTATIONS_SPEC = new SmaliElementType("ANNOTATIONS_SPEC");
  IElementType ANNOTATION_PROPERTY = new SmaliElementType("ANNOTATION_PROPERTY");
  IElementType BOOL = new SmaliElementType("BOOL");
  IElementType CLASS_NAME = new SmaliElementType("CLASS_NAME");
  IElementType CLASS_SPEC = new SmaliElementType("CLASS_SPEC");
  IElementType FIELD_NAME = new SmaliElementType("FIELD_NAME");
  IElementType FIELD_SPEC = new SmaliElementType("FIELD_SPEC");
  IElementType FIELD_VALUE = new SmaliElementType("FIELD_VALUE");
  IElementType IMPLEMENTS_SPEC = new SmaliElementType("IMPLEMENTS_SPEC");
  IElementType METHOD_BODY = new SmaliElementType("METHOD_BODY");
  IElementType METHOD_SPEC = new SmaliElementType("METHOD_SPEC");
  IElementType METHOD_START = new SmaliElementType("METHOD_START");
  IElementType PARAMETER_DECLARATION = new SmaliElementType("PARAMETER_DECLARATION");
  IElementType PRIMITIVE_TYPE = new SmaliElementType("PRIMITIVE_TYPE");
  IElementType PROPERTY_VALUE = new SmaliElementType("PROPERTY_VALUE");
  IElementType REGULAR_METHOD_START = new SmaliElementType("REGULAR_METHOD_START");
  IElementType RETURN_TYPE = new SmaliElementType("RETURN_TYPE");
  IElementType SINGLE_VALUE = new SmaliElementType("SINGLE_VALUE");
  IElementType SINGLE_VALUES = new SmaliElementType("SINGLE_VALUES");
  IElementType SOURCE_SPEC = new SmaliElementType("SOURCE_SPEC");
  IElementType SUPER_SPEC = new SmaliElementType("SUPER_SPEC");
  IElementType VALUE_ARRAY = new SmaliElementType("VALUE_ARRAY");
  IElementType VOID_TYPE = new SmaliElementType("VOID_TYPE");

  IElementType AM_ABSTRACT = new SmaliTokenType("abstract");
  IElementType AM_BRIDGE = new SmaliTokenType("bridge");
  IElementType AM_FINAL = new SmaliTokenType("final");
  IElementType AM_INTERFACE = new SmaliTokenType("interface");
  IElementType AM_NATIVE = new SmaliTokenType("native");
  IElementType AM_PRIVATE = new SmaliTokenType("private");
  IElementType AM_PROTECTED = new SmaliTokenType("protected");
  IElementType AM_PUBLIC = new SmaliTokenType("public");
  IElementType AM_STATIC = new SmaliTokenType("static");
  IElementType AM_SYNCHRONIZED = new SmaliTokenType("synchronized");
  IElementType AM_SYNTHETIC = new SmaliTokenType("synthetic");
  IElementType AM_TRANSIENT = new SmaliTokenType("transient");
  IElementType AM_VOLATILE = new SmaliTokenType("volatile");
  IElementType CHAR = new SmaliTokenType("CHAR");
  IElementType COMMENT = new SmaliTokenType("COMMENT");
  IElementType CONSTRUCTOR_INIT = new SmaliTokenType("CONSTRUCTOR_INIT");
  IElementType DOT_ANNOTATION = new SmaliTokenType(".annotation");
  IElementType DOT_ANNOTATION_END = new SmaliTokenType(".end annotation");
  IElementType DOT_CLASS = new SmaliTokenType(".class");
  IElementType DOT_FIELD = new SmaliTokenType(".field");
  IElementType DOT_IMPLEMENTS = new SmaliTokenType(".implements");
  IElementType DOT_LINE = new SmaliTokenType(".line");
  IElementType DOT_METHOD = new SmaliTokenType(".method");
  IElementType DOT_METHOD_END = new SmaliTokenType(".end method");
  IElementType DOT_PARAM = new SmaliTokenType(".param");
  IElementType DOT_PROLOGUE = new SmaliTokenType(".prologue");
  IElementType DOT_REGISTERS = new SmaliTokenType(".registers");
  IElementType DOT_SOURCE = new SmaliTokenType(".source");
  IElementType DOT_SUPER = new SmaliTokenType(".super");
  IElementType DOUBLE_QUOTED_STRING = new SmaliTokenType("DOUBLE_QUOTED_STRING");
  IElementType FALSE = new SmaliTokenType("false");
  IElementType HEX_NUMBER = new SmaliTokenType("HEX_NUMBER");
  IElementType IDENTIFIER = new SmaliTokenType("IDENTIFIER");
  IElementType JAVA_IDENTIFIER = new SmaliTokenType("JAVA_IDENTIFIER");
  IElementType L_CURLY = new SmaliTokenType("{");
  IElementType L_PARENTHESIS = new SmaliTokenType("(");
  IElementType REGULAR_NUMBER = new SmaliTokenType("REGULAR_NUMBER");
  IElementType R_CURLY = new SmaliTokenType("}");
  IElementType R_PARENTHESIS = new SmaliTokenType(")");
  IElementType TRUE = new SmaliTokenType("true");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
       if (type == ACCESS_MODIFIER) {
        return new SmaliAccessModifierImpl(node);
      }
      else if (type == ANNOTATIONS_SPEC) {
        return new SmaliAnnotationsSpecImpl(node);
      }
      else if (type == ANNOTATION_PROPERTY) {
        return new SmaliAnnotationPropertyImpl(node);
      }
      else if (type == BOOL) {
        return new SmaliBoolImpl(node);
      }
      else if (type == CLASS_NAME) {
        return new SmaliClassNameImpl(node);
      }
      else if (type == CLASS_SPEC) {
        return new SmaliClassSpecImpl(node);
      }
      else if (type == FIELD_NAME) {
        return new SmaliFieldNameImpl(node);
      }
      else if (type == FIELD_SPEC) {
        return new SmaliFieldSpecImpl(node);
      }
      else if (type == FIELD_VALUE) {
        return new SmaliFieldValueImpl(node);
      }
      else if (type == IMPLEMENTS_SPEC) {
        return new SmaliImplementsSpecImpl(node);
      }
      else if (type == METHOD_BODY) {
        return new SmaliMethodBodyImpl(node);
      }
      else if (type == METHOD_SPEC) {
        return new SmaliMethodSpecImpl(node);
      }
      else if (type == METHOD_START) {
        return new SmaliMethodStartImpl(node);
      }
      else if (type == PARAMETER_DECLARATION) {
        return new SmaliParameterDeclarationImpl(node);
      }
      else if (type == PRIMITIVE_TYPE) {
        return new SmaliPrimitiveTypeImpl(node);
      }
      else if (type == PROPERTY_VALUE) {
        return new SmaliPropertyValueImpl(node);
      }
      else if (type == REGULAR_METHOD_START) {
        return new SmaliRegularMethodStartImpl(node);
      }
      else if (type == RETURN_TYPE) {
        return new SmaliReturnTypeImpl(node);
      }
      else if (type == SINGLE_VALUE) {
        return new SmaliSingleValueImpl(node);
      }
      else if (type == SINGLE_VALUES) {
        return new SmaliSingleValuesImpl(node);
      }
      else if (type == SOURCE_SPEC) {
        return new SmaliSourceSpecImpl(node);
      }
      else if (type == SUPER_SPEC) {
        return new SmaliSuperSpecImpl(node);
      }
      else if (type == VALUE_ARRAY) {
        return new SmaliValueArrayImpl(node);
      }
      else if (type == VOID_TYPE) {
        return new SmaliVoidTypeImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
