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
package com.android.tools.idea.run.deployable;

import com.android.ddmlib.Client;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

class Process {
  @NotNull private volatile Set<String> myApplicationIds;
  @NotNull private Client myClient;

  Process(@NotNull Client client) {
    myApplicationIds = new HashSet<>();
    myClient = client;
  }

  @NotNull
  Client getClient() {
    return myClient;
  }

  void setClient(@NotNull Client client) {
    myClient = client;
  }

  void addApplicationId(@NotNull String applicationId) {
    myApplicationIds.add(applicationId);
  }

  boolean containsApplicationId(@NotNull String applicationId) {
    return myApplicationIds.contains(applicationId);
  }
}
