/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.lang.buildfile.validation;

import static com.google.idea.blaze.base.lang.buildfile.validation.AttributeTypeGroups.DICT_TYPES;
import static com.google.idea.blaze.base.lang.buildfile.validation.AttributeTypeGroups.INTEGER_TYPES;
import static com.google.idea.blaze.base.lang.buildfile.validation.AttributeTypeGroups.LIST_TYPES;
import static com.google.idea.blaze.base.lang.buildfile.validation.AttributeTypeGroups.STRING_TYPES;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.query2.proto.proto2api.Build;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Attribute.Discriminator;
import com.google.idea.blaze.base.lang.buildfile.psi.DictionaryLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.FunctionStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.GlobExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.IntegerLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.ListLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.LiteralExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.LoadStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.intellij.psi.PsiElement;
import java.util.EnumSet;

/**
 * Provides simple validation of BUILD psi element types (e.g. is the type known to not resolve to a
 * string).<br>
 * We err on the side of avoiding spurious errors.
 */
class BuildElementValidation {

  // This enum list is duplicated several times through Bazel source code. In some places there are
  // additional items not covered here. Don't show spurious errors when more items are added.
  private static final EnumSet<Build.Attribute.Discriminator> HANDLED_TYPES =
      EnumSet.copyOf(
          ImmutableList.<Discriminator>builder()
              .addAll(LIST_TYPES)
              .addAll(DICT_TYPES)
              .addAll(STRING_TYPES)
              .addAll(INTEGER_TYPES)
              .build());

  /** Returns false iff we know with certainty that the element cannot resolve to the given type. */
  public static boolean possiblyValidType(PsiElement element, Build.Attribute.Discriminator type) {
    if (!HANDLED_TYPES.contains(type)) {
      return true;
    }
    if (element instanceof ListLiteral || element instanceof GlobExpression) {
      return LIST_TYPES.contains(type);
    }
    if (element instanceof StringLiteral) {
      return STRING_TYPES.contains(type);
    }
    if (element instanceof DictionaryLiteral) {
      return DICT_TYPES.contains(type);
    }
    if (element instanceof IntegerLiteral) {
      return INTEGER_TYPES.contains(type);
    }
    return true;
  }

  /** Returns false iff we know with certainty that the element cannot resolve to a list literal. */
  public static boolean possiblyValidListLiteral(PsiElement element) {
    if (element instanceof ListLiteral || element instanceof GlobExpression) {
      return true; // these evaluate directly to list literals
    }
    if (element instanceof LiteralExpression) {
      return false; // all other literals cannot evaluate to a ListLiteral
    }
    if (element instanceof LoadStatement || element instanceof FunctionStatement) {
      return false;
    }
    // everything else treated as possibly evaluating to a list
    return true;
  }

  /**
   * Returns false iff we know with certainty that the element cannot resolve to a string literal.
   */
  public static boolean possiblyValidStringLiteral(PsiElement element) {
    if (element instanceof StringLiteral) {
      return true;
    }
    if (element instanceof LiteralExpression) {
      return false; // all other literals cannot evaluate to a StringLiteral
    }
    if (element instanceof LoadStatement
        || element instanceof FunctionStatement
        || element instanceof GlobExpression) {
      return false;
    }
    // everything else treated as possibly evaluating to a string
    return true;
  }
}
