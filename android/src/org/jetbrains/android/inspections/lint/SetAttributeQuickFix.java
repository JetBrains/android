package org.jetbrains.android.inspections.lint;

import com.android.SdkConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.resourceManagers.SystemResourceManager;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SetAttributeQuickFix implements AndroidLintQuickFix {

  private final String myName;
  private final String myAttributeName;
  private final String myValue;
  private final String myNamespace;
  private final int myDot;
  private final int myMark;

  // 'null' value means asking
  public SetAttributeQuickFix(@NotNull String name, @NotNull String attributeName, @Nullable String value) {
    this(name, attributeName, SdkConstants.ANDROID_URI, value);
  }

  public SetAttributeQuickFix(@NotNull String name, @NotNull String attributeName, @Nullable String namespace, @Nullable String value) {
    this(name, attributeName, namespace, value,
         // The default was to select the whole text range
         value != null ? 0 : Integer.MIN_VALUE,
         value != null ? value.length() : Integer.MIN_VALUE);

  }
  public SetAttributeQuickFix(@NotNull String name, @NotNull String attributeName, @Nullable String namespace, @Nullable String value,
                              int dot, int mark) {
    super();
    myName = name;
    myAttributeName = attributeName;
    myValue = value;
    myNamespace = namespace;
    myDot = dot;
    myMark = mark;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class, false);

    if (tag == null) {
      return;
    }
    String value = myValue;

    if (value == null && context instanceof AndroidQuickfixContexts.DesignerContext) {
      value = askForAttributeValue(tag);
      if (value == null) {
        return;
      }
    }

    if (myNamespace != null) {
      XmlFile file = PsiTreeUtil.getParentOfType(tag, XmlFile.class);
      if (file != null) {
        AndroidResourceUtil.ensureNamespaceImported(file, myNamespace, null);
      }
    }

    final XmlAttribute attribute = ApplicationManager.getApplication().runWriteAction(
      (Computable<XmlAttribute>)() -> myNamespace != null
           ? tag.setAttribute(myAttributeName, myNamespace, "")
           : tag.setAttribute(myAttributeName, ""));

    if (attribute != null ) {
      if (value != null && !value.isEmpty()) {
        String finalValue = value;
        ApplicationManager.getApplication().runWriteAction(() -> attribute.setValue(finalValue));
      }
      if (context instanceof AndroidQuickfixContexts.EditorContext) {
        final Editor editor = ((AndroidQuickfixContexts.EditorContext)context).getEditor();
        final XmlAttributeValue valueElement = attribute.getValueElement();
        final TextRange valueTextRange = attribute.getValueTextRange();

        if (valueElement != null) {
          final int valueElementStart = valueElement.getTextRange().getStartOffset();
          if (myDot != Integer.MIN_VALUE) {
            int end = valueElementStart + valueTextRange.getStartOffset() + myDot;
            if (myMark != Integer.MIN_VALUE && myMark != myDot) {
              int start = valueElementStart + valueTextRange.getStartOffset() + myMark;
              editor.getCaretModel().moveToOffset(end);
              editor.getSelectionModel().setSelection(start, end);
            } else {
              editor.getCaretModel().moveToOffset(end);
            }
          }
        }
      }
    }
  }

  @Nullable
  private String askForAttributeValue(@NotNull PsiElement context) {
    final AndroidFacet facet = AndroidFacet.getInstance(context);
    final String message = "Specify value of attribute '" + myAttributeName + "'";
    final String title = "Set Attribute Value";

    if (facet != null) {
      final SystemResourceManager srm = ModuleResourceManagers.getInstance(facet).getSystemResourceManager();

      if (srm != null) {
        final AttributeDefinitions attrDefs = srm.getAttributeDefinitions();

        if (attrDefs != null) {
          final AttributeDefinition def = attrDefs.getAttrDefByName(myAttributeName);
          if (def != null) {
            final String[] variants = def.getValues();

            if (variants.length > 0) {
              return Messages.showEditableChooseDialog(message, title, Messages.getQuestionIcon(), variants, variants[0], null);
            }
          }
        }
      }
    }
    return Messages.showInputDialog(context.getProject(), message, title, Messages.getQuestionIcon());
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    if (myValue == null && contextType == AndroidQuickfixContexts.BatchContext.TYPE) {
      return false;
    }
    final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class, false);
    if (tag == null) {
      return false;
    }

    XmlAttribute attribute;
    if (myNamespace != null) {
      attribute = tag.getAttribute(myAttributeName, myNamespace);
    } else {
      attribute = tag.getAttribute(myAttributeName);
    }
    return attribute == null || !StringUtil.notNullize(myValue).equals(StringUtil.notNullize(attribute.getValue()));
  }
}
