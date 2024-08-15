/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.skylark.debugger.impl;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.starlarkdebugging.StarlarkDebuggingProtos;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.XNamedValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.Icon;

/**
 * Converts a skylark binding to an {@link XNamedValue}, shown in the Variables view for a frame.
 */
class SkylarkDebugValue extends XNamedValue {

  static SkylarkDebugValue fromProto(
      SkylarkStackFrame frameContext, StarlarkDebuggingProtos.Value value) {
    return new SkylarkDebugValue(frameContext, value);
  }

  static Icon getIcon(StarlarkDebuggingProtos.Value value) {
    return parseType(value).icon;
  }

  static Type parseType(StarlarkDebuggingProtos.Value value) {
    String typeString = value.getType();
    if (ARRAY_TYPES.contains(typeString)) {
      return Type.ARRAY;
    }
    if (PRIMITIVE_TYPES.contains(typeString)) {
      return Type.PRIMITIVE;
    }
    if (FUNCTION_TYPES.contains(typeString)) {
      return Type.FUNCTION;
    }
    return Type.VALUE;
  }

  // Some initial mappings. TODO(brendandouglas): Expand these (and improve on the skylark-side)
  // TODO(brendandouglas): move this logic onto the server side?
  private static final ImmutableSet<String> ARRAY_TYPES = ImmutableSet.of("dict", "list", "depset");
  private static final ImmutableSet<String> PRIMITIVE_TYPES =
      ImmutableSet.of("bool", "string", "int");
  private static final ImmutableSet<String> FUNCTION_TYPES =
      ImmutableSet.of("function", "Provider");

  enum Type {
    ARRAY(AllIcons.Debugger.Db_array),
    PRIMITIVE(AllIcons.Debugger.Db_primitive),
    FUNCTION(AllIcons.Nodes.Function),
    VALUE(AllIcons.Debugger.Value);

    private final Icon icon;

    Type(Icon icon) {
      this.icon = icon;
    }
  }

  private SkylarkStackFrame frameContext;
  private final StarlarkDebuggingProtos.Value value;
  private final Type type;

  private SkylarkDebugValue(SkylarkStackFrame frameContext, StarlarkDebuggingProtos.Value value) {
    super(value.getLabel());
    this.value = value;
    this.type = parseType(value);
    this.frameContext = frameContext;
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }

  @Override
  public void computePresentation(XValueNode node, XValuePlace place) {
    node.setPresentation(
        type.icon,
        getTypeString(),
        truncateToMaxLength(value.getDescription()),
        /* hasChildren= */ value.getHasChildren());
    if (value.getDescription().length() > XValueNode.MAX_VALUE_LENGTH) {
      node.setFullValueEvaluator(
          new XFullValueEvaluator() {
            @Override
            public void startEvaluation(XFullValueEvaluationCallback callback) {
              callback.evaluated(value.getDescription());
            }
          });
    }
  }

  @Nullable
  private String getTypeString() {
    if (type == Type.PRIMITIVE) {
      return null;
    }
    return value.getType();
  }

  private static String truncateToMaxLength(String value) {
    return value.substring(0, Math.min(value.length(), XValueNode.MAX_VALUE_LENGTH));
  }

  @Override
  public void computeChildren(XCompositeNode node) {
    if (node.isObsolete()) {
      return;
    }
    if (!value.getHasChildren()) {
      node.addChildren(XValueChildrenList.EMPTY, true);
      return;
    }
    ApplicationManager.getApplication()
        .executeOnPooledThread(
            () -> {
              List<StarlarkDebuggingProtos.Value> response =
                  frameContext.debugProcess.getChildren(frameContext.threadId, value);
              if (response == null) {
                node.setErrorMessage("Error querying children.");
                return;
              }
              XValueChildrenList children = new XValueChildrenList(response.size());
              response.forEach(v -> children.add(SkylarkDebugValue.fromProto(frameContext, v)));
              node.addChildren(children, true);
            });
  }
}
