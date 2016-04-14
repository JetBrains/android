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
package com.android.tools.idea.run.tasks;

import org.jetbrains.annotations.NotNull;

/**
 * {@link LaunchTasksProviderFactory} provides an additional level of indirection so that the {@link LaunchTasksProvider}
 * can be created once the results of a build are known.
 *
 * An alternative solution would be to just have the {@link LaunchTasksProvider} understand that all its methods are called only after a
 * build is complete, and reconfigure itself based on the build results.
 */
public interface LaunchTasksProviderFactory {
  @NotNull
  LaunchTasksProvider get();
}
