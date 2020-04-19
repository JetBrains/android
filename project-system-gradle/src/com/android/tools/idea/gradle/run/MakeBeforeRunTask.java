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
package com.android.tools.idea.gradle.run;

import com.intellij.execution.BeforeRunTask;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see MakeBeforeRunTaskProvider
 */
public class MakeBeforeRunTask extends BeforeRunTask<MakeBeforeRunTask> {
  private static final String SERIALIZATION_KEY = "goal";

  private String myGoal;
  private boolean myIsValid = true;

  protected MakeBeforeRunTask() {
    super(MakeBeforeRunTaskProvider.ID);
  }

  public void setGoal(String goal) {
    myGoal = goal;
  }

  @Nullable
  public String getGoal() {
    return myGoal;
  }

  public void setInvalid() {
    myIsValid = false;
  }

  public boolean isValid() {
    return myIsValid;
  }

  @Override
  public void writeExternal(@NotNull Element element) {
    super.writeExternal(element);
    if (myGoal != null) {
      element.setAttribute(SERIALIZATION_KEY, myGoal);
    }
  }

  @Override
  public void readExternal(@NotNull Element element) {
    super.readExternal(element);
    myGoal = element.getAttributeValue(SERIALIZATION_KEY);
  }

  // equals and hashCode are necessary so that the infrastructure makes the necessary
  // calls to readExternal & writeExternal above.
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    MakeBeforeRunTask that = (MakeBeforeRunTask)o;

    if (myIsValid != that.myIsValid) return false;
    if (myGoal != null ? !myGoal.equals(that.myGoal) : that.myGoal != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myGoal != null ? myGoal.hashCode() : 0);
    result = 31 * result + (myIsValid ? 1 : 0);
    return result;
  }
}
