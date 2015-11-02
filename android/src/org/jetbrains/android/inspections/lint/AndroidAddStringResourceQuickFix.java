package org.jetbrains.android.inspections.lint;

import com.android.resources.ResourceType;
import com.google.common.base.CharMatcher;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.intentions.AndroidAddStringResourceAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static com.google.common.base.CharMatcher.inRange;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidAddStringResourceQuickFix extends AndroidAddStringResourceAction {
  private static final CharMatcher DISALLOWED_CHARS = inRange('a', 'z').or(inRange('A', 'Z')).or(inRange('0', '9')).negate();
  private final PsiElement myStartElement;

  public AndroidAddStringResourceQuickFix(@NotNull PsiElement startElement) {
    myStartElement = startElement;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myStartElement.isValid()) {
      return false;
    }
    final XmlAttributeValue value = getAttributeValue(myStartElement);
    return value != null && getStringLiteralValue(project, value, file, ResourceType.STRING) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    String defaultName = null;
    final PsiElement parent = myStartElement.getParent();
    if (parent instanceof XmlAttribute) {
      final String value = ((XmlAttribute)parent).getValue();
      if (value != null) {
        defaultName = buildResourceName(value);
      }
    }
    invokeIntention(project, editor, file, defaultName);
  }

  @NotNull
  static String buildResourceName(@NotNull String value) {
    final String result = DISALLOWED_CHARS.trimAndCollapseFrom(value, '_').toLowerCase(Locale.US);
    if (result.length() > 0 && CharMatcher.JAVA_DIGIT.matches(result.charAt(0))) {
      return "_" + result;
    }
    return result;
  }

  public void invokeIntention(Project project, Editor editor, PsiFile file, @Nullable String resName) {
    final XmlAttributeValue attributeValue = getAttributeValue(myStartElement);
    if (attributeValue != null) {
      doInvoke(project, editor, file, resName, attributeValue, ResourceType.STRING);
    }
  }

  @Nullable
  private static XmlAttributeValue getAttributeValue(@NotNull PsiElement element) {
    final XmlAttribute attribute = PsiTreeUtil.getParentOfType(element, XmlAttribute.class);
    return attribute != null ? attribute.getValueElement() : null;
  }
}
