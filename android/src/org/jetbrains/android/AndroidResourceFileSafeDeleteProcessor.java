package org.jetbrains.android;

import com.android.resources.ResourceFolderType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.safeDelete.NonCodeUsageSearchInfo;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegateBase;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourceFileSafeDeleteProcessor extends SafeDeleteProcessorDelegateBase {
  @Nullable
  @Override
  public Collection<? extends PsiElement> getElementsToSearch(@NotNull PsiElement element,
                                                              @Nullable Module module,
                                                              @NotNull Collection<PsiElement> allElementsToDelete) {
    return Collections.singletonList(element);
  }

  @Override
  public boolean handlesElement(PsiElement element) {
    if (!(element instanceof PsiFile)) {
      return false;
    }
    final AndroidFacet facet = AndroidFacet.getInstance(element);

    if (facet == null) {
      return false;
    }
    final VirtualFile vFile = ((PsiFile)element).getVirtualFile();
    final VirtualFile parent = vFile != null ? vFile.getParent() : null;
    final VirtualFile resDir = parent != null ? parent.getParent() : null;
    return resDir != null && facet.getLocalResourceManager().isResourceDir(resDir);
  }

  @Nullable
  @Override
  public NonCodeUsageSearchInfo findUsages(@NotNull PsiElement element, @NotNull PsiElement[] allElementsToDelete, @NotNull List<UsageInfo> result) {
    SafeDeleteProcessor.findGenericElementUsages(element, result, allElementsToDelete);

    if (element instanceof PsiFile) {
      final PsiField[] fields = AndroidResourceUtil.findResourceFieldsForFileResource((PsiFile)element, true);

      for (PsiField field : fields) {
        SafeDeleteProcessor.findGenericElementUsages(field, result, allElementsToDelete);
      }
    }
    return new NonCodeUsageSearchInfo(SafeDeleteProcessor.getDefaultInsideDeletedCondition(allElementsToDelete), element);
  }

  @Nullable
  @Override
  public Collection<PsiElement> getAdditionalElementsToDelete(@NotNull PsiElement element,
                                                              @NotNull Collection<PsiElement> allElementsToDelete,
                                                              boolean askUser) {
    if (allElementsToDelete.size() > 1) {
      // todo: support this case (we should ask once)
      return Collections.emptyList();
    }
    final AndroidFacet facet = AndroidFacet.getInstance(element);
    assert facet != null;

    final PsiFile file = (PsiFile)element;
    final VirtualFile vFile = file.getVirtualFile();
    assert vFile != null;
    final VirtualFile dir = vFile.getParent();
    assert dir != null;
    ResourceFolderType folderType = ResourceFolderType.getFolderType(dir.getName());

    if (folderType == null) {
      return Collections.emptyList();
    }
    final String type = folderType.getName();
    final String name = vFile.getName();
    final List<PsiFile> resourceFiles = facet.getLocalResourceManager().findResourceFiles(
      type, AndroidCommonUtils.getResourceName(type, name), true, false);
    final List<PsiElement> result = new ArrayList<PsiElement>();

    for (PsiFile resourceFile : resourceFiles) {
      if (!resourceFile.getManager().areElementsEquivalent(file, resourceFile) &&
          resourceFile.getName().equals(name)) {
        result.add(resourceFile);
      }
    }
    if (result.size() > 0 && askUser) {
      final int r = Messages.showDialog(element.getProject(), "Delete alternative resource files for other configurations?", "Delete",
                                        new String[]{Messages.YES_BUTTON, Messages.NO_BUTTON}, 1, Messages.getQuestionIcon());
      if (r != Messages.YES) {
        return Collections.emptyList();
      }
    }
    return result;
  }

  @Nullable
  @Override
  public Collection<String> findConflicts(@NotNull PsiElement element, @NotNull PsiElement[] allElementsToDelete) {
    return null;
  }

  @Nullable
  @Override
  public UsageInfo[] preprocessUsages(Project project, UsageInfo[] usages) {
    return usages;
  }

  @Override
  public void prepareForDeletion(PsiElement element) throws IncorrectOperationException {
  }

  @Override
  public boolean isToSearchInComments(PsiElement element) {
    return RefactoringSettings.getInstance().SAFE_DELETE_SEARCH_IN_COMMENTS;
  }

  @Override
  public boolean isToSearchForTextOccurrences(PsiElement element) {
    return RefactoringSettings.getInstance().SAFE_DELETE_SEARCH_IN_NON_JAVA;
  }

  @Override
  public void setToSearchInComments(PsiElement element, boolean enabled) {
    RefactoringSettings.getInstance().SAFE_DELETE_SEARCH_IN_COMMENTS = enabled;
  }

  @Override
  public void setToSearchForTextOccurrences(PsiElement element, boolean enabled) {
    RefactoringSettings.getInstance().SAFE_DELETE_SEARCH_IN_NON_JAVA = enabled;
  }
}
