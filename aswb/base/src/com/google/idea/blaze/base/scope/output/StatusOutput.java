/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
import org.jetbrains.annotations.NotNull;

/**
 * Status message output.
 *
 * <p>Status output are shown sequentially, with previous status strings still visible.
 *
 * <p>See also {@link StateUpdate}.
 */
public class StatusOutput implements Output {
  @NotNull String status;

  public StatusOutput(@NotNull String status) {
    this.status = status;
  }

  @NotNull
  public String getStatus() {
    return status;
  }
}
