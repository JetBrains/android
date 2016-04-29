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
package com.android.tools.idea.fd;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BooleanStatus {
  public static final BooleanStatus SUCCESS = new BooleanStatus(true, null);

  public final boolean success;
  @Nullable private final String error;

  private BooleanStatus(boolean success, @Nullable String error) {
    this.success = success;
    this.error = error;
  }

  public static BooleanStatus failure(@NotNull String error) {
    return new BooleanStatus(false, error);
  }

  @NotNull
  public String getCause() {
    assert !success && error != null;
    return error;
  }
}
