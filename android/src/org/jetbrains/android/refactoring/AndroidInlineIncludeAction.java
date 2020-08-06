package org.jetbrains.android.refactoring;

import static org.jetbrains.android.dom.AndroidResourceDomFileDescription.isFileInResourceFolderType;

import com.android.resources.ResourceFolderType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.RefactoringBundle;
import org.jetbrains.android.dom.converters.AndroidResourceReferenceBase;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public class AndroidInlineIncludeAction extends AndroidBaseXmlRefactoringAction {
  private final AndroidInlineTestConfig myTestConfig;

  public static final String ACTION_ID = "AndroidInlineIncludeAction";

  @SuppressWarnings("UnusedDeclaration")
  public AndroidInlineIncludeAction() {
    myTestConfig = null;
  }

  @TestOnly
  public AndroidInlineIncludeAction(AndroidInlineTestConfig testConfig) {
    myTestConfig = testConfig;
  }

  @Override
  protected void doRefactorForTags(@NotNull Project project, @NotNull final XmlTag[] tags) {
    assert tags.length == 1;
    final XmlTag tag = tags[0];

    final PsiFile file = tag.getContainingFile();
    if (file == null) {
      return;
    }
    final LayoutUsageData usageData = AndroidInlineUtil.getLayoutUsageData(tag);

    if (usageData == null) {
      return;
    }
    final AndroidResourceReferenceBase reference = usageData.getReference();
    final String title = AndroidBundle.message("android.inline.layout.title");
    final PsiElement[] resolvedElements = reference.computeTargetElements();

    if (resolvedElements.length == 0) {
      AndroidUtils.reportError(project, "Cannot find layout by reference '" + reference.getValue() + "'", title);
      return;
    }

    if (resolvedElements.length > 1) {
      AndroidUtils.reportError(project, RefactoringBundle.getCannotRefactorMessage("Unambiguous layout reference."), title);
      return;
    }
    final PsiElement resolvedElement = resolvedElements[0];

    if (!(resolvedElement instanceof XmlFile)) {
      AndroidUtils.reportError(project, "Cannot find layout by reference '" + reference.getValue() + "'", title);
      return;
    }
    AndroidInlineUtil.doInlineLayoutFile(project, (XmlFile)resolvedElement, usageData.getReference().getElement(), myTestConfig);
  }

  @Override
  protected boolean isEnabledForTags(@NotNull XmlTag[] tags) {
    return tags.length == 1 && AndroidInlineUtil.getLayoutUsageData(tags[0]) != null;
  }

  @Override
  protected boolean isMyFile(PsiFile file) {
    return file instanceof XmlFile && isFileInResourceFolderType((XmlFile)file, ResourceFolderType.LAYOUT);
  }
}
