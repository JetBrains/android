/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.streaming.core;

import com.intellij.util.messages.Topic;

/** Topic definition for closing the context menu advertisement. */
interface StreamingContextMenuAdvertisementCloser {
  @Topic.AppLevel
  Topic<StreamingContextMenuAdvertisementCloser> TOPIC =
      new Topic<>(StreamingContextMenuAdvertisementCloser.class, Topic.BroadcastDirection.TO_PARENT);

  /** Closes the context menu advertisement if currently shown. */
  void closeContextMenuAdvertisement();
}