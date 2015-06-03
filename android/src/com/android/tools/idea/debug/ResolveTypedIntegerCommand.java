/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.debug;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.ui.tree.FieldDescriptor;
import com.intellij.debugger.ui.tree.LocalVariableDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.debugger.ui.tree.render.ToStringCommand;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

public class ResolveTypedIntegerCommand extends ToStringCommand {
  private final ValueDescriptor myDescriptor;
  private final EvaluationContext myEvaluationContext;
  private final Value myValue;
  private final DescriptorLabelListener myListener;
  AnnotationsRenderer.Result myResult;

  public ResolveTypedIntegerCommand(@NotNull ValueDescriptor descriptor,
                                    @NotNull EvaluationContext evaluationContext,
                                    @NotNull Value value,
                                    @NotNull DescriptorLabelListener listener) {
    super(evaluationContext, value);
    myDescriptor = descriptor;
    myEvaluationContext = evaluationContext;
    myValue = value;
    myListener = listener;
  }

  @Override
  public void action() {
    // TODO: see if it is possible to cache this evaluation
    // if (myIsEvaluated) return;

    DebugProcess debugProcess = myEvaluationContext.getDebugProcess();
    if (!(debugProcess instanceof DebugProcessImpl)) {
      return;
    }

    final DebuggerContextImpl debuggerContext = ((DebugProcessImpl)debugProcess).getDebuggerContext();
    PsiAnnotation annotation = ApplicationManager.getApplication().runReadAction(new Computable<PsiAnnotation>() {
      @Override
      public PsiAnnotation compute() {
        PsiElement context = PositionUtil.getContextElement(debuggerContext);
        if (context == null) {
          return null;
        }

        if (myDescriptor instanceof LocalVariableDescriptor) {
          return AndroidResolveHelper.getAnnotationForLocal(context, myDescriptor.getName());
        }
        else if (myDescriptor instanceof FieldDescriptor) {
          String className = ((FieldDescriptor)myDescriptor).getField().declaringType().name();
          return AndroidResolveHelper.getAnnotationForField(context, className, myDescriptor.getName());
        }
        else {
          return null;
        }
      }
    });

    if (annotation != null) {
      ResourceIdResolver resolver = ServiceManager.getService(myEvaluationContext.getProject(), ResourceIdResolver.class);
      DynamicResourceIdResolver resolver1 = new DynamicResourceIdResolver(myEvaluationContext, resolver);
      myResult = AnnotationsRenderer.render(resolver1, annotation, ((IntegerValue)myValue).value());
    }

    evaluationResult("");
  }

  @Override
  public void evaluationResult(String message) {
    if (myResult == null) {
      return;
    }

    myDescriptor.setValueLabel(myResult.label);
    if (myResult.icon != null) {
      myDescriptor.setValueIcon(myResult.icon);
    }
    myListener.labelChanged();
  }

  @Override
  public void evaluationError(String message) {
  }
}
