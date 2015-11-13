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
package com.android.tools.idea.fd;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

/**
 * When running with instant run, some of the internal aspects of the objects
 * are not user friendly. This class provides a different implementation of the
 * stack frame proxy that hides all these internal details.
 */
public class InstantRunStackFrame extends StackFrameProxyImpl {

  /**
   * When an instant method is edited, the new version will translate
   * the "this" variable to an argument called "$this". We use
   * this indication to readjust the variable names.
   */
  public static String INSTANT_RUN_THIS = "$this";
  /**
   * When a class is modified, the new implementation has this suffix.
   */
  public static String INSTANT_RUN_CLASS_SUFFIX = "$override";
  /**
   * The static variable that contains the patch of the class.
   */
  public static String INSTANT_RUN_CHANGE = "$change";
  /**
   * A flag on a patch class that tells whether it's obsolete.
   */
  public static String INSTANT_RUN_CHANGE_OBSOLETE = "$obsolete";
  /**
   * When a class is enhanced to support instant run, the redirection
   * part of the method as a special line number assigned to it.
   */
  public static int REDIRECTION_LINE_NUMBER = 0;

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
        LocalVariable newThis = getStackFrame().visibleVariableByName(INSTANT_RUN_THIS);
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
    else if (getStackFrame().location().declaringType().name().endsWith(INSTANT_RUN_CLASS_SUFFIX)) {
      // Avoid seeing the "$change" object's "this".
      return null;
    }
    return ref;
  }


  @Override
  public boolean isHiddenVariable(@NotNull  String name) {
    return INSTANT_RUN_THIS.equals(name);
  }

  @Override
  public boolean isObsolete() throws EvaluateException {
    if (isRedirectionFrame()) {
      return super.isObsolete();
    }
    else {
      ReferenceType clazz = getStackFrame().location().method().declaringType();
      if (!clazz.name().endsWith(INSTANT_RUN_CLASS_SUFFIX)) {
        Field field = clazz.fieldByName(INSTANT_RUN_CHANGE);
        if (field != null && field.isSynthetic() && field.isStatic()) {
          // This is one of ours and can be patched.
          return clazz.getValue(field) != null;
        }
      }
      else {
        // We are now in a patch, this patch can still be obsolete if other patch was applied after it.
        // Here, we use the special flag on the patch class:
        Field field = clazz.fieldByName(INSTANT_RUN_CHANGE_OBSOLETE);
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
    return getStackFrame().location().lineNumber() == REDIRECTION_LINE_NUMBER;
  }
}
