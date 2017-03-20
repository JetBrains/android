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

import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.util.PositionInFile;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NonNavigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SyncMessage {
  public static final String DEFAULT_GROUP = "Gradle Sync Issues";

  @NotNull private final String myGroup;
  @NotNull private final MessageType myType;
  @NotNull private final String[] myText;
  @NotNull private final Navigatable myNavigatable;

  @Nullable private final PositionInFile myPosition;

  @NotNull private final List<NotificationHyperlink> myQuickFixes = new ArrayList<>();

  public SyncMessage(@NotNull String group, @NotNull MessageType type, @NotNull String... text) {
    this(group, type, NonNavigatable.INSTANCE, text);
  }

  public SyncMessage(@NotNull Project project,
                     @NotNull String group,
                     @NotNull MessageType type,
                     @NotNull PositionInFile position,
                     @NotNull String... text) {
    this(group, type, new OpenFileDescriptor(project, position.file, position.line, position.column), text, position);
  }

  public SyncMessage(@NotNull String group, @NotNull MessageType type, @NotNull Navigatable navigatable, @NotNull String... text) {
    this(group, type, navigatable, text, null);
  }

  private SyncMessage(@NotNull String group,
                      @NotNull MessageType type,
                      @NotNull Navigatable navigatable,
                      @NotNull String[] text,
                      @Nullable PositionInFile position) {
    myGroup = group;
    myType = type;
    myNavigatable = navigatable;
    myText = text;
    myPosition = position;
  }

  @NotNull
  public String getGroup() {
    return myGroup;
  }

  @NotNull
  public MessageType getType() {
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
  public PositionInFile getPosition() {
    return myPosition;
  }

  public void add(@NotNull Collection<NotificationHyperlink> quickFixes) {
    for (NotificationHyperlink quickFix : quickFixes) {
      add(quickFix);
    }
  }

  public void add(@NotNull NotificationHyperlink quickFix) {
    myQuickFixes.add(quickFix);
  }

  @NotNull
  public List<NotificationHyperlink> getQuickFixes() {
    return ImmutableList.copyOf(myQuickFixes);
  }

  @Override
  @NotNull
  public String toString() {
    return Joiner.on('\n').join(myText);
  }
}
