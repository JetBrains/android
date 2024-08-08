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
package com.google.idea.blaze.base.lang.buildfile.formatting;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.lang.buildfile.lexer.BuildToken;
import com.google.idea.blaze.base.lang.buildfile.lexer.TokenKind;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildElementTypes;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.LoadStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.references.QuoteType;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import java.util.List;
import javax.annotation.Nullable;

/** Simple code block folding for BUILD files. */
public class BuildFileFoldingBuilder implements FoldingBuilder {

  private final BoolExperiment enabled =
      new BoolExperiment("build.files.code.folding.enabled", true);

  @Override
  public FoldingDescriptor[] buildFoldRegions(ASTNode node, Document document) {
    if (!enabled.getValue()) {
      return FoldingDescriptor.EMPTY;
    }
    List<FoldingDescriptor> descriptors = Lists.newArrayList();
    addDescriptors(descriptors, node);
    return descriptors.toArray(FoldingDescriptor.EMPTY);
  }

  private void addDescriptors(List<FoldingDescriptor> descriptors, ASTNode node) {
    IElementType type = node.getElementType();
    if (type == BuildElementTypes.FUNCTION_STATEMENT) {
      foldFunctionDefinition(descriptors, node);
    } else if (type == BuildElementTypes.FUNCALL_EXPRESSION
        || type == BuildElementTypes.LOAD_STATEMENT) {
      foldFunctionCall(descriptors, node);
    } else if (type == BuildElementTypes.STRING_LITERAL) {
      foldLongStrings(descriptors, node);
    } else if (type == BuildToken.COMMENT) {
      foldSequentialComments(descriptors, node);
    }
    ASTNode child = node.getFirstChildNode();
    while (child != null) {
      addDescriptors(descriptors, child);
      child = child.getTreeNext();
    }
  }

  private static void foldFunctionDefinition(List<FoldingDescriptor> descriptors, ASTNode node) {
    ASTNode colon = node.findChildByType(BuildToken.fromKind(TokenKind.COLON));
    if (colon == null) {
      return;
    }
    ASTNode stmtList = node.findChildByType(BuildElementTypes.STATEMENT_LIST);
    if (stmtList == null) {
      return;
    }
    int start = colon.getStartOffset() + 1;
    int end = endOfList(stmtList);
    descriptors.add(new FoldingDescriptor(node, range(start, end)));
  }

  private static void foldFunctionCall(List<FoldingDescriptor> descriptors, ASTNode node) {
    IElementType type = node.getElementType();
    ASTNode listNode =
        type == BuildElementTypes.FUNCALL_EXPRESSION
            ? node.findChildByType(BuildElementTypes.ARGUMENT_LIST)
            : node;
    if (listNode == null) {
      return;
    }
    ASTNode lParen = listNode.findChildByType(BuildToken.fromKind(TokenKind.LPAREN));
    ASTNode rParen = listNode.findChildByType(BuildToken.fromKind(TokenKind.RPAREN));
    if (lParen == null || rParen == null) {
      return;
    }
    int start = lParen.getStartOffset() + 1;
    int end = rParen.getTextRange().getEndOffset() - 1;
    descriptors.add(new FoldingDescriptor(node, range(start, end)));
  }

  private static void foldLongStrings(List<FoldingDescriptor> descriptors, ASTNode node) {
    boolean isMultiLine = node.textContains('\n');
    if (isMultiLine) {
      descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
    }
  }

  private static void foldSequentialComments(List<FoldingDescriptor> descriptors, ASTNode node) {
    // need to skip previous comments in sequence
    ASTNode curNode = node.getTreePrev();
    while (curNode != null) {
      if (curNode.getElementType() == BuildToken.COMMENT) {
        return;
      }
      curNode = isWhitespaceOrNewline(curNode) ? curNode.getTreePrev() : null;
    }

    // fold sequence comments in one block
    curNode = node.getTreeNext();
    ASTNode lastCommentNode = node;
    while (curNode != null) {
      if (curNode.getElementType() == BuildToken.COMMENT) {
        lastCommentNode = curNode;
        curNode = curNode.getTreeNext();
        continue;
      }
      curNode = isWhitespaceOrNewline(curNode) ? curNode.getTreeNext() : null;
    }

    if (lastCommentNode != node) {
      descriptors.add(
          new FoldingDescriptor(
              node,
              TextRange.create(
                  node.getStartOffset(), lastCommentNode.getTextRange().getEndOffset())));
    }
  }

  private static boolean isWhitespaceOrNewline(ASTNode node) {
    if (node.getPsi() instanceof PsiWhiteSpace) {
      return true;
    }
    return BuildToken.WHITESPACE_AND_NEWLINE.contains(node.getElementType());
  }

  private static TextRange range(int start, int end) {
    if (start >= end) {
      return new TextRange(start, start + 1);
    }
    return new TextRange(start, end);
  }

  /**
   * Don't include whitespace and newlines at the end of the function.<br>
   * Could do this in the lexer instead, with additional look-ahead checks.
   */
  private static int endOfList(ASTNode stmtList) {
    ASTNode child = getLastChildRecursively(stmtList);
    while (child != null) {
      if (!isWhitespaceOrNewline(child)) {
        return child.getTextRange().getEndOffset();
      }
      child = child.getTreePrev();
    }
    return stmtList.getTextRange().getEndOffset();
  }

  private static ASTNode getLastChildRecursively(ASTNode node) {
    ASTNode child = node;
    while (true) {
      ASTNode newChild = child.getLastChildNode();
      if (newChild == null) {
        return child;
      }
      child = newChild;
    }
  }

  @Override
  @Nullable
  public String getPlaceholderText(ASTNode node) {
    PsiElement psi = node.getPsi();
    if (psi instanceof FuncallExpression) {
      FuncallExpression expr = (FuncallExpression) psi;
      String name = expr.getNameArgumentValue();
      if (name != null) {
        return "name = \"" + name + "\"...";
      }
    }
    if (psi instanceof LoadStatement) {
      String fileName = ((LoadStatement) psi).getImportedPath();
      if (fileName != null) {
        return "\"" + fileName + "\"...";
      }
    }
    if (psi instanceof PsiComment) {
      return "#...";
    }
    if (psi instanceof StringLiteral) {
      StringLiteral str = ((StringLiteral) psi);
      QuoteType type = str.getQuoteType();
      String contents = str.getStringContents();
      if (contents.contains("\n")) {
        return type.wrap(getFirstLine(contents) + "...");
      }
      return type.wrap("...");
    }
    return "...";
  }

  private static String getFirstLine(String string) {
    int newlineIx = string.indexOf('\n');
    return newlineIx == -1 ? string : string.substring(0, newlineIx);
  }

  @Override
  public boolean isCollapsedByDefault(ASTNode node) {
    return false;
  }
}
