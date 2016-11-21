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
package com.android.tools.idea.npw.template;


import com.android.SdkConstants;
import com.android.ide.common.repository.GradleVersion;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.gradle.plugin.AndroidPluginGeneration;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.npw.ActivityGalleryStep;
import com.android.tools.idea.npw.module.ConfigureAndroidModuleStep;
import com.android.tools.idea.npw.module.NewModuleModel;
import com.android.tools.idea.npw.project.AndroidSourceSet;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.templates.RepositoryUrlManager;
import com.android.tools.idea.templates.SupportLibrary;
import com.android.tools.idea.ui.ASGallery;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.android.tools.idea.wizard.template.TemplateWizard;
import com.android.tools.swing.util.FormScalingUtil;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.wizard.WizardConstants.DEFAULT_GALLERY_THUMBNAIL_SIZE;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

/**
 * This step allows the user to select which type of Activity they want to create.
 * TODO: This step can be used as part of the "New Project" flow. In that flow, if the "Has CPP support" is selected, we should not show
 * this step, but the next step should be "Basic Activity". In the current work flow (using the dynamic wizard), this was difficult to do,
 * so instead {@link ActivityGalleryStep} was always shown with three options ("Add no Activity", "Basic Activity" and "Empty Activity").
 * The code to filter out the activities is {@link TemplateListProvider}
 * TODO: ATTR_IS_LAUNCHER seems to be dead code, it was one option in the old UI flow. Find out if we can remove it.
 * TODO: CircularParameterDependencyException when selecting "Empty Activity" > "Cancel" (OK with all others!)
 * TODO: Missing error messages for "missing theme", "incompatible API", ect. See {@link ActivityGalleryStep#validate()}
 * TODO: Extending RenderTemplateModel don't seem to match with the things ChooseActivityTypeStep needs to be configured... For example,
 * it needs to know "Is Cpp Project" (to adjust the list of templates or hide itself).
 * TODO: This class and future ChooseModuleTypeStep look to have a lot in common. Should we have something more specific than a ASGallery,
 * that renders "Gallery items"?
 */
public class ChooseActivityTypeStep extends SkippableWizardStep<NewModuleModel> {
  private static final String WH_SDK_ENV_VAR = "WH_SDK";

  private final RenderTemplateModel myRenderModel;
  private @NotNull TemplateHandle[] myTemplateList;
  private @NotNull List<AndroidSourceSet> mySourceSets;

  private @NotNull ASGallery<TemplateHandle> myActivityGallery;
  private @NotNull JComponent myRootPanel;

  private @Nullable AndroidFacet myFacet;

  public ChooseActivityTypeStep(@NotNull NewModuleModel moduleModel,
                                @NotNull RenderTemplateModel renderModel,
                                @NotNull List<TemplateHandle> templateList,
                                @NotNull List<AndroidSourceSet> sourceSets) {
    this(moduleModel, renderModel);
    init(templateList, sourceSets, null);
  }

  public ChooseActivityTypeStep(@NotNull NewModuleModel moduleModel,
                                @NotNull RenderTemplateModel renderModel,
                                @NotNull AndroidFacet facet,
                                @NotNull List<TemplateHandle> templateList,
                                @NotNull VirtualFile targetDirectory) {
    this(moduleModel, renderModel);
    List<AndroidSourceSet> sourceSets = AndroidSourceSet.getSourceSets(facet, targetDirectory);
    init(templateList, sourceSets, facet);
  }

  private ChooseActivityTypeStep(@NotNull NewModuleModel moduleModel, @NotNull RenderTemplateModel renderModel) {
    super(moduleModel, "Add an Activity to " + renderModel.getTemplateHandle().getMetadata().getFormFactor());
    this.myRenderModel = renderModel;
  }

  private void init(@NotNull List<TemplateHandle> templateList,
                    @NotNull List<AndroidSourceSet> sourceSets,
                    @Nullable AndroidFacet facet) {
    myTemplateList = templateList.toArray(new TemplateHandle[templateList.size()]);
    mySourceSets = sourceSets;

    myActivityGallery = createGallery(getTitle());
    myRootPanel = new JBScrollPane(myActivityGallery);
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
    myFacet = facet;
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myActivityGallery;
  }

  @NotNull
  @Override
  public Collection<? extends ModelWizardStep> createDependentSteps() {
    String title = AndroidBundle.message("android.wizard.config.activity.title");
    return Lists.newArrayList(new ConfigureTemplateParametersStep(myRenderModel, title, mySourceSets, myFacet));
  }

  private static ASGallery<TemplateHandle> createGallery(String title) {
    ASGallery<TemplateHandle> gallery = new ASGallery<TemplateHandle>(
      JBList.createDefaultListModel(),
      ChooseActivityTypeStep::getImage,
      ChooseActivityTypeStep::getTemplateTitle,
      DEFAULT_GALLERY_THUMBNAIL_SIZE,
      null
    ) {

      @Override
      public Dimension getPreferredScrollableViewportSize() {
        Dimension cellSize = computeCellSize();
        int heightInsets = getInsets().top + getInsets().bottom;
        int widthInsets = getInsets().left + getInsets().right;
        // Don't want to show an exact number of rows, since then it's not obvious there's another row available.
        return new Dimension(cellSize.width * 5 + widthInsets, (int)(cellSize.height * 2.2) + heightInsets);
      }
    };

    gallery.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    AccessibleContextUtil.setDescription(gallery, title);

    return gallery;
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    myActivityGallery.setModel(JBList.createDefaultListModel((Object[])myTemplateList));
    myActivityGallery.setDefaultAction(new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        wizard.goForward();
      }
    });

    myActivityGallery.addListSelectionListener(listSelectionEvent -> {
      TemplateHandle selectedTemplate = myActivityGallery.getSelectedElement();
      if (selectedTemplate != null) {
        myRenderModel.setTemplateHandle(selectedTemplate);
      }
    });

    int defaultSelection = getDefaultSelectedTemplateIndex(myTemplateList);
    myActivityGallery.setSelectedIndex(defaultSelection); // Also fires the Selection Listener
  }

  @Override
  protected void onProceeding() {
    // TODO: From David: Can we look into moving this logic into handleFinished?
    // There should be multiple hashtables that a model points to, which gets merged at the last second. That way, we can clear one of the
    // hashtables.

    getModel().getRenderTemplateValues().setValue(myRenderModel.getTemplateValues());

    Map<String, Object> moduleTemplateValue = getModel().getTemplateValues();
    initTemplateValues(moduleTemplateValue, getModel().getProject().getValueOrNull());

    moduleTemplateValue.put(ATTR_APP_TITLE, getModel().applicationName().get());
  }

  private static void initTemplateValues(@NotNull Map<String, Object> templateValues, @Nullable Project project) {
    templateValues.put(ATTR_GRADLE_PLUGIN_VERSION, determineGradlePluginVersion(project));
    templateValues.put(ATTR_GRADLE_VERSION, SdkConstants.GRADLE_LATEST_VERSION);
    templateValues.put(ATTR_IS_GRADLE, true);

    // TODO: Check if this is used at all by the templates
    templateValues.put("target.files", new HashSet<>());
    templateValues.put("files.to.open", new ArrayList<>());

    // TODO: Implement Instant App code
    String whSdkLocation = System.getenv(WH_SDK_ENV_VAR);
    templateValues.put(ATTR_WH_SDK, whSdkLocation + "/tools/resources/shared-libs");
    templateValues.put("whSdkEnabled", isNotEmpty(whSdkLocation));
    templateValues.put("alsoCreateIapk", false);
    templateValues.put("isInstantApp", false);

    // TODO: Check this one with Joe. It seems to be used by the old code on Import module, but can't find it on new code
    templateValues.put(ATTR_CREATE_ACTIVITY, false);
    templateValues.put(ATTR_PER_MODULE_REPOS, false);

    // TODO: This seems project stuff
    if (project != null) {
      templateValues.put(ATTR_TOP_OUT, project.getBasePath());
    }

    String mavenUrl = System.getProperty(TemplateWizard.MAVEN_URL_PROPERTY);
    if (mavenUrl != null) {
      templateValues.put(ATTR_MAVEN_URL, mavenUrl);
    }

    final AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    BuildToolInfo buildTool = sdkHandler.getLatestBuildTool(new StudioLoggerProgressIndicator(ConfigureAndroidModuleStep.class), false);
    if (buildTool != null) {
      // If buildTool is null, the template will use buildApi instead, which might be good enough.
      templateValues.put(ATTR_BUILD_TOOLS_VERSION, buildTool.getRevision().toString());
    }

    File sdkLocation = sdkHandler.getLocation();
    if (sdkLocation != null) {
      // Gradle expects a platform-neutral path
      templateValues.put(ATTR_SDK_DIR, FileUtil.toSystemIndependentName(sdkLocation.getPath()));

      String espressoVersion = RepositoryUrlManager.get().getLibraryRevision(SupportLibrary.ESPRESSO_CORE.getGroupId(),
                                                                             SupportLibrary.ESPRESSO_CORE.getArtifactId(),
                                                                             null, false, sdkLocation, FileOpUtils.create());

      if (espressoVersion != null) {
        // TODO: Is this something that should be on the template (TemplateMetadata.ATTR_)?
        // Check with Jens, or at least send an email to verify template variables. We may also need to port some old dynamic step.
        templateValues.put("espressoVersion", espressoVersion);
      }
    }
  }

  /**
   * Find the most appropriated Gradle Plugin version for the specified project.
   * @param project If {@code null} (ie we are creating a new project) returns the recommended gradle version.
   */
  @NotNull
  private static String determineGradlePluginVersion(@Nullable Project project) {
    String defaultGradleVersion = AndroidPluginGeneration.ORIGINAL.getLatestKnownVersion();
    if (project == null) {
      return defaultGradleVersion;
    }

    GradleVersion versionInUse = GradleUtil.getAndroidGradleModelVersionInUse(project);
    if (versionInUse != null) {
      return versionInUse.toString();
    }

    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.searchInBuildFilesOnly(project);
    GradleVersion pluginVersion = (androidPluginInfo == null) ? null : androidPluginInfo.getPluginVersion();
    return (pluginVersion == null) ? defaultGradleVersion : pluginVersion.toString();
  }

  /**
   * Return the image associated with the current template, if it specifies one, or null otherwise.
   */
  @Nullable
  private static Image getImage(TemplateHandle template) {
    String thumb = template.getMetadata().getThumbnailPath();
    if (thumb != null && !thumb.isEmpty()) {
      try {
        File file = new File(template.getRootPath(), thumb.replace('/', File.separatorChar));
        return file.isFile() ? ImageIO.read(file) : null;
      }
      catch (IOException e) {
        Logger.getInstance(ActivityGalleryStep.class).warn(e);
      }
    }
    return null;
  }

  @NotNull
  private static String getTemplateTitle(TemplateHandle templateHandle) {
    return templateHandle == null ? "<none>" : templateHandle.getMetadata().getTitle();
  }

  private static int getDefaultSelectedTemplateIndex(@NotNull TemplateHandle[] templateList) {
    for (int i = 0; i < templateList.length; i++) {
      if (getTemplateTitle(templateList[i]).equals("Empty Activity")) {
        return i;
      }
    }
    return 0;
  }
}
