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
package com.android.tools.idea.diagnostics.crash;

import com.intellij.openapi.components.ServiceManager;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpUriRequest;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface CrashReporter {
  @NotNull
  static CrashReporter getInstance() {
    return ServiceManager.getService(CrashReporter.class);
  }

  @NotNull
  CompletableFuture<String> submit(@NotNull CrashReport crashReport);

  @NotNull
  CompletableFuture<String> submit(@NotNull CrashReport crashReport, boolean userReported);

  @NotNull
  CompletableFuture<String> submit(@NotNull Map<String,String> kv);

  @NotNull
  CompletableFuture<String> submit(@NotNull HttpEntity entity);
}
