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
package com.android.tools.idea.gradle.project.model.ide.android.stubs;

import com.android.builder.model.InstantRun;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Objects;

/**
 * Stub implementation of {@link InstantRun} for testing.
 */
public class InstantRunStub extends BaseStub implements InstantRun {
  @NotNull private File myInfoFile;
  private boolean myIsSupportedByArtifact;
  private int mySupportStatus;

  public InstantRunStub() {
    this(new File("info"), true, 1);
  }

  public InstantRunStub(@NotNull File infoFile, boolean isSupportedByArtifact, int supportStatus) {
    myInfoFile = infoFile;
    myIsSupportedByArtifact = isSupportedByArtifact;
    mySupportStatus = supportStatus;
  }

  @Override
  @NotNull
  public File getInfoFile() {
    return myInfoFile;
  }

  @Override
  public boolean isSupportedByArtifact() {
    return myIsSupportedByArtifact;
  }

  @Override
  public int getSupportStatus() {
    return mySupportStatus;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof InstantRun)) {
      return false;
    }
    InstantRun stub = (InstantRun)o;
    return isSupportedByArtifact() == stub.isSupportedByArtifact() &&
           getSupportStatus() == stub.getSupportStatus() &&
           Objects.equals(getInfoFile(), stub.getInfoFile());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getInfoFile(), isSupportedByArtifact(), getSupportStatus());
  }

  @Override
  public String toString() {
    return "InstantRunStub{" +
           "myInfoFile=" + myInfoFile +
           ", myIsSupportedByArtifact=" + myIsSupportedByArtifact +
           ", mySupportStatus=" + mySupportStatus +
           "}";
  }
}
