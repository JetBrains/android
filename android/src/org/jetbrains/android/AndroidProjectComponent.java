/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android;

import com.android.tools.idea.templates.TemplateManager;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetManagerAdapter;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.android.compiler.AndroidAutogeneratorMode;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.compiler.AndroidPrecompileTask;
import org.jetbrains.android.compiler.ModuleSourceAutogenerating;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidResourceFilesListener;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidProjectComponent extends AbstractProjectComponent {
  private Disposable myDisposable;

  private static boolean ourDynamicTemplateMenuCreated;

  protected AndroidProjectComponent(Project project) {
    super(project);
  }

  @Override
  public void projectOpened() {
    final CompilerManager manager = CompilerManager.getInstance(myProject);
    manager.addBeforeTask(new AndroidPrecompileTask());

    myDisposable = Disposer.newDisposable(getClass().getName());

    if (!ApplicationManager.getApplication().isUnitTestMode() &&
        !ApplicationManager.getApplication().isHeadlessEnvironment()) {

      if (ProjectFacetManager.getInstance(myProject).hasFacets(AndroidFacet.ID)) {
        createAndroidSpecificComponents();
      }
      else {
        final MessageBusConnection connection = myProject.getMessageBus().connect(myDisposable);

        connection.subscribe(FacetManager.FACETS_TOPIC, new FacetManagerAdapter() {
          @Override
          public void facetAdded(@NotNull Facet facet) {
            if (facet instanceof AndroidFacet) {
              createAndroidSpecificComponents();
              connection.disconnect();
            }
          }
        });
      }
    }
  }

  @Override
  public void projectClosed() {
    Disposer.dispose(myDisposable);
  }

  private void createAndroidSpecificComponents() {
    final AndroidResourceFilesListener listener = new AndroidResourceFilesListener(myProject);
    Disposer.register(myDisposable, listener);

    createDynamicTemplateMenu();

    // TODO: for external build systems, this alarm is unnecessary and should not be added
    createAlarmForAutogeneration();
  }

  private static void createDynamicTemplateMenu() {
    if (ourDynamicTemplateMenuCreated) {
      return;
    }
    ourDynamicTemplateMenuCreated = true;
    DefaultActionGroup newGroup = (DefaultActionGroup)ActionManager.getInstance().getAction("NewGroup");
    newGroup.addSeparator();
    ActionGroup menu = TemplateManager.getInstance().getTemplateCreationMenu(null);

    if (menu != null) {
      newGroup.add(menu, new Constraints(Anchor.AFTER, "NewFromTemplate"));
    }
  }

  private void createAlarmForAutogeneration() {
    final Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, myDisposable);
    alarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (!myProject.isOpen()) {
          return; // When project is closing (but not disposed yet), runReadActionInSmartMode throws "Registering post-startup activity"
        }

        DumbService service = DumbService.getInstance(myProject);
        Map<AndroidFacet, Collection<AndroidAutogeneratorMode>> facetsToProcess = service.runReadActionInSmartMode(() -> checkGenerate());
        if (!facetsToProcess.isEmpty()) {
          generate(facetsToProcess);
        }
        if (!alarm.isDisposed()) {
          alarm.addRequest(this, 2000);
        }
      }
    }, 2000);
  }

  private Map<AndroidFacet, Collection<AndroidAutogeneratorMode>> checkGenerate() {
    if (myProject.isDisposed()) return Collections.emptyMap();

    final Map<AndroidFacet, Collection<AndroidAutogeneratorMode>> facetsToProcess = new HashMap<>();

    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
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

  private void generate(final Map<AndroidFacet, Collection<AndroidAutogeneratorMode>> facetsToProcess) {
    TransactionGuard.getInstance().submitTransactionAndWait(
      () -> AndroidCompileUtil.createGenModulesAndSourceRoots(myProject, facetsToProcess.keySet()));

    for (Map.Entry<AndroidFacet, Collection<AndroidAutogeneratorMode>> entry : facetsToProcess.entrySet()) {
      final AndroidFacet facet = entry.getKey();
      final Collection<AndroidAutogeneratorMode> modes = entry.getValue();

      for (AndroidAutogeneratorMode mode : modes) {
        AndroidCompileUtil.doGenerate(facet, mode);
      }
    }
  }
}
