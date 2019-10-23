/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.npw.benchmark;

import static com.android.tools.idea.npw.model.NewProjectModel.getSuggestedProjectPackage;
import static com.android.tools.idea.npw.ui.ActivityGallery.getTemplateIcon;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.npw.model.NewModuleModel;
import com.android.tools.idea.npw.module.ModuleDescriptionProvider;
import com.android.tools.idea.npw.module.ModuleGalleryEntry;
import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.Collections;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NewBenchmarkModuleDescriptionProvider implements ModuleDescriptionProvider {
  @Override
  public Collection<ModuleGalleryEntry> getDescriptions(Project project) {
    if (StudioFlags.NPW_BENCHMARK_TEMPLATE_MODULE.get()) {
      return Collections.singletonList(new BenchmarkModuleTemplateGalleryEntry());
    } else {
      return Collections.EMPTY_LIST;
    }
  }

  private static class BenchmarkModuleTemplateGalleryEntry implements ModuleGalleryEntry {
    @NotNull
    private TemplateHandle myTemplateHandle;

    BenchmarkModuleTemplateGalleryEntry() {
      myTemplateHandle = new TemplateHandle(TemplateManager.getInstance().getTemplateFile(Template.CATEGORY_APPLICATION, "Benchmark Module"));
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return getTemplateIcon(myTemplateHandle, false);
    }

    @NotNull
    @Override
    public String getName() {
      return message("android.wizard.module.new.benchmark.module.app");
    }

    @Nullable
    @Override
    public String getDescription() {
      return myTemplateHandle.getMetadata().getDescription();
    }

    @Override
    public String toString() {
      return getName();
    }

    @NotNull
    @Override
    public SkippableWizardStep createStep(@NotNull NewModuleModel model) {
      Project project = model.getProject().getValue();
      return new ConfigureBenchmarkModuleStep(new NewBenchmarkModuleModel(project, myTemplateHandle, model.getProjectSyncInvoker()), getName());
    }
  }
}
