package org.jetbrains.android;

import static com.android.tools.idea.res.psi.ResourceReferencePsiElement.RESOURCE_CONTEXT_ELEMENT;

import com.android.annotations.concurrency.AnyThread;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.psi.ResourceReferencePsiElement;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments;

/**
 * When performing FindUsages or Refactor Renaming on Android Resources, further processing is performed:
 * <ul>
 *     <li>A {@link ResourceReferencePsiElement} is created based on the selected element.</li>
 *     <li>A `context` element is attached to the {@link ResourceReferencePsiElement} to later find the relevant Resource Repository.</li>
 *     <li>In some XML files, the caret is moved to resolve to the nearby Resource Reference
 * </ul>
 */
public class AndroidUsagesTargetProvider implements UsageTargetProvider {

  @AnyThread
  @Override
  public UsageTarget[] getTargets(@NotNull Editor editor, @NotNull PsiFile file) {
    PsiElement contextElement = file.findElementAt(editor.getCaretModel().getOffset());
    if (contextElement == null) {
      return UsageTarget.EMPTY_ARRAY;
    }
    PsiElement targetElement = null;
    try {
      targetElement = TargetElementUtil.findTargetElement(editor, TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    }
    catch (KotlinExceptionWithAttachments e) {
    }
    ResourceReferencePsiElement resourceReferencePsiElement = null;
    if (targetElement != null) {
      resourceReferencePsiElement = ResourceReferencePsiElement.create(targetElement);
    }
    if (resourceReferencePsiElement == null) {
      // The user has selected an element that does not resolve to a resource, check whether they have selected something nearby and if we
      // can assume the correct resource if any. The allowed matches are:
      // XmlTag of a values resource. eg. <col${caret}or name="... />
      // XmlValue of a values resource if it is not a reference to another resource. eg. <color name="foo">#12${caret}3456</color>
      resourceReferencePsiElement = IdeResourcesUtil.getResourceElementFromSurroundingValuesTag(contextElement);
    }
    if (resourceReferencePsiElement == null) {
      return UsageTarget.EMPTY_ARRAY;
    }
    resourceReferencePsiElement.putCopyableUserData(RESOURCE_CONTEXT_ELEMENT, contextElement);
    return new UsageTarget[]{new PsiElement2UsageTargetAdapter(resourceReferencePsiElement, true)};
  }

  @AnyThread
  @Nullable
  @Override
  public UsageTarget[] getTargets(@NotNull PsiElement psiElement) {
    if (psiElement instanceof ResourceReferencePsiElement) {
      return UsageTarget.EMPTY_ARRAY;
    }
    ResourceReferencePsiElement referencePsiElement = ResourceReferencePsiElement.create(psiElement);
    if (referencePsiElement == null) {
      return UsageTarget.EMPTY_ARRAY;
    }
    referencePsiElement.putCopyableUserData(RESOURCE_CONTEXT_ELEMENT, psiElement);
    return new UsageTarget[]{new PsiElement2UsageTargetAdapter(referencePsiElement, true)};
  }
}
