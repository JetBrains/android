/*
 * Copyright (C) 2020 The Android Open Source Project
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
package org.jetbrains.android;

import com.android.annotations.concurrency.UiThread;
import com.android.tools.idea.AndroidStartupActivity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.compiler.AndroidPrecompileTask;
import org.jetbrains.annotations.NotNull;

// TODO(b/150625066): Move to JPS project system if possible.
public class CompilerManagerAndroidStartupActivity implements AndroidStartupActivity {

  @Override
  @UiThread
  public void runActivity(@NotNull Project project, @NotNull Disposable disposable) {
    final CompilerManager manager = CompilerManager.getInstance(project);
    manager.addBeforeTask(new AndroidPrecompileTask());
  }
}
