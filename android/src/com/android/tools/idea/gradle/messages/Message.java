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

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NonNavigatable;
import com.intellij.util.ui.MessageCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Message {
  @NotNull private final String myGroupName;
  @NotNull private final Type myType;
  @NotNull private final String[] myText;
  @NotNull private final Navigatable myNavigatable;

  @Nullable private final VirtualFile myFile;

  private final int myLine;
  private final int myColumn;

  public Message(@NotNull String groupName, @NotNull Type type, @NotNull String... text) {
    this(groupName, type, NonNavigatable.INSTANCE, text);
  }

  public Message(@NotNull Project project,
                 @NotNull String groupName,
                 @NotNull Type type,
                 @NotNull VirtualFile file,
                 int line,
                 int column,
                 @NotNull String... text) {
    this(groupName, type, new OpenFileDescriptor(project, file, line, column), file, line, column, text);
  }

  public Message(@NotNull String groupName, @NotNull Type type, @NotNull Navigatable navigatable, @NotNull String... text) {
    this(groupName, type, navigatable, null, -1, -1, text);
  }

  private Message(@NotNull String groupName, @NotNull Type type, @NotNull Navigatable navigatable, @Nullable VirtualFile file,
                  int line, int column, @NotNull String... text) {
    myType = type;
    myText = text;
    myGroupName = groupName;
    myNavigatable = navigatable;
    myFile = file;
    myLine = line;
    myColumn = column;
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

  @Nullable
  public VirtualFile getFile() {
    return myFile;
  }

  public int getLine() {
    return myLine;
  }

  public int getColumn() {
    return myColumn;
  }

  public enum Type {
    ERROR(MessageCategory.ERROR), INFO(MessageCategory.INFORMATION), SIMPLE(MessageCategory.SIMPLE), WARNING(MessageCategory.WARNING);

    private final int myValue;

    Type(int value) {
      myValue = value;
    }

    /**
     * @see MessageCategory
     */
    public int getValue() {
      return myValue;
    }

    @Nullable
    public static Type find(@NotNull String value) {
      for (Type type : values()) {
        if (type.name().equalsIgnoreCase(value)) {
          return type;
        }
      }
      return null;
    }
  }
}
