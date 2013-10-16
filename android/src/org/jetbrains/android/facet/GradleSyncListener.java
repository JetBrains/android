/*
 * Copyright (C) 2013 The Android Open Source Project
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
package org.jetbrains.android.facet;

import org.jetbrains.annotations.NotNull;

/**
 * Interface implemented by listeners on Gradle sync events.
 * To register for notification, call {@link AndroidFacet#addListener(GradleSyncListener)}
 */
public interface GradleSyncListener {
  /**
   * A gradle sync has completed (as well as source generation)
   *
   * @param facet the facet affected by the sync
   * @param success whether the gradle sync was successful
   */
  void performedGradleSync(@NotNull AndroidFacet facet, boolean success);
}
