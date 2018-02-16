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
package com.android.tools.profilers;

import com.android.tools.profiler.proto.Common;
import org.jetbrains.annotations.NotNull;

/**
 * A SessionArtifact is any session-related entity that should show up in the sessions panel as its own row. (e.g. A session, a memory
 * heap dump, a CPU capture, etc).
 */
public interface SessionArtifact {
  /**
   * @return the {@link Common.Session} instance that this artifact belongs to.
   */
  @NotNull
  Common.Session getSession();

  /**
   * @return The name used for display.
   */
  @NotNull
  String getName();

  /**
   * @return The timestamp when this artifact was created/took place.
   */
  long getTimestampNs();

  /**
   * The {@link SessionArtifact} has been selected. Perform the corresponding navigation and selection change in the model.
   */
  void onSelect();
}
