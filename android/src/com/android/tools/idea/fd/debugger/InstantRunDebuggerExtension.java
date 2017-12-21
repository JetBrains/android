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

import com.intellij.debugger.engine.DebuggerExtension;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.sun.jdi.Field;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import org.jetbrains.annotations.NotNull;

public class InstantRunDebuggerExtension implements DebuggerExtension {

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

  @Override
  public StackFrameProxyImpl createProxy(@NotNull ThreadReferenceProxyImpl threadProxy, @NotNull StackFrame frame, int fromBottomIndex) {
    return new InstantRunStackFrame(threadProxy, frame, fromBottomIndex);
  }

  @Override
  public boolean filterStaticVariable(@NotNull ReferenceType refType, @NotNull Field field, boolean isSynthetic) {
    if (refType.name().endsWith(INSTANT_RUN_CLASS_SUFFIX)) {
      return true;
    }
    if (field.isStatic() && isSynthetic && INSTANT_RUN_CHANGE.equals(field.name())) {
      return true;
    }
    return false;
  }
}
