package org.jetbrains.android.formatter;

import com.intellij.formatting.FormattingDocumentModel;
import com.intellij.formatting.Indent;
import com.intellij.formatting.WrapType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.formatter.xml.XmlPolicy;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author Eugene.Kudelevsky
*/
public class AndroidXmlPolicy extends XmlPolicy {
  private final AndroidXmlCodeStyleSettings.MySettings myCustomSettings;

  public AndroidXmlPolicy(CodeStyleSettings settings,
                          AndroidXmlCodeStyleSettings.MySettings customSettings,
                          FormattingDocumentModel documentModel) {
    super(settings, documentModel);
    myCustomSettings = customSettings;
  }

  @Override
  public WrapType getWrappingTypeForTagBegin(XmlTag tag) {
    final PsiElement element = getNextSiblingElement(tag);

    if (element instanceof XmlTag && insertLineBreakBeforeTag((XmlTag)element)) {
      return WrapType.NORMAL;
    }
    return super.getWrappingTypeForTagBegin(tag);
  }

  @Override
  public int getAttributesWrap() {
    return myCustomSettings.WRAP_ATTRIBUTES;
  }

  @Override
  public boolean insertLineBreakBeforeFirstAttribute(XmlAttribute attribute) {
    if (myCustomSettings.INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE) {
      // Even if setting for inserting line break before the first attribute, we want
      // _not_ to insert it if the first attribute would be namespace declaration.

      // However, we can't just check whether "attribute" is namespace declaration,
      // because for Android XMLs by default attributes are rearranged first,
      // and relying on the current first attribute leads to confusing results
      // as reported on http://b.android.com/196833

      // So, we just iterate through all the attribute and check whether there is
      // a namespace declaration among them.
      boolean hasNamespace = false;
      for (XmlAttribute xmlAttribute : attribute.getParent().getAttributes()) {
        if (xmlAttribute.isNamespaceDeclaration()) {
          hasNamespace = true;
          break;
        }
      }

      if (hasNamespace) {
        return false;
      }

      if (!attribute.isNamespaceDeclaration()) {
        return attribute.getParent().getAttributes().length > 1;
      }
    }
    return false;
  }

  @Override
  public boolean insertLineBreakAfterLastAttribute(XmlAttribute attribute) {
    if (!myCustomSettings.INSERT_LINE_BREAK_AFTER_LAST_ATTRIBUTE) {
      return false;
    }
    return attribute.getParent().getAttributes().length > 1;
  }

  @Nullable
  protected static PsiElement getPrevSiblingElement(@NotNull PsiElement element) {
    final PsiElement prev = element.getPrevSibling();
    ASTNode prevNode = SourceTreeToPsiMap.psiElementToTree(prev);

    while (prevNode != null && FormatterUtil.containsWhiteSpacesOnly(prevNode)) {
      prevNode = prevNode.getTreePrev();
    }
    return SourceTreeToPsiMap.treeElementToPsi(prevNode);
  }

  @Nullable
  protected static PsiElement getNextSiblingElement(@NotNull PsiElement element) {
    final PsiElement next = element.getNextSibling();
    ASTNode nextNode = SourceTreeToPsiMap.psiElementToTree(next);

    while (nextNode != null && FormatterUtil.containsWhiteSpacesOnly(nextNode)) {
      nextNode = nextNode.getTreeNext();
    }
    return SourceTreeToPsiMap.treeElementToPsi(nextNode);
  }

  @Override
  public int getBlankLinesBeforeTag(XmlTag xmlTag) {
    return 1;
  }

  @Override
  public Indent getTagEndIndent() {
    return null;
  }
}
