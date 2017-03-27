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

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.BatchEvaluator;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.debugger.ui.tree.render.NodeRendererImpl;
import com.sun.jdi.IntegerType;
import com.sun.jdi.Type;
import com.sun.jdi.Value;

/**
 * Android uses integers in its various APIs rather than using specific types. In order to help avoid type errors, there are
 * a number of annotations that allow the IDE to identify the type of the integer that should be used. {@link AndroidTypedIntegerRenderer}
 * attempts to identify the type of an integer (by identify its annotation), and if such a type is available, then it displays it
 * appropriately. For example, integers representing color values are shown with an icon.
 */
public class AndroidTypedIntegerRenderer extends NodeRendererImpl {
  // NOTE: this name is used in NodeRendererSettings to give this priority over primitive renderer
  private static final String ID = "android.resource.renderer";

  public AndroidTypedIntegerRenderer() {
    // TODO: we need a good presentation name. This is the name that shows up when you right click on the value and click "Show as".
    // We can detect if something is a resource reference, RGB color integer or a flag (@IntDef)
    super("Android Typed Integer");
    myProperties.setEnabled(true);
  }

  @Override
  public String getUniqueId() {
    return ID;
  }

  @Override
  public boolean isApplicable(Type type) {
    if (!(type instanceof IntegerType)) {
      return false;
    }

    // only supported on Android VMs, https://youtrack.jetbrains.com/issue/IDEA-157010
    return type.virtualMachine().name().startsWith("Dalvik");
  }

  @Override
  public String calcLabel(ValueDescriptor descriptor, final EvaluationContext evaluationContext, final DescriptorLabelListener listener)
    throws EvaluateException {
    final Value value = descriptor.getValue();

    BatchEvaluator.getBatchEvaluator(evaluationContext.getDebugProcess())
      .invoke(new ResolveTypedIntegerCommand(descriptor, evaluationContext, value, listener));

    return value.toString();
  }
}
