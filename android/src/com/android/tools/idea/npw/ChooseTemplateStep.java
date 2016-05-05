/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.npw;

import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.ui.ImageComponent;
import com.android.tools.idea.wizard.template.TemplateWizardState;
import com.android.tools.idea.wizard.template.TemplateWizardStep;
import com.google.common.collect.ComparisonChain;
import com.google.common.io.Files;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.templates.TemplateMetadata.*;

/**
 * ChooseTemplateStep is a wizard page that shows the user a list of templates of a given type and lets the user choose one.
 *
 * Deprecated. Use {@link TemplateGalleryStep} instead.
 */
@Deprecated
public class ChooseTemplateStep extends TemplateWizardStep implements ListSelectionListener {
  private static final Logger LOG = Logger.getInstance("#" + ChooseTemplateStep.class.getName());
  private final TemplateChangeListener myTemplateChangeListener;

  private JPanel myPanel;
  protected JBList myTemplateList;
  private ImageComponent myTemplateImage;
  private JLabel myDescription;
  private JLabel myError;
  private int myPreviousSelection = -1;

  public interface TemplateChangeListener {
    void templateChanged(String templateName);
  }

  public ChooseTemplateStep(TemplateWizardState state, String templateCategory, @Nullable Project project, @Nullable Module module,
                            @Nullable Icon sidePanelIcon, UpdateListener updateListener,
                            @Nullable TemplateChangeListener templateChangeListener) {
    this(state, templateCategory, project, module, sidePanelIcon, updateListener, templateChangeListener, null);
  }

  public ChooseTemplateStep(TemplateWizardState state, String templateCategory, @Nullable Project project, @Nullable Module module,
                            @Nullable Icon sidePanelIcon, UpdateListener updateListener,
                            @Nullable TemplateChangeListener templateChangeListener, @Nullable Set<String> excluded) {
    super(state, project, module, sidePanelIcon, updateListener);
    myTemplateChangeListener = templateChangeListener;
    myTemplateList.setBorder(BorderFactory.createLoweredBevelBorder());

    if (templateCategory != null) {
      List<MetadataListItem> templates = getTemplateList(state, templateCategory, excluded);
      setListData(templates);
      validate();
    }
  }


  /**
   * Search the given folder for a list of templates and populate the display list.
   */
  protected static List<MetadataListItem> getTemplateList(TemplateWizardState state, String templateFolder, @Nullable Set<String> excluded) {
    TemplateManager manager = TemplateManager.getInstance();
    List<File> templates = manager.getTemplates(templateFolder);
    return getTemplateList(state, templates, excluded);
  }

  /**
   * Retrieve the metadata for the given list of template files, excluding the files from the excluded set.
   */
  protected static List<MetadataListItem> getTemplateList(TemplateWizardState state, List<File> templateFiles, @Nullable Set<String> excluded) {
    TemplateManager manager = TemplateManager.getInstance();
    List<MetadataListItem> metadataList = new ArrayList<>(templateFiles.size());
    for (File template : templateFiles) {
      TemplateMetadata metadata = manager.getTemplateMetadata(template);
      if (metadata == null || !metadata.isSupported()) {
        continue;
      }
      // If we're trying to create a launchable activity, don't include templates that
      // lack the isLauncher parameter.
      Boolean isLauncher = (Boolean)state.get(ATTR_IS_LAUNCHER);
      if (isLauncher != null && isLauncher && metadata.getParameter(TemplateMetadata.ATTR_IS_LAUNCHER) == null) {
        continue;
      }

      // Don't include this template if it's been excluded
      if (excluded != null && excluded.contains(metadata.getTitle())) {
        continue;
      }

      metadataList.add(new MetadataListItem(template, metadata));
    }
    Collections.sort(metadataList);
    return metadataList;
  }

  /**
   * Populate the JBList of templates from the given list of metadata.
   */
  protected void setListData(List<MetadataListItem> metadataList) {
    myTemplateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTemplateList.setModel(JBList.createDefaultListModel(ArrayUtil.toObjectArray(metadataList)));
    if (!metadataList.isEmpty()) {
      myTemplateList.setSelectedIndex(0);
    }
    myTemplateList.addListSelectionListener(this);
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTemplateList;
  }

  @Override
  public void valueChanged(ListSelectionEvent listSelectionEvent) {
    update();
  }

  @Override
  public boolean validate() {
    if (!super.validate()) {
      return false;
    }
    int index = myTemplateList.getSelectedIndex();
    myTemplateImage.setIcon(null);
    setDescriptionHtml("");
    if (index != -1) {
      MetadataListItem templateListItem = (MetadataListItem)myTemplateList.getModel().getElementAt(index);

      if (templateListItem != null) {
        myTemplateState.setTemplateLocation(templateListItem.getTemplateFile());
        Template.convertApisToInt(myTemplateState.getParameters());
        String thumb = templateListItem.myMetadata.getThumbnailPath();
        if (thumb != null && !thumb.isEmpty()) {
          File file = new File(myTemplateState.myTemplate.getRootPath(), thumb.replace('/', File.separatorChar));
          try {
            byte[] bytes = Files.toByteArray(file);
            ImageIcon previewImage = new ImageIcon(bytes);
            myTemplateImage.setIcon(previewImage);
          }
          catch (IOException e) {
            LOG.warn(e);
          }
        } else {
          myTemplateImage.setIcon(AndroidIcons.Wizards.DefaultTemplate);
        }
        setDescriptionHtml(templateListItem.getDescription());
        String apiValidationError = validateApiLevels(templateListItem.myMetadata, myTemplateState);
        if (apiValidationError != null) {
          setErrorHtml(apiValidationError);
          return false;
        }
        if (myTemplateChangeListener != null && myPreviousSelection != index) {
          myPreviousSelection = index;
          myTemplateChangeListener.templateChanged(templateListItem.toString());
        }
      }
    }
    return true;
  }

  @Nullable
  protected static String validateApiLevels(@Nullable TemplateMetadata metadata, @NotNull TemplateWizardState templateState) {
    // If this is not a real template, but a stub, no API level validation is necessary.
    // If the given template has category="Application," then we're selecting a template to create a new module, in which case
    // the current API level of the project is irrelevant since the new module will have its own minSDK/minBuild API levels.
    if (metadata == null || metadata.getCategory() != null && metadata.getCategory().equals(Template.CATEGORY_APPLICATION)) {
      return null;
    }
    int minSdk = metadata.getMinSdk();
    Integer minApi = (Integer)templateState.get(ATTR_MIN_API_LEVEL);
    if (minApi != null && minSdk > minApi) {
      return String.format("The component %s has a minimum SDK level of %d.", metadata.getTitle(), minSdk);
    }
    int minBuildApi = metadata.getMinBuildApi();
    Integer buildApi = (Integer)templateState.get(ATTR_BUILD_API);
    if (buildApi != null && minSdk > buildApi) {
      return String.format("The component %s has a minimum build API level of %d.", metadata.getTitle(), minBuildApi);
    }

    return null;
  }

  @NotNull
  @Override
  protected JLabel getDescription() {
    return myDescription;
  }

  @NotNull
  @Override
  protected JLabel getError() {
    return myError;
  }

  @Nullable
  public TemplateMetadata getSelectedTemplateMetadata() {
    MetadataListItem templateListItem = (MetadataListItem)myTemplateList.getSelectedValue();
    if (templateListItem != null) {
      return templateListItem.myMetadata;
    }
    return null;
  }

  public static class MetadataListItem implements Comparable<MetadataListItem> {
    private TemplateMetadata myMetadata;
    private final File myTemplate;

    public MetadataListItem(@NotNull File template, @NotNull TemplateMetadata metadata) {
      myTemplate = template;
      myMetadata = metadata;
    }

    @Override
    public String toString() {
      return myMetadata.getTitle();
    }

    /**
     * Get the folder containing this template
     */
    public File getTemplateFile() {
      return myTemplate;
    }

    @Override
    public int compareTo(@NotNull MetadataListItem other) {
      return ComparisonChain.start()
        .compare(this.myMetadata.getTitle(), other.myMetadata.getTitle())
        .result();
    }

    @Nullable
    public String getDescription() {
      return myMetadata.getDescription();
    }
  }
}
