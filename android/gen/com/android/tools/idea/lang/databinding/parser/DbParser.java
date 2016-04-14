// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.lang.databinding.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.android.tools.idea.lang.databinding.psi.DbTokenTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class DbParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, EXTENDS_SETS_);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    if (t == ADD_EXPR) {
      r = expr(b, 0, 10);
    }
    else if (t == BINARY_AND_EXPR) {
      r = expr(b, 0, 5);
    }
    else if (t == BINARY_OR_EXPR) {
      r = expr(b, 0, 3);
    }
    else if (t == BINARY_XOR_EXPR) {
      r = expr(b, 0, 4);
    }
    else if (t == BIT_SHIFT_EXPR) {
      r = expr(b, 0, 9);
    }
    else if (t == BRACKET_EXPR) {
      r = expr(b, 0, 16);
    }
    else if (t == CAST_EXPR) {
      r = castExpr(b, 0);
    }
    else if (t == CLASS_EXTRACTION_EXPR) {
      r = classExtractionExpr(b, 0);
    }
    else if (t == CLASS_OR_INTERFACE_TYPE) {
      r = classOrInterfaceType(b, 0);
    }
    else if (t == CONSTANT_VALUE) {
      r = constantValue(b, 0);
    }
    else if (t == DEFAULTS) {
      r = defaults(b, 0);
    }
    else if (t == DOT_EXPR) {
      r = expr(b, 0, 17);
    }
    else if (t == EQ_COMPARISON_EXPR) {
      r = expr(b, 0, 6);
    }
    else if (t == EXPR) {
      r = expr(b, 0, -1);
    }
    else if (t == EXPRESSION_LIST) {
      r = expressionList(b, 0);
    }
    else if (t == FIELD_NAME) {
      r = fieldName(b, 0);
    }
    else if (t == ID_EXPR) {
      r = idExpr(b, 0);
    }
    else if (t == INEQ_COMPARISON_EXPR) {
      r = expr(b, 0, 8);
    }
    else if (t == INSTANCE_OF_EXPR) {
      r = expr(b, 0, 7);
    }
    else if (t == LITERAL_EXPR) {
      r = literalExpr(b, 0);
    }
    else if (t == LOGICAL_AND_EXPR) {
      r = expr(b, 0, 2);
    }
    else if (t == LOGICAL_OR_EXPR) {
      r = expr(b, 0, 1);
    }
    else if (t == METHOD_EXPR) {
      r = expr(b, 0, 15);
    }
    else if (t == METHOD_NAME) {
      r = methodName(b, 0);
    }
    else if (t == MUL_EXPR) {
      r = expr(b, 0, 11);
    }
    else if (t == NEGATION_EXPR) {
      r = negationExpr(b, 0);
    }
    else if (t == NULL_COALESCE_EXPR) {
      r = expr(b, 0, -1);
    }
    else if (t == PAREN_EXPR) {
      r = parenExpr(b, 0);
    }
    else if (t == PRIMITIVE_TYPE) {
      r = primitiveType(b, 0);
    }
    else if (t == RESOURCE_PARAMETERS) {
      r = resourceParameters(b, 0);
    }
    else if (t == RESOURCES_EXPR) {
      r = resourcesExpr(b, 0);
    }
    else if (t == SIGN_CHANGE_EXPR) {
      r = signChangeExpr(b, 0);
    }
    else if (t == TERNARY_EXPR) {
      r = expr(b, 0, 0);
    }
    else if (t == TYPE) {
      r = type(b, 0);
    }
    else if (t == TYPE_ARGUMENTS) {
      r = typeArguments(b, 0);
    }
    else {
      r = parse_root_(t, b, 0);
    }
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return dataBindingExpression(b, l + 1);
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(ADD_EXPR, BINARY_AND_EXPR, BINARY_OR_EXPR, BINARY_XOR_EXPR,
      BIT_SHIFT_EXPR, BRACKET_EXPR, CAST_EXPR, CLASS_EXTRACTION_EXPR,
      DOT_EXPR, EQ_COMPARISON_EXPR, EXPR, ID_EXPR,
      INEQ_COMPARISON_EXPR, INSTANCE_OF_EXPR, LITERAL_EXPR, LOGICAL_AND_EXPR,
      LOGICAL_OR_EXPR, METHOD_EXPR, MUL_EXPR, NEGATION_EXPR,
      NULL_COALESCE_EXPR, PAREN_EXPR, RESOURCES_EXPR, SIGN_CHANGE_EXPR,
      TERNARY_EXPR),
  };

  /* ********************************************************** */
  // '+' | '-'
  static boolean addOp(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "addOp")) return false;
    if (!nextTokenIs(b, "", PLUS, MINUS)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, PLUS);
    if (!r) r = consumeToken(b, MINUS);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '<<' | '>>>' | '>>'
  static boolean bitShiftOp(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bitShiftOp")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LTLT);
    if (!r) r = consumeToken(b, GTGTGT);
    if (!r) r = consumeToken(b, GTGT);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER typeArguments? ('.' IDENTIFIER typeArguments? )*
  public static boolean classOrInterfaceType(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "classOrInterfaceType")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    r = r && classOrInterfaceType_1(b, l + 1);
    r = r && classOrInterfaceType_2(b, l + 1);
    exit_section_(b, m, CLASS_OR_INTERFACE_TYPE, r);
    return r;
  }

  // typeArguments?
  private static boolean classOrInterfaceType_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "classOrInterfaceType_1")) return false;
    typeArguments(b, l + 1);
    return true;
  }

  // ('.' IDENTIFIER typeArguments? )*
  private static boolean classOrInterfaceType_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "classOrInterfaceType_2")) return false;
    int c = current_position_(b);
    while (true) {
      if (!classOrInterfaceType_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "classOrInterfaceType_2", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // '.' IDENTIFIER typeArguments?
  private static boolean classOrInterfaceType_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "classOrInterfaceType_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DOT);
    r = r && consumeToken(b, IDENTIFIER);
    r = r && classOrInterfaceType_2_0_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // typeArguments?
  private static boolean classOrInterfaceType_2_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "classOrInterfaceType_2_0_2")) return false;
    typeArguments(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // literal
  //   |   RESOURCE_REFERENCE
  //   |   IDENTIFIER
  public static boolean constantValue(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "constantValue")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, "<constant value>");
    r = literal(b, l + 1);
    if (!r) r = consumeToken(b, RESOURCE_REFERENCE);
    if (!r) r = consumeToken(b, IDENTIFIER);
    exit_section_(b, l, m, CONSTANT_VALUE, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // expr defaults?
  static boolean dataBindingExpression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dataBindingExpression")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = expr(b, l + 1, -1);
    r = r && dataBindingExpression_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // defaults?
  private static boolean dataBindingExpression_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dataBindingExpression_1")) return false;
    defaults(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // ',' 'default' '=' constantValue
  public static boolean defaults(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "defaults")) return false;
    if (!nextTokenIs(b, COMMA)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && consumeToken(b, DEFAULT_KEYWORD);
    r = r && consumeToken(b, EQ);
    r = r && constantValue(b, l + 1);
    exit_section_(b, m, DEFAULTS, r);
    return r;
  }

  /* ********************************************************** */
  // '==' | '!='
  static boolean eqComparisonOp(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "eqComparisonOp")) return false;
    if (!nextTokenIs(b, "", NE, EQEQ)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, EQEQ);
    if (!r) r = consumeToken(b, NE);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // expr (',' expr)*
  public static boolean expressionList(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expressionList")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, "<expression list>");
    r = expr(b, l + 1, -1);
    r = r && expressionList_1(b, l + 1);
    exit_section_(b, l, m, EXPRESSION_LIST, r, false, null);
    return r;
  }

  // (',' expr)*
  private static boolean expressionList_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expressionList_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!expressionList_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "expressionList_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // ',' expr
  private static boolean expressionList_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expressionList_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean fieldName(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fieldName")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    exit_section_(b, m, FIELD_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // '<=' | '>=' | '<' | '>'
  static boolean ineqComparisonOp(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ineqComparisonOp")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LE);
    if (!r) r = consumeToken(b, GTEQ);
    if (!r) r = consumeToken(b, LT);
    if (!r) r = consumeToken(b, GT);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // INTEGER_LITERAL
  //   |   FLOAT_LITERAL
  //   |   LONG_LITERAL
  //   |   DOUBLE_LITERAL
  //   |   TRUE | FALSE
  //   |   NULL
  //   |   CHARACTER_LITERAL
  //   |   STRING_LITERAL
  static boolean literal(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literal")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, INTEGER_LITERAL);
    if (!r) r = consumeToken(b, FLOAT_LITERAL);
    if (!r) r = consumeToken(b, LONG_LITERAL);
    if (!r) r = consumeToken(b, DOUBLE_LITERAL);
    if (!r) r = consumeToken(b, TRUE);
    if (!r) r = consumeToken(b, FALSE);
    if (!r) r = consumeToken(b, NULL);
    if (!r) r = consumeToken(b, CHARACTER_LITERAL);
    if (!r) r = consumeToken(b, STRING_LITERAL);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean methodName(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "methodName")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    exit_section_(b, m, METHOD_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // '*' | '/' | '%'
  static boolean mulOp(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mulOp")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ASTERISK);
    if (!r) r = consumeToken(b, DIV);
    if (!r) r = consumeToken(b, PERC);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '~' | '!'
  static boolean negationOp(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "negationOp")) return false;
    if (!nextTokenIs(b, "", EXCL, TILDE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, TILDE);
    if (!r) r = consumeToken(b, EXCL);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // BOOLEAN_KEYWORD
  //   |   BYTE_KEYWORD
  //   |   CHAR_KEYWORD
  //   |   SHORT_KEYWORD
  //   |   INT_KEYWORD
  //   |   LONG_KEYWORD
  //   |   FLOAT_KEYWORD
  //   |   DOUBLE_KEYWORD
  public static boolean primitiveType(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "primitiveType")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, "<primitive type>");
    r = consumeToken(b, BOOLEAN_KEYWORD);
    if (!r) r = consumeToken(b, BYTE_KEYWORD);
    if (!r) r = consumeToken(b, CHAR_KEYWORD);
    if (!r) r = consumeToken(b, SHORT_KEYWORD);
    if (!r) r = consumeToken(b, INT_KEYWORD);
    if (!r) r = consumeToken(b, LONG_KEYWORD);
    if (!r) r = consumeToken(b, FLOAT_KEYWORD);
    if (!r) r = consumeToken(b, DOUBLE_KEYWORD);
    exit_section_(b, l, m, PRIMITIVE_TYPE, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '(' expressionList ')'
  public static boolean resourceParameters(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "resourceParameters")) return false;
    if (!nextTokenIs(b, LPARENTH)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPARENTH);
    r = r && expressionList(b, l + 1);
    r = r && consumeToken(b, RPARENTH);
    exit_section_(b, m, RESOURCE_PARAMETERS, r);
    return r;
  }

  /* ********************************************************** */
  // '+' | '-'
  static boolean signOp(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "signOp")) return false;
    if (!nextTokenIs(b, "", PLUS, MINUS)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, PLUS);
    if (!r) r = consumeToken(b, MINUS);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // primitiveType ('[' ']')* | classOrInterfaceType ('[' ']')*
  public static boolean type(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, "<type>");
    r = type_0(b, l + 1);
    if (!r) r = type_1(b, l + 1);
    exit_section_(b, l, m, TYPE, r, false, null);
    return r;
  }

  // primitiveType ('[' ']')*
  private static boolean type_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = primitiveType(b, l + 1);
    r = r && type_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // ('[' ']')*
  private static boolean type_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_0_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!type_0_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "type_0_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // '[' ']'
  private static boolean type_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACKET);
    r = r && consumeToken(b, RBRACKET);
    exit_section_(b, m, null, r);
    return r;
  }

  // classOrInterfaceType ('[' ']')*
  private static boolean type_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = classOrInterfaceType(b, l + 1);
    r = r && type_1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // ('[' ']')*
  private static boolean type_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_1_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!type_1_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "type_1_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // '[' ']'
  private static boolean type_1_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_1_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACKET);
    r = r && consumeToken(b, RBRACKET);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '<' type (',' type)* '>'
  public static boolean typeArguments(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeArguments")) return false;
    if (!nextTokenIs(b, LT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LT);
    r = r && type(b, l + 1);
    r = r && typeArguments_2(b, l + 1);
    r = r && consumeToken(b, GT);
    exit_section_(b, m, TYPE_ARGUMENTS, r);
    return r;
  }

  // (',' type)*
  private static boolean typeArguments_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeArguments_2")) return false;
    int c = current_position_(b);
    while (true) {
      if (!typeArguments_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "typeArguments_2", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // ',' type
  private static boolean typeArguments_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeArguments_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && type(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // Expression root: expr
  // Operator priority table:
  // 0: BINARY(nullCoalesceExpr)
  // 1: BINARY(ternaryExpr)
  // 2: BINARY(logicalOrExpr)
  // 3: BINARY(logicalAndExpr)
  // 4: BINARY(binaryOrExpr)
  // 5: BINARY(binaryXorExpr)
  // 6: BINARY(binaryAndExpr)
  // 7: BINARY(eqComparisonExpr)
  // 8: BINARY(instanceOfExpr)
  // 9: BINARY(ineqComparisonExpr)
  // 10: BINARY(bitShiftExpr)
  // 11: BINARY(addExpr)
  // 12: BINARY(mulExpr)
  // 13: PREFIX(negationExpr)
  // 14: PREFIX(signChangeExpr)
  // 15: PREFIX(castExpr)
  // 16: POSTFIX(methodExpr)
  // 17: BINARY(bracketExpr)
  // 18: POSTFIX(dotExpr)
  // 19: ATOM(resourcesExpr)
  // 20: ATOM(classExtractionExpr)
  // 21: ATOM(literalExpr)
  // 22: ATOM(idExpr)
  // 23: PREFIX(parenExpr)
  public static boolean expr(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "expr")) return false;
    addVariant(b, "<expr>");
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, "<expr>");
    r = negationExpr(b, l + 1);
    if (!r) r = signChangeExpr(b, l + 1);
    if (!r) r = castExpr(b, l + 1);
    if (!r) r = resourcesExpr(b, l + 1);
    if (!r) r = classExtractionExpr(b, l + 1);
    if (!r) r = literalExpr(b, l + 1);
    if (!r) r = idExpr(b, l + 1);
    if (!r) r = parenExpr(b, l + 1);
    p = r;
    r = r && expr_0(b, l + 1, g);
    exit_section_(b, l, m, null, r, p, null);
    return r || p;
  }

  public static boolean expr_0(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "expr_0")) return false;
    boolean r = true;
    while (true) {
      Marker m = enter_section_(b, l, _LEFT_, null);
      if (g < 0 && consumeTokenSmart(b, QUESTQUEST)) {
        r = expr(b, l, 0);
        exit_section_(b, l, m, NULL_COALESCE_EXPR, r, true, null);
      }
      else if (g < 1 && consumeTokenSmart(b, QUEST)) {
        r = report_error_(b, expr(b, l, 1));
        r = ternaryExpr_1(b, l + 1) && r;
        exit_section_(b, l, m, TERNARY_EXPR, r, true, null);
      }
      else if (g < 2 && consumeTokenSmart(b, OROR)) {
        r = expr(b, l, 2);
        exit_section_(b, l, m, LOGICAL_OR_EXPR, r, true, null);
      }
      else if (g < 3 && consumeTokenSmart(b, ANDAND)) {
        r = expr(b, l, 3);
        exit_section_(b, l, m, LOGICAL_AND_EXPR, r, true, null);
      }
      else if (g < 4 && consumeTokenSmart(b, OR)) {
        r = expr(b, l, 4);
        exit_section_(b, l, m, BINARY_OR_EXPR, r, true, null);
      }
      else if (g < 5 && consumeTokenSmart(b, XOR)) {
        r = expr(b, l, 5);
        exit_section_(b, l, m, BINARY_XOR_EXPR, r, true, null);
      }
      else if (g < 6 && consumeTokenSmart(b, AND)) {
        r = expr(b, l, 6);
        exit_section_(b, l, m, BINARY_AND_EXPR, r, true, null);
      }
      else if (g < 7 && eqComparisonOp(b, l + 1)) {
        r = expr(b, l, 7);
        exit_section_(b, l, m, EQ_COMPARISON_EXPR, r, true, null);
      }
      else if (g < 8 && consumeTokenSmart(b, INSTANCEOF_KEYWORD)) {
        r = expr(b, l, 8);
        exit_section_(b, l, m, INSTANCE_OF_EXPR, r, true, null);
      }
      else if (g < 9 && ineqComparisonOp(b, l + 1)) {
        r = expr(b, l, 9);
        exit_section_(b, l, m, INEQ_COMPARISON_EXPR, r, true, null);
      }
      else if (g < 10 && bitShiftOp(b, l + 1)) {
        r = expr(b, l, 10);
        exit_section_(b, l, m, BIT_SHIFT_EXPR, r, true, null);
      }
      else if (g < 11 && addOp(b, l + 1)) {
        r = expr(b, l, 11);
        exit_section_(b, l, m, ADD_EXPR, r, true, null);
      }
      else if (g < 12 && mulOp(b, l + 1)) {
        r = expr(b, l, 12);
        exit_section_(b, l, m, MUL_EXPR, r, true, null);
      }
      else if (g < 16 && methodExpr_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, METHOD_EXPR, r, true, null);
      }
      else if (g < 17 && consumeTokenSmart(b, LBRACKET)) {
        r = report_error_(b, expr(b, l, 17));
        r = consumeToken(b, RBRACKET) && r;
        exit_section_(b, l, m, BRACKET_EXPR, r, true, null);
      }
      else if (g < 18 && dotExpr_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, DOT_EXPR, r, true, null);
      }
      else {
        exit_section_(b, l, m, null, false, false, null);
        break;
      }
    }
    return r;
  }

  // ':' expr
  private static boolean ternaryExpr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ternaryExpr_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COLON);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  public static boolean negationExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "negationExpr")) return false;
    if (!nextTokenIsFast(b, EXCL, TILDE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = negationOp(b, l + 1);
    p = r;
    r = p && expr(b, l, 13);
    exit_section_(b, l, m, NEGATION_EXPR, r, p, null);
    return r || p;
  }

  public static boolean signChangeExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "signChangeExpr")) return false;
    if (!nextTokenIsFast(b, PLUS, MINUS)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = signOp(b, l + 1);
    p = r;
    r = p && expr(b, l, 14);
    exit_section_(b, l, m, SIGN_CHANGE_EXPR, r, p, null);
    return r || p;
  }

  public static boolean castExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "castExpr")) return false;
    if (!nextTokenIsFast(b, LPARENTH)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = castExpr_0(b, l + 1);
    p = r;
    r = p && expr(b, l, 15);
    exit_section_(b, l, m, CAST_EXPR, r, p, null);
    return r || p;
  }

  // '(' type ')'
  private static boolean castExpr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "castExpr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, LPARENTH);
    r = r && type(b, l + 1);
    r = r && consumeToken(b, RPARENTH);
    exit_section_(b, m, null, r);
    return r;
  }

  // '.' methodName '(' expressionList? ')'
  private static boolean methodExpr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "methodExpr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, DOT);
    r = r && methodName(b, l + 1);
    r = r && consumeToken(b, LPARENTH);
    r = r && methodExpr_0_3(b, l + 1);
    r = r && consumeToken(b, RPARENTH);
    exit_section_(b, m, null, r);
    return r;
  }

  // expressionList?
  private static boolean methodExpr_0_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "methodExpr_0_3")) return false;
    expressionList(b, l + 1);
    return true;
  }

  // '.' fieldName
  private static boolean dotExpr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dotExpr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, DOT);
    r = r && fieldName(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // RESOURCE_REFERENCE resourceParameters?
  public static boolean resourcesExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "resourcesExpr")) return false;
    if (!nextTokenIsFast(b, RESOURCE_REFERENCE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, RESOURCE_REFERENCE);
    r = r && resourcesExpr_1(b, l + 1);
    exit_section_(b, m, RESOURCES_EXPR, r);
    return r;
  }

  // resourceParameters?
  private static boolean resourcesExpr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "resourcesExpr_1")) return false;
    resourceParameters(b, l + 1);
    return true;
  }

  // (type|'void') '.' 'class'
  public static boolean classExtractionExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "classExtractionExpr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, "<class extraction expr>");
    r = classExtractionExpr_0(b, l + 1);
    r = r && consumeToken(b, DOT);
    r = r && consumeToken(b, CLASS_KEYWORD);
    exit_section_(b, l, m, CLASS_EXTRACTION_EXPR, r, false, null);
    return r;
  }

  // type|'void'
  private static boolean classExtractionExpr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "classExtractionExpr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type(b, l + 1);
    if (!r) r = consumeTokenSmart(b, VOID_KEYWORD);
    exit_section_(b, m, null, r);
    return r;
  }

  // literal
  public static boolean literalExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literalExpr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, "<literal expr>");
    r = literal(b, l + 1);
    exit_section_(b, l, m, LITERAL_EXPR, r, false, null);
    return r;
  }

  // IDENTIFIER
  public static boolean idExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "idExpr")) return false;
    if (!nextTokenIsFast(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, IDENTIFIER);
    exit_section_(b, m, ID_EXPR, r);
    return r;
  }

  public static boolean parenExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parenExpr")) return false;
    if (!nextTokenIsFast(b, LPARENTH)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = consumeTokenSmart(b, LPARENTH);
    p = r;
    r = p && expr(b, l, -1);
    r = p && report_error_(b, consumeToken(b, RPARENTH)) && r;
    exit_section_(b, l, m, PAREN_EXPR, r, p, null);
    return r || p;
  }

}
