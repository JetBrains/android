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

import static com.android.tools.idea.common.model.NlTreeReaderKt.findAttributeByPsi;

import com.android.ide.common.rendering.api.ResourceReference;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.lint.common.AndroidLintInspectionBase;
import com.android.tools.idea.lint.common.LintEditorResult;
import com.android.tools.idea.lint.common.LintExternalAnnotator;
import com.android.tools.idea.lint.common.LintProblemData;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.UIUtil;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
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
  private final Executor myExecutor;
  private final AtomicReference<Disposable> annotationComputation = new AtomicReference<>();

  /**
   * @param surface the surface to add the lint annotations to. This class will keep a {@link WeakReference} to the
   *                surface, so it won't stop it from being disposed.
   */
  public ModelLintIssueAnnotator(@NotNull DesignSurface<?> surface) {
    this(surface, AppExecutorUtil.getAppExecutorService());
  }

  public ModelLintIssueAnnotator(@NotNull DesignSurface<?> surface, @NotNull Executor executor) {
    mySurfaceRef = new WeakReference<>(surface);
    myExecutor = executor;
  }

  public void annotateRenderInformationToLint(@NotNull NlModel model) {
    final DesignSurface<?> surface = mySurfaceRef.get();
    if (surface == null || model.isDisposed()) {
      // The surface is gone or the model is already disposed, no need to keep going
      return;
    }
    Disposable computationToken = Disposer.newDisposable();
    if (!Disposer.tryRegister(model, computationToken)) {
      // The model has been disposed, no need to keep going
      return;
    }
    Disposable oldComputation = annotationComputation.getAndSet(computationToken);
    if (oldComputation != null) {
      Disposer.dispose(oldComputation);
    }
    ReadAction.nonBlocking(() -> {
        if (annotationComputation.get() != computationToken) {
          return null;
        }
        return getAnnotations(model);
      })
      .finishOnUiThread(ModalityState.defaultModalityState(),
                        lintAnnotationsModel -> updateLintAnnotationsModelToSurface(surface, model, lintAnnotationsModel))
      .expireWith(computationToken)
      .submit(myExecutor)
      .onProcessed(lintAnnotationsModel -> {
        if (annotationComputation.compareAndSet(computationToken, null)) {
          Disposer.dispose(computationToken);
        }
      });
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

      NlComponent component = model.getTreeReader().findViewByPsi(startElement);
      if (component == null) {
        continue;
      }

      ResourceReference attribute = findAttributeByPsi(startElement);
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

      Incident incident = problemData.getIncident();
      lintModel.addIssue(component, attributeKey, incident, issue, problemData.getMessage(), inspection, level,
                         startElementPointer, endElementPointer, problemData.getQuickfixData());
    }

    return lintModel;
  }
}
