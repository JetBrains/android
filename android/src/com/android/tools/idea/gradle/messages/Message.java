/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.messages;

import com.intellij.pom.Navigatable;
import com.intellij.util.ui.MessageCategory;
import org.jetbrains.annotations.NotNull;

public class Message {
  private static final Navigatable NULL_NAVIGATABLE = new AbstractNavigatable() {};

  @NotNull private final String myGroupName;
  @NotNull private final Type myType;
  @NotNull private final String[] myText;
  @NotNull private final Navigatable myNavigatable;

  public Message(@NotNull String groupName, @NotNull Type type, @NotNull String... text) {
    this(groupName, type, NULL_NAVIGATABLE, text);
  }

  public Message(@NotNull String groupName, @NotNull Type type, @NotNull Navigatable navigatable, @NotNull String... text) {
    myType = type;
    myText = text;
    myGroupName = groupName;
    myNavigatable = navigatable;
  }

  @NotNull
  public String getGroupName() {
    return myGroupName;
  }

  @NotNull
  public Type getType() {
    return myType;
  }

  @NotNull
  public String[] getText() {
    return myText;
  }

  @NotNull
  public Navigatable getNavigatable() {
    return myNavigatable;
  }

  public enum Type {
    ERROR(MessageCategory.ERROR), INFO(MessageCategory.INFORMATION), SIMPLE(MessageCategory.SIMPLE), WARNING(MessageCategory.WARNING);

    private final int myValue;

    Type(int value) {
      myValue = value;
    }

    /**
     * @see com.intellij.util.ui.MessageCategory
     */
    public int getValue() {
      return myValue;
    }
  }
}
