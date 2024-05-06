/*
 * Copyright (C) 2019 The Android Open Source Project
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

// ATTENTION: This file has been automatically generated from proguardR8.bnf. Do not edit it manually.

package com.android.tools.idea.lang.proguardR8.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.android.tools.idea.lang.proguardR8.psi.impl.*;

public interface ProguardR8PsiTypes {

  IElementType ANNOTATION_NAME = new ProguardR8AstNodeType("ANNOTATION_NAME");
  IElementType ANY_FIELD_OR_METHOD = new ProguardR8AstNodeType("ANY_FIELD_OR_METHOD");
  IElementType ANY_NOT_PRIMITIVE_TYPE = new ProguardR8AstNodeType("ANY_NOT_PRIMITIVE_TYPE");
  IElementType ANY_PRIMITIVE_TYPE = new ProguardR8AstNodeType("ANY_PRIMITIVE_TYPE");
  IElementType ANY_TYPE = new ProguardR8AstNodeType("ANY_TYPE");
  IElementType ARRAY_TYPE = new ProguardR8AstNodeType("ARRAY_TYPE");
  IElementType CLASS_MEMBER_NAME = new ProguardR8AstNodeType("CLASS_MEMBER_NAME");
  IElementType CLASS_MODIFIER = new ProguardR8AstNodeType("CLASS_MODIFIER");
  IElementType CLASS_NAME = new ProguardR8AstNodeType("CLASS_NAME");
  IElementType CLASS_SPECIFICATION_BODY = new ProguardR8AstNodeType("CLASS_SPECIFICATION_BODY");
  IElementType CLASS_SPECIFICATION_HEADER = new ProguardR8AstNodeType("CLASS_SPECIFICATION_HEADER");
  IElementType CLASS_TYPE = new ProguardR8AstNodeType("CLASS_TYPE");
  IElementType CONSTRUCTOR_NAME = new ProguardR8AstNodeType("CONSTRUCTOR_NAME");
  IElementType FIELD = new ProguardR8AstNodeType("FIELD");
  IElementType FIELDS_SPECIFICATION = new ProguardR8AstNodeType("FIELDS_SPECIFICATION");
  IElementType FILE = new ProguardR8AstNodeType("FILE");
  IElementType FILE_FILTER = new ProguardR8AstNodeType("FILE_FILTER");
  IElementType FLAG = new ProguardR8AstNodeType("FLAG");
  IElementType FLAG_ARGUMENT = new ProguardR8AstNodeType("FLAG_ARGUMENT");
  IElementType FULLY_QUALIFIED_NAME_CONSTRUCTOR = new ProguardR8AstNodeType("FULLY_QUALIFIED_NAME_CONSTRUCTOR");
  IElementType INCLUDE_FILE = new ProguardR8AstNodeType("INCLUDE_FILE");
  IElementType JAVA_PRIMITIVE = new ProguardR8AstNodeType("JAVA_PRIMITIVE");
  IElementType JAVA_RULE = new ProguardR8AstNodeType("JAVA_RULE");
  IElementType KEEP_OPTION_MODIFIER = new ProguardR8AstNodeType("KEEP_OPTION_MODIFIER");
  IElementType METHOD = new ProguardR8AstNodeType("METHOD");
  IElementType METHOD_SPECIFICATION = new ProguardR8AstNodeType("METHOD_SPECIFICATION");
  IElementType MODIFIER = new ProguardR8AstNodeType("MODIFIER");
  IElementType PARAMETERS = new ProguardR8AstNodeType("PARAMETERS");
  IElementType QUALIFIED_NAME = new ProguardR8AstNodeType("QUALIFIED_NAME");
  IElementType RULE = new ProguardR8AstNodeType("RULE");
  IElementType RULE_WITH_CLASS_FILTER = new ProguardR8AstNodeType("RULE_WITH_CLASS_FILTER");
  IElementType RULE_WITH_CLASS_SPECIFICATION = new ProguardR8AstNodeType("RULE_WITH_CLASS_SPECIFICATION");
  IElementType SUPER_CLASS_NAME = new ProguardR8AstNodeType("SUPER_CLASS_NAME");
  IElementType TYPE = new ProguardR8AstNodeType("TYPE");
  IElementType TYPE_LIST = new ProguardR8AstNodeType("TYPE_LIST");

  IElementType ABSTRACT = new ProguardR8TokenType("abstract");
  IElementType ALLOWOBFUSCATION = new ProguardR8TokenType("allowobfuscation");
  IElementType ALLOWOPTIMIZATION = new ProguardR8TokenType("allowoptimization");
  IElementType ALLOWSHRINKING = new ProguardR8TokenType("allowshrinking");
  IElementType ANY_PRIMITIVE_TYPE_ = new ProguardR8TokenType("%");
  IElementType ANY_TYPE_ = new ProguardR8TokenType("***");
  IElementType ANY_TYPE_AND_NUM_OF_ARGS = new ProguardR8TokenType("...");
  IElementType ASTERISK = new ProguardR8TokenType("*");
  IElementType AT = new ProguardR8TokenType("@");
  IElementType AT_INTERFACE = new ProguardR8TokenType("AT_INTERFACE");
  IElementType BOOLEAN = new ProguardR8TokenType("boolean");
  IElementType BYTE = new ProguardR8TokenType("byte");
  IElementType CHAR = new ProguardR8TokenType("char");
  IElementType CLASS = new ProguardR8TokenType("class");
  IElementType CLOSE_BRACE = new ProguardR8TokenType("}");
  IElementType CLOSE_BRACKET = new ProguardR8TokenType("]");
  IElementType COLON = new ProguardR8TokenType(":");
  IElementType COMMA = new ProguardR8TokenType(",");
  IElementType DOT = new ProguardR8TokenType(".");
  IElementType DOUBLE = new ProguardR8TokenType("double");
  IElementType DOUBLE_ASTERISK = new ProguardR8TokenType("**");
  IElementType DOUBLE_QUOTED_CLASS = new ProguardR8TokenType("DOUBLE_QUOTED_CLASS");
  IElementType DOUBLE_QUOTED_STRING = new ProguardR8TokenType("DOUBLE_QUOTED_STRING");
  IElementType EM = new ProguardR8TokenType("!");
  IElementType ENUM = new ProguardR8TokenType("enum");
  IElementType EXTENDS = new ProguardR8TokenType("extends");
  IElementType FILE_NAME = new ProguardR8TokenType("FILE_NAME");
  IElementType FINAL = new ProguardR8TokenType("final");
  IElementType FLAG_TOKEN = new ProguardR8TokenType("FLAG_TOKEN");
  IElementType FLOAT = new ProguardR8TokenType("float");
  IElementType IMPLEMENTS = new ProguardR8TokenType("implements");
  IElementType INCLUDECODE = new ProguardR8TokenType("includecode");
  IElementType INCLUDEDESCRIPTORCLASSES = new ProguardR8TokenType("includedescriptorclasses");
  IElementType INT = new ProguardR8TokenType("int");
  IElementType INTERFACE = new ProguardR8TokenType("interface");
  IElementType JAVA_IDENTIFIER = new ProguardR8TokenType("JAVA_IDENTIFIER");
  IElementType JAVA_IDENTIFIER_WITH_WILDCARDS = new ProguardR8TokenType("JAVA_IDENTIFIER_WITH_WILDCARDS");
  IElementType LINE_CMT = new ProguardR8TokenType("LINE_CMT");
  IElementType LONG = new ProguardR8TokenType("long");
  IElementType LPAREN = new ProguardR8TokenType("(");
  IElementType NATIVE = new ProguardR8TokenType("native");
  IElementType OPEN_BRACE = new ProguardR8TokenType("{");
  IElementType OPEN_BRACKET = new ProguardR8TokenType("[");
  IElementType PRIVATE = new ProguardR8TokenType("private");
  IElementType PROTECTED = new ProguardR8TokenType("protected");
  IElementType PUBLIC = new ProguardR8TokenType("public");
  IElementType RETURN = new ProguardR8TokenType("return");
  IElementType RPAREN = new ProguardR8TokenType(")");
  IElementType SEMICOLON = new ProguardR8TokenType(";");
  IElementType SHORT = new ProguardR8TokenType("short");
  IElementType SINGLE_QUOTED_CLASS = new ProguardR8TokenType("SINGLE_QUOTED_CLASS");
  IElementType SINGLE_QUOTED_STRING = new ProguardR8TokenType("SINGLE_QUOTED_STRING");
  IElementType STATIC = new ProguardR8TokenType("static");
  IElementType STRICTFP = new ProguardR8TokenType("strictfp");
  IElementType SYNCHRONIZED = new ProguardR8TokenType("synchronized");
  IElementType SYNTHETIC = new ProguardR8TokenType("synthetic");
  IElementType TRANSIENT = new ProguardR8TokenType("transient");
  IElementType UNTERMINATED_DOUBLE_QUOTED_CLASS = new ProguardR8TokenType("UNTERMINATED_DOUBLE_QUOTED_CLASS");
  IElementType UNTERMINATED_DOUBLE_QUOTED_STRING = new ProguardR8TokenType("UNTERMINATED_DOUBLE_QUOTED_STRING");
  IElementType UNTERMINATED_SINGLE_QUOTED_CLASS = new ProguardR8TokenType("UNTERMINATED_SINGLE_QUOTED_CLASS");
  IElementType UNTERMINATED_SINGLE_QUOTED_STRING = new ProguardR8TokenType("UNTERMINATED_SINGLE_QUOTED_STRING");
  IElementType VALUES = new ProguardR8TokenType("values");
  IElementType VOID = new ProguardR8TokenType("void");
  IElementType VOLATILE = new ProguardR8TokenType("volatile");
  IElementType WHITE_SPACE = new ProguardR8TokenType("WHITE_SPACE");
  IElementType _CLINIT_ = new ProguardR8TokenType("<clinit>");
  IElementType _FIELDS_ = new ProguardR8TokenType("<fields>");
  IElementType _INIT_ = new ProguardR8TokenType("<init>");
  IElementType _METHODS_ = new ProguardR8TokenType("<methods>");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == ANNOTATION_NAME) {
        return new ProguardR8AnnotationNameImpl(node);
      }
      else if (type == ANY_FIELD_OR_METHOD) {
        return new ProguardR8AnyFieldOrMethodImpl(node);
      }
      else if (type == ANY_NOT_PRIMITIVE_TYPE) {
        return new ProguardR8AnyNotPrimitiveTypeImpl(node);
      }
      else if (type == ANY_PRIMITIVE_TYPE) {
        return new ProguardR8AnyPrimitiveTypeImpl(node);
      }
      else if (type == ANY_TYPE) {
        return new ProguardR8AnyTypeImpl(node);
      }
      else if (type == ARRAY_TYPE) {
        return new ProguardR8ArrayTypeImpl(node);
      }
      else if (type == CLASS_MEMBER_NAME) {
        return new ProguardR8ClassMemberNameImpl(node);
      }
      else if (type == CLASS_MODIFIER) {
        return new ProguardR8ClassModifierImpl(node);
      }
      else if (type == CLASS_NAME) {
        return new ProguardR8ClassNameImpl(node);
      }
      else if (type == CLASS_SPECIFICATION_BODY) {
        return new ProguardR8ClassSpecificationBodyImpl(node);
      }
      else if (type == CLASS_SPECIFICATION_HEADER) {
        return new ProguardR8ClassSpecificationHeaderImpl(node);
      }
      else if (type == CLASS_TYPE) {
        return new ProguardR8ClassTypeImpl(node);
      }
      else if (type == CONSTRUCTOR_NAME) {
        return new ProguardR8ConstructorNameImpl(node);
      }
      else if (type == FIELD) {
        return new ProguardR8FieldImpl(node);
      }
      else if (type == FIELDS_SPECIFICATION) {
        return new ProguardR8FieldsSpecificationImpl(node);
      }
      else if (type == FILE) {
        return new ProguardR8FileImpl(node);
      }
      else if (type == FILE_FILTER) {
        return new ProguardR8FileFilterImpl(node);
      }
      else if (type == FLAG) {
        return new ProguardR8FlagImpl(node);
      }
      else if (type == FLAG_ARGUMENT) {
        return new ProguardR8FlagArgumentImpl(node);
      }
      else if (type == FULLY_QUALIFIED_NAME_CONSTRUCTOR) {
        return new ProguardR8FullyQualifiedNameConstructorImpl(node);
      }
      else if (type == INCLUDE_FILE) {
        return new ProguardR8IncludeFileImpl(node);
      }
      else if (type == JAVA_PRIMITIVE) {
        return new ProguardR8JavaPrimitiveImpl(node);
      }
      else if (type == JAVA_RULE) {
        return new ProguardR8JavaRuleImpl(node);
      }
      else if (type == KEEP_OPTION_MODIFIER) {
        return new ProguardR8KeepOptionModifierImpl(node);
      }
      else if (type == METHOD) {
        return new ProguardR8MethodImpl(node);
      }
      else if (type == METHOD_SPECIFICATION) {
        return new ProguardR8MethodSpecificationImpl(node);
      }
      else if (type == MODIFIER) {
        return new ProguardR8ModifierImpl(node);
      }
      else if (type == PARAMETERS) {
        return new ProguardR8ParametersImpl(node);
      }
      else if (type == QUALIFIED_NAME) {
        return new ProguardR8QualifiedNameImpl(node);
      }
      else if (type == RULE) {
        return new ProguardR8RuleImpl(node);
      }
      else if (type == RULE_WITH_CLASS_FILTER) {
        return new ProguardR8RuleWithClassFilterImpl(node);
      }
      else if (type == RULE_WITH_CLASS_SPECIFICATION) {
        return new ProguardR8RuleWithClassSpecificationImpl(node);
      }
      else if (type == SUPER_CLASS_NAME) {
        return new ProguardR8SuperClassNameImpl(node);
      }
      else if (type == TYPE) {
        return new ProguardR8TypeImpl(node);
      }
      else if (type == TYPE_LIST) {
        return new ProguardR8TypeListImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
