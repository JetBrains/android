/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.navigation;

import com.android.annotations.NonNull;

public class Event {
  public enum Operation {INSERT, UPDATE, DELETE}

  public final Operation operation;
  public final Class<?> operandType;

  public Event(@NonNull Operation operation, @NonNull Class operandType) {
    this.operation = operation;
    this.operandType = operandType;
  }

  public static Event of(@NonNull Operation operation, @NonNull Class operandType) {
    return new Event(operation, operandType);
  }

  public static Event insert(@NonNull Class operandType) {
    return of(Operation.INSERT, operandType);
  }

  public static Event update(@NonNull Class operandType) {
    return of(Operation.UPDATE, operandType);
  }

  public static Event delete(@NonNull Class operandType) {
    return of(Operation.DELETE, operandType);
  }
}
