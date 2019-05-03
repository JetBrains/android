/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.actions;

import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.compiler.AndroidAutogenerator;
import org.jetbrains.android.compiler.AndroidAutogeneratorMode;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidRegenerateSourcesAction extends AnAction {

  private static final String TITLE = "Generate Sources";

  public AndroidRegenerateSourcesAction() {
    super(TITLE);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Module module = e.getData(LangDataKeys.MODULE);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    boolean visible = project != null && !ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID).isEmpty();
    String title = TITLE;

    if (visible) {
      visible = false;

      if (module != null) {
        AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet != null) {
          visible = AndroidAutogenerator.supportsAutogeneration(facet);
          title = TITLE + " for '" + module.getName() + "'";
        }
      }
      else {
        List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
        for (AndroidFacet facet : facets) {
          if (AndroidAutogenerator.supportsAutogeneration(facet)) {
            visible = true;
            break;
          }
        }
      }
    }
    e.getPresentation().setEnabledAndVisible(visible);
    e.getPresentation().setText(title);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    Module module = e.getData(LangDataKeys.MODULE);
    if (module != null) {
      generate(project, module);
      return;
    }
    assert project != null;
    List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    List<Module> modulesToProcess = new ArrayList<Module>();
    for (AndroidFacet facet : facets) {
      module = facet.getModule();
      if (AndroidAutogenerator.supportsAutogeneration(facet)) {
        modulesToProcess.add(module);
      }
    }
    if (!modulesToProcess.isEmpty()) {
      generate(project, modulesToProcess.toArray(Module.EMPTY_ARRAY));
    }
  }

  private static void generate(Project project, final Module... modules) {
    CompilerManager.getInstance(project).executeTask(new CompileTask() {
      @Override
      public boolean execute(CompileContext context) {
        // todo: compatibility with background autogenerating

        for (Module module : modules) {
          final AndroidFacet facet = AndroidFacet.getInstance(module);

          if (facet != null) {
            for (AndroidAutogeneratorMode mode : AndroidAutogeneratorMode.values()) {
              AndroidCompileUtil.generate(facet, mode, context, true);
            }
          }
        }
        return true;
      }
    }, new ModuleCompileScope(project, modules, false), AndroidBundle.message("android.compile.messages.generating.r.java.content.name"),
                                                     null);
  }
}
