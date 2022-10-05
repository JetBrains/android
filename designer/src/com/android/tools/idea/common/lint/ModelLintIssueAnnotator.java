/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.lint;

import com.android.ide.common.rendering.api.ResourceReference;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.lint.common.AndroidLintInspectionBase;
import com.android.tools.idea.lint.common.LintEditorResult;
import com.android.tools.idea.lint.common.LintExternalAnnotator;
import com.android.tools.idea.lint.common.LintProblemData;
import com.android.tools.lint.detector.api.Issue;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ui.UIUtil;
import java.lang.ref.WeakReference;
import kotlin.Pair;
import net.jcip.annotations.GuardedBy;
import org.jetbrains.annotations.NotNull;

/**
 * Annotate the lint issue information by using the rendered {@link NlModel}.
 *
 * @see LintAnnotationsModel
 */
public class ModelLintIssueAnnotator {
  private final WeakReference<DesignSurface<?>> mySurfaceRef;

  private final Object myRunningTaskLock = new Object();
  @GuardedBy("myRunningTaskLock")
  private Runnable myRunningTask;

  /**
   * @param surface the surface to add the lint annotations to. This class will keep a {@link WeakReference} to the
   *                surface, so it won't stop it from being disposed.
   */
  public ModelLintIssueAnnotator(@NotNull DesignSurface<?> surface) {
    mySurfaceRef = new WeakReference<>(surface);
  }

  public void annotateRenderInformationToLint(@NotNull NlModel model) {
    final DesignSurface<?> surface = mySurfaceRef.get();
    if (surface == null) {
      // The surface is gone, no need to keep going
      return;
    }
    Runnable annotatingTask = () -> {
      SceneView sceneView = surface.getFocusedSceneView();
      if (sceneView == null) {
        return;
      }
      LintAnnotationsModel lintAnnotationsModel =
        ApplicationManager.getApplication().runReadAction((Computable<LintAnnotationsModel>)() -> getAnnotations(model));
      synchronized (myRunningTaskLock) {
        myRunningTask = null;
      }
      UIUtil.invokeLaterIfNeeded(() -> updateLintAnnotationsModelToSurface(surface, model, lintAnnotationsModel));
    };
    synchronized (myRunningTaskLock) {
      if (myRunningTask == null) {
        myRunningTask = annotatingTask;
        ApplicationManager.getApplication().executeOnPooledThread(annotatingTask);
      }
    }
  }

  private static void updateLintAnnotationsModelToSurface(@NotNull DesignSurface<?> surface,
                                                          @NotNull NlModel model,
                                                          @NotNull LintAnnotationsModel annotationsModel) {
    model.setLintAnnotationsModel(annotationsModel);
    surface.setLintAnnotationsModel(annotationsModel);
    // Ensure that the layers are repainted to reflect the latest model
    // (updating the lint annotations associated with a model doesn't actually rev the model
    // version.)
    surface.repaint();
  }

  @NotNull
  private static LintAnnotationsModel getAnnotations(@NotNull NlModel model) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    LintAnnotationsModel lintModel = new LintAnnotationsModel();

    XmlFile xmlFile = model.getFile();

    LintExternalAnnotator annotator = new LintExternalAnnotator();
    LintEditorResult lintResult = annotator.collectInformation(xmlFile);
    // TODO: Separate analytics mode here?
    if (lintResult != null) {
      lintResult = annotator.doAnnotate(lintResult);
    }

    if (lintResult == null) {
      return lintModel;
    }

    for (LintProblemData problemData : lintResult.getProblems()) {
      TextRange range = problemData.getTextRange();
      final PsiElement startElement = xmlFile.findElementAt(range.getStartOffset());
      final PsiElement endElement = xmlFile.findElementAt(range.getEndOffset());
      if (startElement == null || endElement == null) {
        continue;
      }

      NlComponent component = model.findViewByPsi(startElement);
      if (component == null) {
        continue;
      }

      ResourceReference attribute = model.findAttributeByPsi(startElement);
      AttributeKey attributeKey =
        attribute != null ? new AttributeKey(component, attribute.getNamespace().getXmlNamespaceUri(), attribute.getName()) : null;

      Issue issue = problemData.getIssue();
      Pair<AndroidLintInspectionBase, HighlightDisplayLevel> pair =
        LintExternalAnnotator.Companion.getHighlightLevelAndInspection(xmlFile.getProject(), issue, xmlFile);
      if (pair == null) {
        continue;
      }

      AndroidLintInspectionBase inspection = pair.getFirst();
      if (inspection == null) {
        continue;
      }

      HighlightDisplayLevel level = pair.getSecond();
      HighlightDisplayKey key = HighlightDisplayKey.find(inspection.getShortName());
      if (key == null) {
        continue;
      }

      SmartPsiElementPointer<PsiElement> startElementPointer =
        SmartPointerManager.getInstance(model.getProject()).createSmartPsiElementPointer(startElement, xmlFile);
      SmartPsiElementPointer<PsiElement> endElementPointer =
        SmartPointerManager.getInstance(model.getProject()).createSmartPsiElementPointer(endElement, xmlFile);

      lintModel.addIssue(component, attributeKey, issue, problemData.getMessage(), inspection, level,
                         startElementPointer, endElementPointer, problemData.getQuickfixData());
    }

    return lintModel;
  }
}
