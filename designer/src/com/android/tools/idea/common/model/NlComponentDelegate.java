/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.model;

import com.android.tools.idea.rendering.parsers.AttributeSnapshot;
import com.android.tools.idea.uibuilder.handlers.constraint.ComponentModification;
import com.android.utils.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Specify delegation operations for NlComponent
 */
public interface NlComponentDelegate {
  boolean handlesAttribute(@NotNull NlComponent component, @Nullable String namespace, @NotNull String attribute);

  boolean handlesAttributes(NlComponent component);

  boolean handlesApply(ComponentModification modification);

  boolean handlesCommit(ComponentModification modification);

  String getAttribute(@NotNull NlComponent component, @Nullable String namespace, @NotNull String attribute);

  List<AttributeSnapshot> getAttributes(NlComponent component);

  void apply(ComponentModification modification);

  void commit(ComponentModification modification);

  void setAttribute(NlComponent component, String namespace, String attribute, String value);

  void clearCaches();

  void willRemoveChild(@NotNull NlComponent component);

  boolean commitToMotionScene(Pair<String, String> key);
}
