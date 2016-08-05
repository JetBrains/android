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
package com.android.tools.idea.fd.debugger;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * When running with instant run, some of the internal aspects of the objects
 * are not user friendly. This class provides a different implementation of the
 * stack frame proxy that hides all these internal details.
 */
public class InstantRunStackFrame extends StackFrameProxyImpl {

  private ObjectReference myThisReference;

  public InstantRunStackFrame(@NotNull ThreadReferenceProxyImpl threadProxy, @NotNull StackFrame frame, int fromBottomIndex) {
    super(threadProxy, frame, fromBottomIndex);
  }

  @Override
  public ObjectReference thisObject() throws EvaluateException {
    if (myThisReference != null) {
      return myThisReference;
    }
    ObjectReference ref = super.thisObject();
    if (ref == null) {
      // We might be in an edited method
      try {
        LocalVariable newThis = getStackFrame().visibleVariableByName(InstantRunDebuggerExtension.INSTANT_RUN_THIS);
        if (newThis != null) {
          Value value = getStackFrame().getValue(newThis);
          if (value instanceof ObjectReference) {
            myThisReference = (ObjectReference)value;
            return myThisReference;
          }
        }
      }
      catch (AbsentInformationException e1) {
        // ignore
      }
    }
    else if (getStackFrame().location().declaringType().name().endsWith(InstantRunDebuggerExtension.INSTANT_RUN_CLASS_SUFFIX)) {
      // Avoid seeing the "$change" object's "this".
      return null;
    }
    return ref;
  }


  @Override
  public boolean isHiddenVariable(@NotNull String name) {
    return super.isHiddenVariable(name) ||
           InstantRunDebuggerExtension.INSTANT_RUN_THIS.equals(name);
  }

  @Override
  public boolean isObsolete() throws EvaluateException {
    if (isRedirectionFrame()) {
      return super.isObsolete();
    }
    else {
      ReferenceType clazz = getStackFrame().location().method().declaringType();
      if (!clazz.name().endsWith(InstantRunDebuggerExtension.INSTANT_RUN_CLASS_SUFFIX)) {
        Field field = clazz.fieldByName(InstantRunDebuggerExtension.INSTANT_RUN_CHANGE);
        if (field != null && field.isSynthetic() && field.isStatic()) {
          // This is one of ours and can be patched.
          return clazz.getValue(field) != null;
        }
      }
      else {
        // We are now in a patch, this patch can still be obsolete if other patch was applied after it.
        // Here, we use the special flag on the patch class:
        Field field = clazz.fieldByName(InstantRunDebuggerExtension.INSTANT_RUN_CHANGE_OBSOLETE);
        if (field != null) {
          Value value = clazz.getValue(field);
          if (value instanceof BooleanValue) {
            return ((BooleanValue)value).value();
          }
        }
      }
    }
    return false;
  }

  private boolean isRedirectionFrame() throws EvaluateException {
    return getStackFrame().location().lineNumber() == InstantRunDebuggerExtension.REDIRECTION_LINE_NUMBER;
  }

  @Nullable
  @Override
  public ReferenceType getDeclaringType() throws EvaluateException {
    ReferenceType type = super.getDeclaringType();

    if (type != null && type.name().endsWith(InstantRunDebuggerExtension.INSTANT_RUN_CLASS_SUFFIX)) {
      if (getFrameIndex() < threadProxy().frameCount()) {
        StackFrameProxyImpl caller = threadProxy().frame(getFrameIndex() + 1);
        if (caller != null) {
          return caller.getDeclaringType();
        }
      }
    }

    return type;
  }

  @Override
  protected void clearCaches() {
    super.clearCaches();
    myThisReference = null;
  }
}
