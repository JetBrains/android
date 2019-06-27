// generatedFilesHeader.txt
package com.android.tools.idea.lang.multiDexKeep.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.android.tools.idea.lang.multiDexKeep.psi.impl.*;

public interface MultiDexKeepPsiTypes {

  IElementType CLASS_NAME = new MultiDexKeepElementType("CLASS_NAME");
  IElementType CLASS_NAMES = new MultiDexKeepElementType("CLASS_NAMES");

  IElementType EOL = new MultiDexKeepTokenType("EOL");
  IElementType STRING = new MultiDexKeepTokenType("STRING");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == CLASS_NAME) {
        return new MultiDexKeepClassNameImpl(node);
      }
      else if (type == CLASS_NAMES) {
        return new MultiDexKeepClassNamesImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
