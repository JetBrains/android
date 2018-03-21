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
package com.android.tools.idea.npw.template;

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.npw.assetstudio.IconGenerator;
import com.android.tools.idea.npw.module.NewModuleModel;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.android.tools.idea.npw.platform.Language;
import com.android.tools.idea.observable.core.*;
import com.android.tools.idea.projectsystem.AndroidModuleTemplate;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.wizard.model.WizardModel;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.DOT_JAVA;
import static com.android.SdkConstants.DOT_KT;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_KOTLIN_SUPPORT;

/**
 * A model responsible for instantiating a FreeMarker {@link Template} into the current project
 * representing an Android component.
 */
public final class RenderTemplateModel extends WizardModel {
  private static final String PROPERTIES_RENDER_LANGUAGE_KEY = "SAVED_RENDER_LANGUAGE";

  @NotNull private final String myCommandName;
  @NotNull private final OptionalProperty<Project> myProject;
  @NotNull private final ObjectProperty<NamedModuleTemplate> myTemplates;
  @NotNull private final ObjectProperty<Language> myLanguageSet;
  @NotNull private final OptionalProperty<AndroidVersionsInfo.VersionItem> myAndroidSdkInfo = new OptionalValueProperty<>();
  @NotNull private final StringProperty myPackageName;
  @NotNull private final BoolProperty myInstantApp;
  @NotNull private final MultiTemplateRenderer myMultiTemplateRenderer;
  @Nullable private final Module myModule;
  private final boolean myIsNewProject;

  /**
   * The target template we want to render. If null, the user is skipping steps that would instantiate a template and this model shouldn't
   * try to render anything.
   */
  @Nullable private TemplateHandle myTemplateHandle;
  @NotNull private final Map<String, Object> myTemplateValues = Maps.newHashMap();
  @Nullable private IconGenerator myIconGenerator;

  public RenderTemplateModel(@NotNull Module module,
                             @Nullable TemplateHandle templateHandle,
                             @NotNull String initialPackageSuggestion,
                             @NotNull NamedModuleTemplate template,
                             @NotNull String commandName) {
    myProject = new OptionalValueProperty<>(module.getProject());
    myModule = module;
    myInstantApp = new BoolValueProperty(false);
    myPackageName = new StringValueProperty(initialPackageSuggestion);
    myTemplates = new ObjectValueProperty<>(template);
    myTemplateHandle = templateHandle;
    myCommandName = commandName;
    myMultiTemplateRenderer = new MultiTemplateRenderer();
    myLanguageSet = new ObjectValueProperty<>(getInitialSourceLanguage(module.getProject()));
    myIsNewProject = myProject.getValueOrNull() == null;
    init();
  }

  public RenderTemplateModel(@NotNull NewModuleModel moduleModel,
                             @Nullable TemplateHandle templateHandle,
                             @NotNull NamedModuleTemplate template,
                             @NotNull String commandName) {
    myProject = moduleModel.getProject();
    myModule = null;
    myInstantApp = moduleModel.instantApp();
    myPackageName = moduleModel.packageName();
    myTemplates = new ObjectValueProperty<>(template);
    myTemplateHandle = templateHandle;
    myCommandName = commandName;
    myMultiTemplateRenderer = moduleModel.getMultiTemplateRenderer();
    myMultiTemplateRenderer.increment();
    myLanguageSet = new ObjectValueProperty<>(getInitialSourceLanguage(myProject.getValueOrNull()));
    myIsNewProject = myProject.getValueOrNull() == null;
    init();
  }

  private void init() {
    myLanguageSet.addListener(sender -> setInitialSourceLanguage(myLanguageSet.get()));
  }

  private static Logger getLog() {
    return Logger.getInstance(RenderTemplateModel.class);
  }

  @NotNull
  public Map<String, Object> getTemplateValues() {
    return myTemplateValues;
  }

  /**
   * Get the current {@link SourceProvider} used by this model (the source provider affects which
   * paths the template's output will be rendered into).
   */
  @NotNull
  public ObjectProperty<NamedModuleTemplate> getTemplate() {
    return myTemplates;
  }

  @NotNull
  public ObjectProperty<Language> getLanguage() {
    return myLanguageSet;
  }

  /**
   * The package name affects which paths the template's output will be rendered into.
   */
  @NotNull
  public StringProperty packageName() {
    return myPackageName;
  }

  @NotNull
  public BoolProperty instantApp() {
    return myInstantApp;
  }

  public void setTemplateHandle(@Nullable TemplateHandle templateHandle) {
    myTemplateHandle = templateHandle;
  }

  @Nullable
  public TemplateHandle getTemplateHandle() {
    return myTemplateHandle;
  }

  @NotNull
  public OptionalProperty<Project> getProject() {
    return myProject;
  }

  public OptionalProperty<AndroidVersionsInfo.VersionItem> androidSdkInfo() {
    return myAndroidSdkInfo;
  }

  /**
   * If this template should also generate icon assets, set an icon generator.
   */
  public void setIconGenerator(@NotNull IconGenerator iconGenerator) {
    myIconGenerator = iconGenerator;
  }

  @Override
  protected void handleFinished() {
    myMultiTemplateRenderer.requestRender(new FreeMarkerTemplateRenderer());
  }

  @Override
  protected void handleSkipped() {
    myMultiTemplateRenderer.skipRender();
  }

  private class FreeMarkerTemplateRenderer implements MultiTemplateRenderer.TemplateRenderer {

    @Override
    public boolean doDryRun() {
      if (!myProject.get().isPresent() || myTemplateHandle == null) {
        getLog().error("RenderTemplateModel did not collect expected information and will not complete. Please report this error.");
        return false;
      }

      AndroidModuleTemplate paths = myTemplates.get().getPaths();
      final Project project = myProject.getValue();

      return renderTemplate(true, project, paths, null, null);
    }

    @Override
    public void render() {
      final AndroidModuleTemplate paths = myTemplates.get().getPaths();
      final Project project = myProject.getValue();
      final List<File> filesToOpen = Lists.newArrayListWithExpectedSize(3);
      final List<File> filesToReformat = Lists.newArrayList();

      boolean success = new WriteCommandAction<Boolean>(project, myCommandName) {
        @Override
        protected void run(@NotNull Result<Boolean> result) throws Throwable {
          boolean success = renderTemplate(false, project, paths, filesToOpen, filesToReformat);
          if (success && myIconGenerator != null) {
            myIconGenerator.generateIconsToDisk(paths);
          }

          result.setResult(success);
        }
      }.execute().getResultObject();

      if (success) {
        if (isKotlinTemplate()) {
          JavaToKotlinHandler.convertJavaFilesToKotlin(project, filesToReformat, () -> {
            // replace .java w/ .kt files
            for (int i = 0; i < filesToOpen.size(); i++) {
              File file = filesToOpen.get(i);
              if (file.getName().endsWith(DOT_JAVA)) {
                File ktFile =
                  new File(file.getParent(), file.getName().replace(DOT_JAVA, DOT_KT));
                filesToOpen.set(i, ktFile);
              }
            }

            TemplateUtils.openEditors(project, filesToOpen, true);
          });
        }
        else {
          // calling smartInvokeLater will make sure that files are open only when the project is ready
          DumbService.getInstance(project).smartInvokeLater(() -> TemplateUtils.openEditors(project, filesToOpen, true));
        }
      }
    }

    private boolean isKotlinTemplate() {
      return (Boolean)myTemplateValues.getOrDefault(ATTR_KOTLIN_SUPPORT, false);
    }

    private boolean renderTemplate(boolean dryRun,
                                   @NotNull Project project,
                                   @NotNull AndroidModuleTemplate paths,
                                   @Nullable List<File> filesToOpen,
                                   @Nullable List<File> filesToReformat) {
      final Template template = myTemplateHandle.getTemplate();
      File moduleRoot = paths.getModuleRoot();
      if (moduleRoot == null) {
        return false;
      }

      if (!dryRun && StudioFlags.NPW_DUMP_TEMPLATE_VARS.get() && filesToOpen != null) {
        VirtualFile result = toScratchFile(project);
        if (result != null) {
          filesToOpen.add(VfsUtilCore.virtualToIoFile(result));
        }
      }

      // @formatter:off
    final RenderingContext context = RenderingContext.Builder.newContext(template, project)
      .withCommandName(myCommandName)
      .withDryRun(dryRun)
      .withShowErrors(true)
      .withModuleRoot(paths.getModuleRoot())
      .withModule(myModule)
      .withParams(myTemplateValues)
      .intoOpenFiles(filesToOpen)
      .intoTargetFiles(filesToReformat)
      .build();
    // @formatter:on
      return template.render(context, dryRun);
    }
  }

  // For ease of debugging add a scratch file containing the template values.
  private VirtualFile toScratchFile(@Nullable Project project) {
    StringBuilder templateVars = new StringBuilder();
    String lineSeparator = System.lineSeparator();
    for (Map.Entry<String, Object> entry : myTemplateValues.entrySet()) {
      templateVars.append(entry.getKey())
        .append("=").append(entry.getValue())
        .append(lineSeparator);
    }
    return ScratchRootType.getInstance()
      .createScratchFile(project, "templateVars.txt", PlainTextLanguage.INSTANCE,
                         templateVars.toString(), ScratchFileService.Option.create_new_always);
  }

  /**
   * Design: If there are no kotlin facets in the project, the default should be Java, whether or not you previously chose Kotlin
   * (presumably in a different project which did have Kotlin).
   * If it *does* have a Kotlin facet, then remember the previous selection (if there was no previous selection yet, default to Kotlin)
   */
  @NotNull
  private static Language getInitialSourceLanguage(@Nullable Project project) {
    if (project != null && JavaToKotlinHandler.hasKotlinFacet(project)) {
      return Language.fromName(PropertiesComponent.getInstance().getValue(PROPERTIES_RENDER_LANGUAGE_KEY), Language.KOTLIN);
    }
    return Language.JAVA;
  }

  private static void setInitialSourceLanguage(@NotNull Language language) {
    PropertiesComponent.getInstance().setValue(PROPERTIES_RENDER_LANGUAGE_KEY, language.getName());
  }
}
