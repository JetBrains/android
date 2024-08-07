/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.psi;

import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;

/** Collects the types used by the PsiBuilder to construct the AST */
public interface BuildElementTypes {

  IFileElementType BUILD_FILE = new IFileElementType(BuildFileType.INSTANCE.getLanguage());

  // Statements
  BuildElementType RETURN_STATEMENT = new BuildElementType("return", ReturnStatement.class);
  BuildElementType PASS_STATMENT = new BuildElementType("pass", PassStatement.class);
  BuildElementType ASSIGNMENT_STATEMENT =
      new BuildElementType("assignment", AssignmentStatement.class);
  BuildElementType AUGMENTED_ASSIGNMENT =
      new BuildElementType("aug_assign", AugmentedAssignmentStatement.class);
  BuildElementType FLOW_STATEMENT = new BuildElementType("flow", FlowStatement.class);
  BuildElementType LOAD_STATEMENT = new BuildElementType("load", LoadStatement.class);
  BuildElementType FUNCTION_STATEMENT =
      new BuildElementType("function_def", FunctionStatement.class);
  BuildElementType FOR_STATEMENT = new BuildElementType("for", ForStatement.class);
  BuildElementType IF_STATEMENT = new BuildElementType("if", IfStatement.class);

  BuildElementType IF_PART = new BuildElementType("if_part", IfPart.class);
  BuildElementType ELSE_IF_PART = new BuildElementType("else_if_part", ElseIfPart.class);
  BuildElementType ELSE_PART = new BuildElementType("else_part", ElsePart.class);

  BuildElementType STATEMENT_LIST = new BuildElementType("stmt_list", StatementList.class);

  // passed arguments
  BuildElementType ARGUMENT_LIST = new BuildElementType("arg_list", ArgumentList.class);
  BuildElementType KEYWORD = new BuildElementType("keyword", Argument.Keyword.class);
  BuildElementType POSITIONAL = new BuildElementType("positional", Argument.Positional.class);
  BuildElementType STAR = new BuildElementType("*", Argument.Star.class);
  BuildElementType STAR_STAR = new BuildElementType("**", Argument.StarStar.class);

  // parameters
  BuildElementType PARAMETER_LIST = new BuildElementType("parameter_list", ParameterList.class);
  BuildElementType PARAM_OPTIONAL =
      new BuildElementType("optional_param", Parameter.Optional.class);
  BuildElementType PARAM_MANDATORY =
      new BuildElementType("mandatory_param", Parameter.Mandatory.class);
  BuildElementType PARAM_STAR = new BuildElementType("*", Parameter.Star.class);
  BuildElementType PARAM_STAR_STAR = new BuildElementType("**", Parameter.StarStar.class);

  // Expressions
  BuildElementType DICTIONARY_LITERAL = new BuildElementType("dict", DictionaryLiteral.class);
  BuildElementType DICTIONARY_ENTRY_LITERAL =
      new BuildElementType("dict_entry", DictionaryEntryLiteral.class);
  BuildElementType BINARY_OP_EXPRESSION =
      new BuildElementType("binary_op", BinaryOpExpression.class);
  BuildElementType FUNCALL_EXPRESSION =
      new BuildElementType("function_call", FuncallExpression.class);
  BuildElementType DOT_EXPRESSION = new BuildElementType("dot_expr", DotExpression.class);
  BuildElementType STRING_LITERAL = new BuildElementType("string", StringLiteral.class);
  BuildElementType INTEGER_LITERAL = new BuildElementType("int", IntegerLiteral.class);
  BuildElementType LIST_LITERAL = new BuildElementType("list", ListLiteral.class);
  BuildElementType GLOB_EXPRESSION = new BuildElementType("glob", GlobExpression.class);
  BuildElementType REFERENCE_EXPRESSION =
      new BuildElementType("reference", ReferenceExpression.class);
  BuildElementType TARGET_EXPRESSION = new BuildElementType("target", TargetExpression.class);
  BuildElementType LIST_COMPREHENSION_EXPR =
      new BuildElementType("list_comp", ListComprehensionExpression.class);
  BuildElementType LOADED_SYMBOL = new BuildElementType("loaded_symbol", LoadedSymbol.class);
  BuildElementType PARENTHESIZED_EXPRESSION =
      new BuildElementType("parens", ParenthesizedExpression.class);
  BuildElementType TUPLE_EXPRESSION = new BuildElementType("tuple", TupleExpression.class);

  TokenSet EXPRESSIONS =
      TokenSet.create(
          FUNCALL_EXPRESSION,
          DICTIONARY_LITERAL,
          DICTIONARY_ENTRY_LITERAL,
          BINARY_OP_EXPRESSION,
          DOT_EXPRESSION,
          STRING_LITERAL,
          INTEGER_LITERAL,
          LIST_LITERAL,
          PARENTHESIZED_EXPRESSION,
          TUPLE_EXPRESSION,
          REFERENCE_EXPRESSION,
          TARGET_EXPRESSION,
          LIST_COMPREHENSION_EXPR,
          GLOB_EXPRESSION,
          LOADED_SYMBOL);

  TokenSet STATEMENTS =
      TokenSet.create(
          RETURN_STATEMENT,
          PASS_STATMENT,
          ASSIGNMENT_STATEMENT,
          FLOW_STATEMENT,
          LOAD_STATEMENT,
          FUNCTION_STATEMENT,
          FOR_STATEMENT,
          IF_STATEMENT);

  TokenSet ARGUMENTS = TokenSet.create(KEYWORD, POSITIONAL, STAR, STAR_STAR);

  TokenSet PARAMETERS =
      TokenSet.create(PARAM_OPTIONAL, PARAM_MANDATORY, PARAM_STAR, PARAM_STAR_STAR);

  TokenSet STRINGS = TokenSet.create(STRING_LITERAL);
  TokenSet FUNCTIONS = TokenSet.create(FUNCTION_STATEMENT);
}
