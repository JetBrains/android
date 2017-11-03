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
package com.android.tools.adtui.validation.validators;

import com.android.tools.adtui.validation.Validator;
import org.jetbrains.annotations.NotNull;

/**
 * A validator that returns valid when the boolean value it is testing against is false.
 */
public final class FalseValidator implements Validator<Boolean> {
  private final Result myInvalidResult;

  public FalseValidator(@NotNull  String errorMessage) {
    this(Severity.ERROR, errorMessage);
  }

  public FalseValidator(@NotNull Severity severity, @NotNull String message) {
    myInvalidResult = new Result(severity, message);
  }

  @NotNull
  @Override
  public Result validate(@NotNull Boolean value) {
    // returns failure when true
    return value ? myInvalidResult : Result.OK;
  }
}
