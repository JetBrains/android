// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.logcat.filters.parser;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.android.tools.idea.logcat.filters.parser.impl.*;

public interface LogcatFilterTypes {

  IElementType AND_EXPRESSION = new LogcatFilterElementType("AND_EXPRESSION");
  IElementType EXPRESSION = new LogcatFilterElementType("EXPRESSION");
  IElementType LITERAL_EXPRESSION = new LogcatFilterElementType("LITERAL_EXPRESSION");
  IElementType OR_EXPRESSION = new LogcatFilterElementType("OR_EXPRESSION");
  IElementType PAREN_EXPRESSION = new LogcatFilterElementType("PAREN_EXPRESSION");

  IElementType AND = new LogcatFilterTokenType("AND");
  IElementType KEY = new LogcatFilterTokenType("KEY");
  IElementType LPAREN = new LogcatFilterTokenType("LPAREN");
  IElementType OR = new LogcatFilterTokenType("OR");
  IElementType RPAREN = new LogcatFilterTokenType("RPAREN");
  IElementType VALUE = new LogcatFilterTokenType("VALUE");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == AND_EXPRESSION) {
        return new LogcatFilterAndExpressionImpl(node);
      }
      else if (type == LITERAL_EXPRESSION) {
        return new LogcatFilterLiteralExpressionImpl(node);
      }
      else if (type == OR_EXPRESSION) {
        return new LogcatFilterOrExpressionImpl(node);
      }
      else if (type == PAREN_EXPRESSION) {
        return new LogcatFilterParenExpressionImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
