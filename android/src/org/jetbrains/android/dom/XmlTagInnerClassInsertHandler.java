// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package org.jetbrains.android.dom;

import com.android.utils.Pair;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.android.dom.converters.PackageClassConverter;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * {@link InsertHandler} for Xml tags of inner classes.
 *
 * These tags must be inserted as a view tag since the fully qualified name has a "$" sign
 * which would be an invalid xml tag name.
 */
public class XmlTagInnerClassInsertHandler implements InsertHandler<LookupElement> {
  public static final XmlTagInnerClassInsertHandler INSTANCE = new XmlTagInnerClassInsertHandler();

  @Override
  public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
    Project project = context.getProject();
    Editor editor = context.getEditor();
    Document document = editor.getDocument();

    // The invalid tag is already inserted. Replace with <view> tag.
    PsiElement invalidElement = context.getFile().findElementAt(context.getStartOffset());
    if (invalidElement == null) return;
    XmlTag invalidTag = PsiTreeUtil.getContextOfType(invalidElement, XmlTag.class, true);
    if (invalidTag == null) return;
    XmlElementDescriptor descriptor = invalidTag.getDescriptor();
    XmlAttributeDescriptor[] attributes = descriptor != null ? descriptor.getAttributesDescriptors(invalidTag) : null;
    if (!(item.getObject() instanceof PsiClass)) return;
    PsiClass psiClass = (PsiClass)item.getObject();

    String className = PackageClassConverter.getQualifiedName(psiClass);
    String replacement = "view class=\"" + className + "\"";
    int sOffset = invalidElement.getTextRange().getStartOffset();
    int eOffset = invalidElement.getTextRange().getEndOffset();
    document.deleteString(sOffset, eOffset);
    document.insertString(sOffset, replacement);
    sOffset += replacement.length();

    if (context.getCompletionChar() != Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
      context.setAddCompletionChar(false);
    }
    int caretOffset = -1;

    if (XmlUtil.getTokenOfType(invalidTag, XmlTokenType.XML_TAG_END) == null &&
        XmlUtil.getTokenOfType(invalidTag, XmlTokenType.XML_EMPTY_ELEMENT_END) == null) {

      int startIndent = findStartIndent(document, context.getStartOffset() - 2);
      if (attributes != null) {
        Pair<Integer, Integer> result = addRequiredAttributes(attributes, invalidTag, document, sOffset, startIndent + 4);
        sOffset = result.getFirst();
        caretOffset = result.getSecond();
      }
      String indent = StringUtil.repeatSymbol(' ', startIndent);
      String endTag = isLayout(psiClass) ? ">\n\n" + indent + "</view>" : "/>";
      document.insertString(sOffset, endTag);
      sOffset += endTag.length();
    }
    else if (context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();

      PsiElement otherTag = PsiTreeUtil.getParentOfType(context.getFile().findElementAt(context.getStartOffset()), XmlTag.class);
      PsiElement endTagStart = XmlUtil.getTokenOfType(otherTag, XmlTokenType.XML_END_TAG_START);

      if (endTagStart != null) {
        PsiElement sibling = endTagStart.getNextSibling();

        assert sibling != null;
        ASTNode node = sibling.getNode();
        assert node != null;
        if (node.getElementType() == XmlTokenType.XML_NAME) {
          sOffset = sibling.getTextRange().getStartOffset();
          eOffset = sibling.getTextRange().getEndOffset();

          document.deleteString(sOffset, eOffset);
          document.insertString(sOffset, ((XmlTag)otherTag).getName());
        }
      }
    }
    if (caretOffset < 0) {
      caretOffset = sOffset;
    }
    PsiDocumentManager.getInstance(project).commitDocument(document);

    editor.getCaretModel().moveToOffset(caretOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
  }

  @NotNull
  private static Pair<Integer, Integer> addRequiredAttributes(@NotNull XmlAttributeDescriptor[] attributes,
                                                              @NotNull XmlTag tag,
                                                              @NotNull Document document,
                                                              int offset,
                                                              int indentSize) {
    int cursorOffset = -1;
    String indent = StringUtil.repeatSymbol(' ', indentSize);
    for (XmlAttributeDescriptor attribute : attributes) {
      String attributeName = attribute.getName(tag);

      if (attribute.isRequired()) {
        String attrText = "\n" + indent + attributeName + "=\"\"";
        document.insertString(offset, attrText);
        offset += attrText.length();
        if (cursorOffset < 0) {
          cursorOffset = offset - 1;
        }
      }
    }
    return Pair.of(offset, cursorOffset);
  }

  private static boolean isLayout(@NotNull PsiClass psiClass) {
    Set<PsiClass> visited = new HashSet<>();
    while (psiClass != null && visited.add(psiClass)) {
      if ("android.view.ViewGroup".equals(psiClass.getQualifiedName())) {
        return true;
      }
      psiClass = psiClass.getSuperClass();
    }
    return false;
  }

  private static int findStartIndent(@NotNull Document document, int offset) {
    int indent = 0;
    CharSequence text = document.getCharsSequence();
    // TODO: handle tabs
    while (offset > 0 && text.charAt(offset) == ' ') {
      offset--;
      indent++;
    }
    return indent;
  }
}
