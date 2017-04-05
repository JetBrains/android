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
package com.android.tools.idea.model;

import com.android.annotations.Nullable;
import com.android.builder.model.SyncIssue;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * Creates a deep copy of {@link SyncIssue}.
 *
 * @see IdeAndroidProject
 */
public class IdeSyncIssue implements SyncIssue, Serializable {
  @NotNull private final String myMessage;
  @Nullable private final String myData;
  private final int mySeverity;
  private final int myType;

  public IdeSyncIssue(@NotNull SyncIssue issue) {
    myMessage = issue.getMessage();
    myData = issue.getData();
    mySeverity = issue.getSeverity();
    myType = issue.getType();
  }

  @Override
  @NotNull
  public String getMessage() {
    return myMessage;
  }

  @Override
  @Nullable
  public String getData() {
    return myData;
  }

  @Override
  public int getSeverity() {
    return mySeverity;
  }

  @Override
  public int getType() {
    return myType;
  }
}
