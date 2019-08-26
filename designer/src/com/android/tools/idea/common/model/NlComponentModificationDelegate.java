/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.uibuilder.handlers.constraint.ComponentModification;
import com.android.utils.Pair;
import java.util.HashMap;
import org.jetbrains.annotations.NotNull;

/**
 * The modification delegate allows interception of init/apply/commit
 * operations by ViewGroupHandlers (e.g. MotionLayout)
 */
public interface NlComponentModificationDelegate {
  void initializeModification(@NotNull ComponentModification modification);
  void applyModification(@NotNull ComponentModification modification);
  void commitModification(@NotNull ComponentModification modification);

  void setAttribute(HashMap<Pair<String, String>, String> attributes, String namespace, String name, String value);
  String getAttribute(HashMap<Pair<String, String>, String> attributes, String namespace, String attribute);
}
