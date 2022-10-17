/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.project.messages;

import static com.android.builder.model.SyncIssue.SEVERITY_ERROR;

import com.android.ide.common.blame.Message;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.util.ui.MessageCategory;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum MessageType {
  SIMPLE(1), INFO(3), ERROR(4), WARNING(5);

  private final int myCategoryCode;

  MessageType(@MagicConstant(valuesFromClass = MessageCategory.class) int categoryCode) {
    myCategoryCode = categoryCode;
  }

  @Nullable
  public static MessageType findByName(@NotNull String name) {
    for (MessageType type : values()) {
      if (type.name().equalsIgnoreCase(name)) {
        return type;
      }
    }
    return null;
  }

  @NotNull
  public static MessageType findMatching(@NotNull Message.Kind kind) {
    String name = kind.name();
    MessageType type = findByName(name);
    return type != null ? type : INFO;
  }

  @NotNull
  public NotificationCategory convertToCategory() {
    return NotificationCategory.convert(myCategoryCode);
  }
}
