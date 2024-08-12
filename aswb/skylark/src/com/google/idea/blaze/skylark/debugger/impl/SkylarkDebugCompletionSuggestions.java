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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.starlarkdebugging.StarlarkDebuggingProtos.Value;
import com.google.idea.blaze.skylark.debugger.impl.SkylarkDebugValue.Type;
import com.intellij.util.Function;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

class SkylarkDebugCompletionSuggestions {

  static SkylarkDebugCompletionSuggestions create(SkylarkSourcePosition position) {
    return new SkylarkDebugCompletionSuggestions(
        position.getBindings(),
        v -> position.frame.debugProcess.getChildren(position.frame.threadId, v));
  }

  private final ImmutableMap<String, Value> topLevelBindings;
  private final Function<Value, List<Value>> childProvider;

  private SkylarkDebugCompletionSuggestions(
      List<Value> topLevelBindings, Function<Value, List<Value>> childProvider) {
    Map<String, Value> bindingsMap = new HashMap<>();
    topLevelBindings.forEach(v -> bindingsMap.put(v.getLabel(), v));
    this.topLevelBindings = ImmutableMap.copyOf(bindingsMap);
    this.childProvider = childProvider;
  }

  /**
   * Returns all {@link Value}s relevant to the current context.
   *
   * <p>Currently only supports completion for a '.'-separated list of bindings.
   */
  List<Value> getCompletionValues(String evaluationString) {
    if (evaluationString.indexOf('.') == -1) {
      return ImmutableList.copyOf(topLevelBindings.values());
    }
    List<String> components = Splitter.on('.').splitToList(evaluationString);
    if (components.size() <= 1) {
      return ImmutableList.copyOf(topLevelBindings.values());
    }
    String first = components.get(0);
    Value currentContext = topLevelBindings.get(first);
    if (currentContext == null) {
      return ImmutableList.of();
    }
    // ignore the last component: that's the one we're matching autocomplete suggestions to
    for (int i = 1; i < components.size() - 1 && currentContext != null; i++) {
      currentContext = findChildWithLabel(currentContext, components.get(i));
    }
    return currentContext != null ? childProvider.fun(currentContext) : ImmutableList.of();
  }

  @Nullable
  private Value findChildWithLabel(Value parent, String label) {
    List<Value> children = childProvider.fun(parent);
    if (children == null) {
      return null;
    }
    return children
        .stream()
        .filter(v -> label.equals(v.getLabel()))
        .filter(v -> !ignoreValue(v))
        .findFirst()
        .orElse(null);
  }

  /**
   * Returns true if we shouldn't show autocomplete suggestions for children of this {@link Value}.
   *
   * <p>Currently returns false for collection-type values (list, dict, depset), for which
   * dot-separated components aren't valid.
   */
  private static boolean ignoreValue(Value value) {
    if (!value.getHasChildren()) {
      return true;
    }
    // TODO(brendandouglas): add array handling (autocomplete dict keys and array index)
    return SkylarkDebugValue.parseType(value) == Type.ARRAY;
  }
}
