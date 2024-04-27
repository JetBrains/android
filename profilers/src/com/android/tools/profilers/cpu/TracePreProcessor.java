/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import com.android.tools.idea.protobuf.ByteString;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Receives a {@link ByteString} trace, applies some process over it (e.g. executes simpleperf report-sample on native traces), and return
 * the resulting trace, which can be parsed by a {@link TraceParser}.
 */
public interface TracePreProcessor {

  ByteString FAILURE = ByteString.copyFromUtf8("Failure");

  @NotNull
  ByteString preProcessTrace(@NotNull ByteString trace, @NotNull List<String> symbolsDirs);
}
