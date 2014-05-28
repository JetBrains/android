// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.lang.proguard.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.android.tools.idea.lang.proguard.psi.impl.*;

public interface ProguardTypes {

  IElementType COMMENT = new ProguardElementType("COMMENT");
  IElementType FLAG = new ProguardElementType("FLAG");
  IElementType JAVA_SECTION = new ProguardElementType("JAVA_SECTION");
  IElementType MULTI_LINE_FLAG = new ProguardElementType("MULTI_LINE_FLAG");
  IElementType SINGLE_LINE_FLAG = new ProguardElementType("SINGLE_LINE_FLAG");

  IElementType CLOSE_BRACE = new ProguardTokenType("CLOSE_BRACE");
  IElementType CRLF = new ProguardTokenType("CRLF");
  IElementType FLAG_ARG = new ProguardTokenType("FLAG_ARG");
  IElementType FLAG_NAME = new ProguardTokenType("FLAG_NAME");
  IElementType JAVA_DECL = new ProguardTokenType("JAVA_DECL");
  IElementType LINE_CMT = new ProguardTokenType("LINE_CMT");
  IElementType OPEN_BRACE = new ProguardTokenType("OPEN_BRACE");
  IElementType WS = new ProguardTokenType("WS");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
       if (type == COMMENT) {
        return new ProguardCommentImpl(node);
      }
      else if (type == FLAG) {
        return new ProguardFlagImpl(node);
      }
      else if (type == JAVA_SECTION) {
        return new ProguardJavaSectionImpl(node);
      }
      else if (type == MULTI_LINE_FLAG) {
        return new ProguardMultiLineFlagImpl(node);
      }
      else if (type == SINGLE_LINE_FLAG) {
        return new ProguardSingleLineFlagImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
