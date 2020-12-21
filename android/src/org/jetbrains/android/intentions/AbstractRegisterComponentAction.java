/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.intentions;

import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Abstract class to cut of boilerplate for intention actions that register different kinds of
 * (Android application) components in the AndroidManifest.xml file.
 */
public abstract class AbstractRegisterComponentAction extends AbstractIntentionAction implements HighPriorityAction {
  /**
   * Specialized analogue of {@link com.intellij.codeInsight.intention.IntentionAction#isAvailable(Project, Editor, PsiFile)}
   * @param psiClass destination class for which availability of intention is checked
   * @param manifest DOM of AndroidManifest.xml file
   * @return whether the intent action should be available
   */
  abstract boolean isAvailable(@NotNull PsiClass psiClass, @NotNull Manifest manifest);

  /**
   * Specialized analogue of {@link com.intellij.codeInsight.intention.IntentionAction#invoke(Project, Editor, PsiFile)}
   * @param psiClass context class where intent action has been invoked
   * @param manifest DOM of AndroidManifest.xml file for manifest manipulation
   */
  abstract void invoke(@NotNull PsiClass psiClass, @NotNull Manifest manifest);

  @Override
  public final void invoke(@NotNull Project project, @Nullable Editor editor, @Nullable PsiFile file) throws IncorrectOperationException {
    final PsiClass psiClass = editor == null ? null : extractClass(editor, file);
    if (psiClass == null) {
      return;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(file);
    final Manifest manifest = facet == null ? null : Manifest.getMainManifest(facet);
    if (manifest == null) {
      return;
    }

    final XmlElement element = manifest.getXmlElement();
    final PsiFile manifestFile = element == null ? null : element.getContainingFile();
    if (manifestFile == null) {
      return;
    }

    WriteCommandAction.writeCommandAction(project, file, manifestFile).run(()-> invoke(psiClass, manifest));
  }

  @Override
  public final boolean isAvailable(@NotNull Project project, @Nullable Editor editor, @Nullable PsiFile file) {
    final PsiClass psiClass = editor == null ? null : extractClass(editor, file);
    if (psiClass == null) {
      return false;
    }

    final int elementOffset = editor.getCaretModel().getOffset();

    // Checking if we're in class "header", or, more specifically, before the "{" opening the class body.
    // Other class intentions, such as CreateTestAction, use the same behaviour to determine whether they
    // should be active.
    final PsiElement lBrace = psiClass.getLBrace();
    if (lBrace == null || elementOffset >= lBrace.getTextOffset()) {
      return false;
    }

    // Check whether the class has a qualified name and is public (thus, can be used as an activity in the manifest)
    final PsiModifierList modifierList = psiClass.getModifierList();
    if (psiClass.getQualifiedName() == null || modifierList == null || !modifierList.hasExplicitModifier(PsiModifier.PUBLIC)) {
      return false;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      return false;
    }

    final Manifest manifest = Manifest.getMainManifest(facet);
    if (manifest == null) {
      return false;
    }

    return isAvailable(psiClass, manifest);
  }

  @Nullable/*if the cursor in the editor is not inside a class definition*/
  public static PsiClass extractClass(@Nullable Editor editor, @Nullable PsiFile file) {
    if (editor == null || file == null) {
      return null;
    }

    final int elementOffset = editor.getCaretModel().getOffset();
    final PsiElement element = file.findElementAt(elementOffset);
    if (element == null) {
      return null;
    }

    return PsiTreeUtil.getParentOfType(element, PsiClass.class);
  }
}
