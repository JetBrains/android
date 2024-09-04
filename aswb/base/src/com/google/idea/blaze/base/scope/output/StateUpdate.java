/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.scope.output;

import com.google.idea.blaze.common.Output;

/**
 * State update.
 *
 * <p>Any state update given replaces any previous state; only the most recent one is visible at a
 * time.
 *
 * <p>See also {@link StatusOutput}.
 */
public final class StateUpdate implements Output {

  final String state;

  public StateUpdate(String state) {
    this.state = state;
  }

  public String getState() {
    return state;
  }
}
