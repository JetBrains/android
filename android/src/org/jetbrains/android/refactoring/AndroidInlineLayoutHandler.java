package org.jetbrains.android.refactoring;

import static org.jetbrains.android.dom.AndroidResourceDomFileDescription.isFileInResourceFolderType;

import com.android.resources.ResourceFolderType;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.lang.Language;
import com.intellij.lang.refactoring.InlineActionHandler;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import org.jetbrains.android.dom.converters.AndroidResourceReferenceBase;
import org.jetbrains.android.dom.wrappers.ResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.refactoring.errorreporter.HintBasedErrorReporter;
import org.jetbrains.android.refactoring.errorreporter.ProjectBasedErrorReporter;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.refactoring.errorreporter.ErrorReporter;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class AndroidInlineLayoutHandler extends InlineActionHandler {
  private static AndroidInlineTestConfig ourTestConfig;

  @TestOnly
  public static void setTestConfig(@Nullable AndroidInlineTestConfig testConfig) {
    ourTestConfig = testConfig;
  }

  @Override
  public boolean isEnabledForLanguage(Language l) {
    return l == XMLLanguage.INSTANCE;
  }

  @Override
  public boolean isEnabledOnElement(PsiElement element, @Nullable Editor editor) {
    return canInlineElementInEditor(element, editor);
  }

  @Override
  public boolean canInlineElement(PsiElement element) {
    if (element instanceof ResourceElementWrapper) {
      element = ((ResourceElementWrapper)element).getWrappedElement();
    }
    if (element instanceof XmlFile) {
      if (AndroidFacet.getInstance(element) == null ||
          ((XmlFile)element).getRootTag() == null) {
        return false;
      }
      return isFileInResourceFolderType((XmlFile)element, ResourceFolderType.LAYOUT);
    }
    return false;
  }

  @Override
  public boolean canInlineElementInEditor(PsiElement element, Editor editor) {
    return canInlineElement(element) || getLayoutUsageDataFromContext(editor) != null;
  }

  @Nullable
  private static LayoutUsageData getLayoutUsageDataFromContext(Editor editor) {
    if (editor == null) {
      return null;
    }
    final PsiElement element = PsiUtilBase.getElementAtCaret(editor);

    if (!(element instanceof XmlToken) ||
        AndroidFacet.getInstance(element) == null) {
      return null;
    }
    final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    return tag != null
           ? AndroidInlineUtil.getLayoutUsageData(tag)
           : null;
  }

  @Override
  public void inlineElement(Project project, Editor editor, PsiElement element) {
    if (element instanceof ResourceElementWrapper) {
      element = ((ResourceElementWrapper)element).getWrappedElement();
    }

    if (element instanceof XmlFile) {
      PsiElement usageElement = null;

      if (editor != null) {
        final PsiReference reference = TargetElementUtil.findReference(editor);

        if (reference != null) {
          usageElement = reference.getElement();
        }
      }
      AndroidInlineUtil.doInlineLayoutFile(project, (XmlFile)element, usageElement, ourTestConfig);
      return;
    }
    final LayoutUsageData usageData = getLayoutUsageDataFromContext(editor);
    assert usageData != null;
    final AndroidResourceReferenceBase ref = usageData.getReference();
    final PsiElement[] elements = ref.computeTargetElements();
    final ErrorReporter errorReporter = editor != null
                                        ? new HintBasedErrorReporter(editor)
                                        : new ProjectBasedErrorReporter(project);
    final String title = AndroidBundle.message("android.inline.layout.title");

    if (elements.length == 0) {
      final String resName = ref.getResourceValue().getResourceName();
      final String message = resName != null
                             ? "Cannot find layout '" + resName + "'"
                             : "Error: cannot find the layout";
      errorReporter.report(message, title);
      return;
    }

    if (elements.length > 1) {
      errorReporter.report("Error: unambiguous reference", title);
      return;
    }

    final PsiElement resolvedElement = elements[0];
    if (!(resolvedElement instanceof XmlFile)) {
      errorReporter.report("Cannot inline reference '" + ref.getValue() + "'", title);
      return;
    }
    AndroidInlineUtil.doInlineLayoutFile(project, (XmlFile)resolvedElement, ref.getElement(), ourTestConfig);
  }

  @Nullable
  @Override
  public String getActionName(PsiElement element) {
    return "Inline Android Layout";
  }
}
