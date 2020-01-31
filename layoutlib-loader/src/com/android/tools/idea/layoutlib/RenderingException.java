/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.layoutlib;

import org.jetbrains.annotations.NotNull;

public class RenderingException extends Exception {
  private final String myPresentableMessage;
  private final Throwable[] myCauses;

  public RenderingException() {
    super();
    myPresentableMessage = null;
    myCauses = new Throwable[0];
  }

  public RenderingException(String message, Throwable... causes) {
    super(message, causes.length > 0 ? causes[0] : null);
    myPresentableMessage = message;
    myCauses = causes;
  }

  public RenderingException(@NotNull Throwable... causes) {
    myPresentableMessage = null;
    myCauses = causes;
  }

  @NotNull
  public Throwable[] getCauses() {
    return myCauses;
  }

  public String getPresentableMessage() {
    return myPresentableMessage;
  }
}
