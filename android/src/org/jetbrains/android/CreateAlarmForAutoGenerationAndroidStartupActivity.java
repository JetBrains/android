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

import com.android.tools.idea.AndroidStartupActivity;
import com.intellij.compiler.impl.CompileContextImpl;
import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.compiler.progress.CompilerTask;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.compiler.AndroidAutogenerator;
import org.jetbrains.android.compiler.AndroidAutogeneratorMode;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.compiler.ModuleSourceAutogenerating;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

// TODO(b/150626962): Review and move to JPS project system if possible.
class CreateAlarmForAutoGenerationAndroidStartupActivity implements AndroidStartupActivity {

  @Override
  public void runActivity(@NotNull Project project, @NotNull Disposable disposable) {
    // TODO: for external build systems, this alarm is unnecessary and should not be added
    createAlarmForAutogeneration(project, disposable);
  }

  private void createAlarmForAutogeneration(Project project, Disposable rootDisposable) {
    final Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, rootDisposable);
    alarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (!project.isOpen()) {
          return; // When project is closing (but not disposed yet), runReadActionInSmartMode throws "Registering post-startup activity"
        }
        // TODO(b/150673256): Consider using NonBlockingReadAction.
        DumbService service = DumbService.getInstance(project);
        Map<AndroidFacet, Collection<AndroidAutogeneratorMode>> facetsToProcess = service.runReadActionInSmartMode(() -> checkGenerate(project));
        if (!facetsToProcess.isEmpty()) {
          generate(project, facetsToProcess);
        }
        if (!alarm.isDisposed()) {
          alarm.addRequest(this, 2000);
        }
      }
    }, 2000);
  }

  private Map<AndroidFacet, Collection<AndroidAutogeneratorMode>> checkGenerate(@NotNull Project project) {
    if (project.isDisposed()) return Collections.emptyMap();

    final Map<AndroidFacet, Collection<AndroidAutogeneratorMode>> facetsToProcess = new HashMap<>();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet == null) {
        continue;
      }

      if (!ModuleSourceAutogenerating.requiresAutoSourceGeneration(facet)) {
        continue;
      }

      ModuleSourceAutogenerating autogenerator = ModuleSourceAutogenerating.getInstance(facet);
      assert autogenerator != null;

      final Set<AndroidAutogeneratorMode> modes = EnumSet.noneOf(AndroidAutogeneratorMode.class);
      for (AndroidAutogeneratorMode mode : AndroidAutogeneratorMode.values()) {
        if (autogenerator.cleanRegeneratingState(mode) || autogenerator.isGeneratedFileRemoved(mode)) {
          modes.add(mode);
        }
      }

      if (!modes.isEmpty()) {
        facetsToProcess.put(facet, modes);
      }
    }
    return facetsToProcess;
  }

  private void generate(@NotNull Project project, final Map<AndroidFacet, Collection<AndroidAutogeneratorMode>> facetsToProcess) {
    TransactionGuard.getInstance().submitTransactionAndWait(
      () -> AndroidCompileUtil.createGenModulesAndSourceRoots(project, facetsToProcess.keySet()));

    for (Map.Entry<AndroidFacet, Collection<AndroidAutogeneratorMode>> entry : facetsToProcess.entrySet()) {
      final AndroidFacet facet = entry.getKey();
      final Collection<AndroidAutogeneratorMode> modes = entry.getValue();

      for (AndroidAutogeneratorMode mode : modes) {
        doGenerate(facet, mode);
      }
    }
  }

  public static boolean doGenerate(AndroidFacet facet, final AndroidAutogeneratorMode mode) {
    assert !ApplicationManager.getApplication().isDispatchThread();
    final CompileContext[] contextWrapper = new CompileContext[1];
    final Module module = facet.getModule();
    final Project project = module.getProject();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        if (project.isDisposed()) return;
        CompilerTask task = new CompilerTask(project, "Android auto-generation", true, false, true, true);
        CompileScope scope = new ModuleCompileScope(module, false);
        contextWrapper[0] = new CompileContextImpl(project, task, scope, false, false);
      }
    });
    CompileContext context = contextWrapper[0];
    if (context == null) {
      return false;
    }
    AndroidAutogenerator.run(mode, facet, context, false);
    return context.getMessages(CompilerMessageCategory.ERROR).length == 0;
  }
}
