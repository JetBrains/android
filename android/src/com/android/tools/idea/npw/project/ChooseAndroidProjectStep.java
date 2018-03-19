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
package com.android.tools.idea.npw.project;

import com.android.tools.adtui.ASGallery;
import com.android.tools.adtui.stdui.CommonTabbedPane;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.cpp.ConfigureCppSupportStep;
import com.android.tools.idea.npw.template.ChooseActivityTypeStep;
import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.npw.ui.WizardGallery;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBList;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.jetbrains.android.util.AndroidBundle.message;

/**
 * First page in the New Project wizard that allows user to select the Form Factor (Mobile, Wear, TV, etc) and its
 * Template ("Empty Activity", "Basic", "Nav Drawer", etc)
 * TODO: Add C++ entry
 * TODO: "No Activity" needs a Template Icon place holder
 */
public class ChooseAndroidProjectStep extends ModelWizardStep<NewProjectModel> {
  // To have the sequence specified by design, we hardcode the sequence.
  private final String[] ORDERED_ACTIVITY_NAMES = {
    "Basic Activity", "Empty Activity", "Bottom Navigation Activity", "Fullscreen Activity", "Master/Detail Flow",
    "Navigation Drawer Activity"
  };

  private final List<FormFactorInfo> myFormFactors = new ArrayList<>();

  private JPanel myRootPanel;
  private CommonTabbedPane myTabsPanel;
  private NewProjectModuleModel myNewProjectModuleModel;

  public ChooseAndroidProjectStep(@NotNull NewProjectModel model) {
    super(model, message("android.wizard.project.new.choose"));
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    myNewProjectModuleModel = new NewProjectModuleModel(getModel());

    return newArrayList(
      new ConfigureAndroidProjectStep(myNewProjectModuleModel, getModel()),
      new ConfigureCppSupportStep(getModel())
    );
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    populateFormFactors();

    for (FormFactorInfo formFactorInfo : myFormFactors) {
      ChooseAndroidProjectPanel<TemplateRenderer> tabPanel = formFactorInfo.tabPanel;
      myTabsPanel.addTab(formFactorInfo.formFactor.toString(), tabPanel.myRootPanel);

      tabPanel.myGallery.setDefaultAction(new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
          wizard.goForward();
        }
      });

      ListSelectionListener activitySelectedListener = selectionEvent -> {
        TemplateRenderer selectedTemplate = tabPanel.myGallery.getSelectedElement();
        if (selectedTemplate != null) {
          TemplateHandle template = selectedTemplate.getTemplate();
          tabPanel.myTemplateName.setText(template == null ? "" : template.getMetadata().getTitle());
          tabPanel.myTemplateDesc.setText(template == null ? "" : "<html>" + template.getMetadata().getDescription() + "</html>");
        }
      };

      tabPanel.myGallery.addListSelectionListener(activitySelectedListener);
      activitySelectedListener.valueChanged(null);
    }

    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @Override
  protected void onProceeding() {
    FormFactorInfo formFactorInfo = myFormFactors.get(myTabsPanel.getSelectedIndex());
    TemplateRenderer selectedTemplate = formFactorInfo.tabPanel.myGallery.getSelectedElement();

    myNewProjectModuleModel.getNewRenderTemplateModel().setTemplateHandle(selectedTemplate.myTemplate);
    myNewProjectModuleModel.getNewModuleModel().templateFile().setValue(formFactorInfo.templateFile);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @NotNull
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myTabsPanel;
  }

  private void populateFormFactors() {
    Map<FormFactor, FormFactorInfo> formFactorInfoMap = Maps.newTreeMap();
    TemplateManager manager = TemplateManager.getInstance();
    List<File> applicationTemplates = manager.getTemplatesInCategory(Template.CATEGORY_APPLICATION);

    for (File templateFile : applicationTemplates) {
      TemplateMetadata metadata = manager.getTemplateMetadata(templateFile);
      if (metadata == null || metadata.getFormFactor() == null) {
        continue;
      }
      FormFactor formFactor = FormFactor.get(metadata.getFormFactor());
      if (formFactor == FormFactor.GLASS && !AndroidSdkUtils.isGlassInstalled()) {
        // Only show Glass if you've already installed the SDK
        continue;
      }
      FormFactorInfo prevFormFactorInfo = formFactorInfoMap.get(formFactor);
      int templateMinSdk = metadata.getMinSdk();

      if (prevFormFactorInfo == null) {
        int minSdk = Math.max(templateMinSdk, formFactor.getMinOfflineApiLevel());
        ChooseAndroidProjectPanel<TemplateRenderer> tabPanel = new ChooseAndroidProjectPanel<>(createGallery(getTitle(), formFactor));
        formFactorInfoMap.put(formFactor, new FormFactorInfo(templateFile, formFactor, minSdk, tabPanel));
      }
      else if (templateMinSdk > prevFormFactorInfo.minSdk) {
        prevFormFactorInfo.minSdk = templateMinSdk;
        prevFormFactorInfo.templateFile = templateFile;
      }
    }

    myFormFactors.addAll(formFactorInfoMap.values());
    myFormFactors.sort(Comparator.comparing(f -> f.formFactor));
  }

  @NotNull
  private List<TemplateHandle> getFilteredTemplateHandles(@NotNull FormFactor formFactor) {
    List<TemplateHandle> templateHandles = TemplateManager.getInstance().getTemplateList(formFactor);

    if (formFactor == FormFactor.MOBILE) {
      Map<String, TemplateHandle> entryMap = templateHandles.stream().collect(toMap(it -> it.getMetadata().getTitle(), it -> it));
      return Arrays.stream(ORDERED_ACTIVITY_NAMES).map(it -> entryMap.get(it)).filter(Objects::nonNull).collect(toList());
    }

    return templateHandles;
  }

  @NotNull
  private ASGallery<TemplateRenderer> createGallery(@NotNull String title, @NotNull FormFactor formFactor) {
    List<TemplateHandle> templateHandles = getFilteredTemplateHandles(formFactor);

    List<TemplateRenderer> templateRenderers = Lists.newArrayListWithExpectedSize(templateHandles.size() + 1);
    templateRenderers.add(new TemplateRenderer(null)); // "Add No Activity" entry
    for (TemplateHandle templateHandle : templateHandles) {
      templateRenderers.add(new TemplateRenderer(templateHandle));
    }

    TemplateRenderer[] listItems = templateRenderers.toArray(new TemplateRenderer[templateRenderers.size()]);

    ASGallery<TemplateRenderer> gallery = new WizardGallery<>(title, TemplateRenderer::getImage, TemplateRenderer::getLabel);
    gallery.setModel(JBList.createDefaultListModel((Object[])listItems));
    gallery.setSelectedIndex(getDefaultSelectedTemplateIndex(listItems));

    return gallery;
  }

  private static int getDefaultSelectedTemplateIndex(@NotNull TemplateRenderer[] templateRenderers) {
    for (int i = 0; i < templateRenderers.length; i++) {
      if (templateRenderers[i].getLabel().equals("Empty Activity")) {
        return i;
      }
    }

    // Default template not found. Instead, return the index to the first valid template renderer (e.g. skip "Add No Activity", etc.)
    for (int i = 0; i < templateRenderers.length; i++) {
      if (templateRenderers[i].getTemplate() != null) {
        return i;
      }
    }

    assert false : "No valid Template found";
    return 0;
  }

  private static class FormFactorInfo {
    @NotNull final FormFactor formFactor;
    @NotNull final ChooseAndroidProjectPanel<TemplateRenderer> tabPanel;
    @NotNull File templateFile;
    int minSdk;

    FormFactorInfo(@NotNull File templateFile, @NotNull FormFactor formFactor, int minSdk,
                   @NotNull ChooseAndroidProjectPanel<TemplateRenderer> tabPanel) {

      this.templateFile = templateFile;
      this.formFactor = formFactor;
      this.minSdk = minSdk;
      this.tabPanel = tabPanel;
    }
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
    String getLabel() {
      String title = myTemplate == null ? message("android.wizard.gallery.item.add.no.activity") : myTemplate.getMetadata().getTitle();
      return title == null ? "" : title;
    }

    @NotNull
    @Override
    public String toString() {
      return getLabel();
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
