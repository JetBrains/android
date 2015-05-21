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
package com.android.tools.idea.editors.hprof.descriptors;

import com.android.tools.idea.editors.hprof.jdi.FieldImpl;
import com.android.tools.perflib.heap.ArrayInstance;
import com.android.tools.perflib.heap.Field;
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class HprofFieldDescriptorImpl extends FieldDescriptorImpl {
  @NotNull protected Field myField;
  @Nullable protected Object myValueData;
  protected int myMemoryOrdering;

  public HprofFieldDescriptorImpl(@NotNull Project project, @NotNull Field field, @Nullable Object value, int memoryOrdering) {
    super(project, null, new FieldImpl(field, value));
    myField = field;
    myValueData = value;
    myMemoryOrdering = memoryOrdering;
  }

  public static void batchUpdateRepresentation(@NotNull final List<HprofFieldDescriptorImpl> descriptors,
                                               @NotNull DebuggerManagerThreadImpl debuggerManagerThread,
                                               @NotNull final SuspendContextImpl suspendContext) {
    debuggerManagerThread.invokeAndWait(new DebuggerCommandImpl() {
      @Override
      protected void action() throws Exception {
        for (HprofFieldDescriptorImpl descriptor : descriptors) {
          descriptor.updateRepresentation(new EvaluationContextImpl(suspendContext, null, descriptor.getValue()),
                                          new DescriptorLabelListener() {
                                            @Override
                                            public void labelChanged() {
                                            }
                                          });
        }
      }
    });
  }

  @Nullable
  public Object getValueData() {
    return myValueData;
  }

  @Override
  public abstract Value calcValue(EvaluationContextImpl evaluationContext);

  @Override
  public abstract Value getValue();

  @Override
  public PsiExpression getDescriptorEvaluation(DebuggerContext context) {
    return null;
  }

  @NotNull
  public Field getHprofField() {
    return myField;
  }

  @Override
  public boolean isArray() {
    return myValueData instanceof ArrayInstance;
  }

  @Override
  public boolean isDirty() {
    return false;
  }

  @Override
  public boolean isLvalue() {
    return false;
  }

  @Override
  public boolean isNull() {
    return myValueData == null;
  }

  public int getMemoryOrdering() {
    return myMemoryOrdering;
  }
}
