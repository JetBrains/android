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

import com.android.annotations.Nullable;
import com.android.builder.model.TestOptions;

import java.util.Objects;

public class TestOptionsStub extends BaseStub implements TestOptions {
  private static final long serialVersionUID = 1L;

  private final boolean myAnimationsDisabled;

  @Nullable private final Execution myExecutionEnum;

  public TestOptionsStub() {
    myAnimationsDisabled = false;
    myExecutionEnum = null;
  }

  @Override
  @Nullable
  public Execution getExecution() { return myExecutionEnum; }

  @Override
  public boolean getAnimationsDisabled() {
    return myAnimationsDisabled;
  }



  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TestOptions)) {
      return false;
    }
    TestOptions options = (TestOptions)o;
    return equals(options, TestOptions::getAnimationsDisabled) &&
           equals(options, TestOptions::getExecution);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getAnimationsDisabled(), getExecution());
  }

  @Override
  public String toString() {
    return "TestOptionsStub{" +
           "myAnimationsDisabled=" + myAnimationsDisabled +
           ", myExecutionEnum=" + myExecutionEnum +
           '}';
  }

}
