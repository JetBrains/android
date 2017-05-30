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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.SyncIssue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Creates a deep copy of a {@link SyncIssue}.
 */
public final class IdeSyncIssue extends IdeModel implements SyncIssue {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;
  private final int myHashCode;

  @NotNull private final String myMessage;
  @Nullable private final String myData;
  private final int mySeverity;
  private final int myType;

  public IdeSyncIssue(@NotNull SyncIssue issue, @NotNull ModelCache modelCache) {
    super(issue, modelCache);
    myMessage = issue.getMessage();
    myData = issue.getData();
    mySeverity = issue.getSeverity();
    myType = issue.getType();

    myHashCode = calculateHashCode();
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeSyncIssue)) {
      return false;
    }
    IdeSyncIssue issue = (IdeSyncIssue)o;
    return mySeverity == issue.mySeverity &&
           myType == issue.myType &&
           Objects.equals(myMessage, issue.myMessage) &&
           Objects.equals(myData, issue.myData);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  private int calculateHashCode() {
    return Objects.hash(myMessage, myData, mySeverity, myType);
  }

  @Override
  public String toString() {
    return "IdeSyncIssue{" +
           "myMessage='" + myMessage + '\'' +
           ", myData='" + myData + '\'' +
           ", mySeverity=" + mySeverity +
           ", myType=" + myType +
           "}";
  }
}
