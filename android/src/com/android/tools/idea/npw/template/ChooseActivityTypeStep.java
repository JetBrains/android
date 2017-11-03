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


import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.module.NewModuleModel;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.android.tools.idea.npw.project.AndroidSourceSet;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.adtui.ASGallery;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.tools.idea.wizard.WizardConstants.DEFAULT_GALLERY_THUMBNAIL_SIZE;
import static org.jetbrains.android.util.AndroidBundle.message;

/**
 * This step allows the user to select which type of Activity they want to create.
 *
 * TODO: ATTR_IS_LAUNCHER seems to be dead code, it was one option in the old UI flow. Find out if we can remove it.
 * TODO: This class and ChooseModuleTypeStep looks to have a lot in common. Should we have something more specific than a ASGallery,
 * that renders "Gallery items"?
 */
public class ChooseActivityTypeStep extends SkippableWizardStep<NewModuleModel> {
  private final RenderTemplateModel myRenderModel;
  private @NotNull List<TemplateRenderer> myTemplateRenderers;
  private @NotNull List<AndroidSourceSet> mySourceSets;

  private @NotNull ASGallery<TemplateRenderer> myActivityGallery;
  private @NotNull ValidatorPanel myValidatorPanel;
  private final StringProperty myInvalidParameterMessage = new StringValueProperty();
  private final ListenerManager myListeners = new ListenerManager();

  private @Nullable AndroidFacet myFacet;

  public ChooseActivityTypeStep(@NotNull NewModuleModel moduleModel,
                                @NotNull RenderTemplateModel renderModel,
                                @NotNull FormFactor formFactor,
                                @NotNull List<AndroidSourceSet> sourceSets) {
    this(moduleModel, renderModel, formFactor);
    init(formFactor, sourceSets, null);
  }

  public ChooseActivityTypeStep(@NotNull NewModuleModel moduleModel,
                                @NotNull RenderTemplateModel renderModel,
                                @NotNull FormFactor formFactor,
                                @NotNull AndroidFacet facet,
                                @NotNull VirtualFile targetDirectory) {
    this(moduleModel, renderModel, formFactor);
    List<AndroidSourceSet> sourceSets = AndroidSourceSet.getSourceSets(facet, targetDirectory);
    init(formFactor, sourceSets, facet);
  }

  private ChooseActivityTypeStep(@NotNull NewModuleModel moduleModel, @NotNull RenderTemplateModel renderModel,
                                 @NotNull FormFactor formFactor) {
    super(moduleModel, message("android.wizard.activity.add", formFactor.id), formFactor.getIcon());
    this.myRenderModel = renderModel;
  }

  private void init(@NotNull FormFactor formFactor,
                    @NotNull List<AndroidSourceSet> sourceSets,
                    @Nullable AndroidFacet facet) {
    mySourceSets = sourceSets;
    myFacet = facet;
    List<TemplateHandle> templateHandles = TemplateManager.getInstance().getTemplateList(formFactor);

    myTemplateRenderers = Lists.newArrayListWithExpectedSize(templateHandles.size() + 1);  // Extra entry for "Add No Activity" template
    if (isNewModule()) {
      myTemplateRenderers.add(new TemplateRenderer(null)); // New modules need a "Add No Activity" entry
    }
    for (TemplateHandle templateHandle : templateHandles) {
      myTemplateRenderers.add(new TemplateRenderer(templateHandle));
    }

    myActivityGallery = createGallery(getTitle());
    myValidatorPanel = new ValidatorPanel(this, new JBScrollPane(myActivityGallery));
    FormScalingUtil.scaleComponentTree(this.getClass(), myValidatorPanel);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myValidatorPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myActivityGallery;
  }

  @NotNull
  @Override
  public Collection<? extends ModelWizardStep> createDependentSteps() {
    String title = message("android.wizard.config.activity.title");
    return Lists.newArrayList(new ConfigureTemplateParametersStep(myRenderModel, title, mySourceSets, myFacet));
  }

  @Override
  public void dispose() {
    myListeners.releaseAll();
  }

  private static ASGallery<TemplateRenderer> createGallery(String title) {
    ASGallery<TemplateRenderer> gallery = new ASGallery<TemplateRenderer>(
      JBList.createDefaultListModel(),
      TemplateRenderer::getImage,
      TemplateRenderer::getTitle,
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
    gallery.getAccessibleContext().setAccessibleDescription(title);

    return gallery;
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    myValidatorPanel.registerMessageSource(myInvalidParameterMessage);

    myActivityGallery.setDefaultAction(new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        wizard.goForward();
      }
    });

    myActivityGallery.addListSelectionListener(listSelectionEvent -> {
      TemplateRenderer selectedTemplate = myActivityGallery.getSelectedElement();
      if (selectedTemplate != null) {
        myRenderModel.setTemplateHandle(selectedTemplate.getTemplate());
        wizard.updateNavigationProperties();
      }
      validateTemplate();
    });

    myListeners.receiveAndFire(getModel().enableCppSupport(), src -> {
      TemplateRenderer[] listItems = createGalleryList(myTemplateRenderers, src.booleanValue());
      myActivityGallery.setModel(JBList.createDefaultListModel((Object[])listItems));
      myActivityGallery.setSelectedIndex(getDefaultSelectedTemplateIndex(listItems, isNewModule())); // Also fires the Selection Listener
    });
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  protected void onEntering() {
    validateTemplate();
  }

  @Override
  protected void onProceeding() {
    // TODO: From David: Can we look into moving this logic into handleFinished?
    // There should be multiple hashtables that a model points to, which gets merged at the last second. That way, we can clear one of the
    // hashtables.

    NewModuleModel moduleModel = getModel();
    Project project = moduleModel.getProject().getValueOrNull();
    if (myRenderModel.getTemplateHandle() == null) { // "Add No Activity" selected
      moduleModel.setDefaultRenderTemplateValues(myRenderModel, project);
    }
    else {
      moduleModel.getRenderTemplateValues().setValue(myRenderModel.getTemplateValues());
    }

    new TemplateValueInjector(moduleModel.getTemplateValues())
      .setProjectDefaults(project, moduleModel.applicationName().get(), myRenderModel.instantApp().get());
  }

  private static int getDefaultSelectedTemplateIndex(@NotNull TemplateRenderer[] templateRenderers, boolean isNewModule) {
    for (int i = 0; i < templateRenderers.length; i++) {
      if (templateRenderers[i].getTitle().equals("Empty Activity")) {
        return i;
      }
    }

    // Default template not found. Instead, return the index to the first valid template renderer (e.g. skip "Add No Activity", etc.)
    for (int i = 0; i < templateRenderers.length; i++) {
      if (templateRenderers[i].getTemplate() != null) {
        return i;
      }
    }

    assert false; // "We should never get here - there should always be at least one valid template
    return 0;
  }

  private boolean isNewModule() {
    return myFacet == null;
  }

  private static TemplateRenderer[] createGalleryList(@NotNull List<TemplateRenderer> templateRenderers, boolean isCppProject) {
    if (isCppProject) {
      List<TemplateRenderer> filteredTemplates = templateRenderers.stream().filter(TemplateRenderer::isCppTemplate).collect(Collectors.toList());
      if (filteredTemplates.size() > 1) {
        return filteredTemplates.toArray(new TemplateRenderer[filteredTemplates.size()]);
      }
    }

    return templateRenderers.toArray(new TemplateRenderer[templateRenderers.size()]);
  }

  private void validateTemplate() {
    TemplateHandle template = myRenderModel.getTemplateHandle();
    TemplateMetadata templateData = (template == null) ? null : template.getMetadata();
    AndroidVersionsInfo.VersionItem androidSdkInfo = myRenderModel.androidSdkInfo().getValueOrNull();

    myInvalidParameterMessage.set(validateTemplate(templateData, androidSdkInfo, isNewModule()));
  }

  private static String validateTemplate(@Nullable TemplateMetadata template,
                                         @Nullable AndroidVersionsInfo.VersionItem androidSdkInfo,
                                         boolean isNewModule) {
    if (template == null) {
      return isNewModule ? "" : message("android.wizard.activity.not.found");
    }

    if (androidSdkInfo != null) {
      if (androidSdkInfo.getApiLevel() < template.getMinSdk()) {
        return message("android.wizard.activity.invalid.min.sdk", template.getMinSdk());
      }

      if (androidSdkInfo.getBuildApiLevel() < template.getMinBuildApi()) {
        return message("android.wizard.activity.invalid.min.build", template.getMinBuildApi());
      }
    }

    return "";
  }

  private static class TemplateRenderer {
    @Nullable private final TemplateHandle myTemplate;

    TemplateRenderer(@Nullable TemplateHandle template) {
      this.myTemplate = template;
    }

    @Nullable
    TemplateHandle getTemplate() {
      return myTemplate;
    }

    @NotNull
    String getTitle() {
      return myTemplate == null ? message("android.wizard.gallery.item.add.no.activity") : myTemplate.getMetadata().getTitle();
    }

    @Override
    public String toString() {
      return getTitle();
    }

    boolean isCppTemplate() {
      if (myTemplate == null) {
        return true;
      }

      // TODO: This is not a good way to find Cpp templates. However, the cpp design needs to be reviewed, and probably updated.
      // TODO: 1 - The Cpp check-box is at the project level, but should probably be at the Module level (like instant apps)
      // TODO: 2 - We should have a dedicated list for Cpp files, or at least add a specific flag to the Templates that are allowed.
      String title = myTemplate.getMetadata().getTitle();
      return "Empty Activity".equals(title) || "Basic Activity".equals(title);
    }

    /**
     * Return the image associated with the current template, if it specifies one, or null otherwise.
     */
    @Nullable
    Image getImage() {
      String thumb = myTemplate == null ? null : myTemplate.getMetadata().getThumbnailPath();
      if (thumb != null && !thumb.isEmpty()) {
        try {
          File file = new File(myTemplate.getRootPath(), thumb.replace('/', File.separatorChar));
          return file.isFile() ? ImageIO.read(file) : null;
        }
        catch (IOException e) {
          Logger.getInstance(ChooseActivityTypeStep.class).warn(e);
        }
      }
      return null;
    }
  }
}
