/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.tools.idea.ui.ASGallery;
import com.android.tools.idea.wizard.template.TemplateWizardState;
import com.android.tools.idea.wizard.template.TemplateWizardStep;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.templates.TemplateMetadata.*;

/**
 * ChooseTemplateStep is a wizard page that shows the user a list of templates
 * of a given type and lets the user choose one.
 */
public class TemplateGalleryStep extends TemplateWizardStep
    implements ListSelectionListener {
  private final TemplateChangeListener myTemplateChangeListener;

  private JPanel myPanel;
  private JLabel myDescription;
  private JLabel myError;
  private ASGallery<MetadataListItem> myGallery;
  private JBScrollPane myGalleryScroller;
  private int myPreviousSelection = -1;

  public TemplateGalleryStep(TemplateWizardState state,
                             String templateCategory,
                             @Nullable Project project,
                             @Nullable Module module,
                             @Nullable Icon sidePanelIcon,
                             UpdateListener updateListener,
                             @Nullable TemplateChangeListener templateChangeListener) {
    this(state, templateCategory, project, module, sidePanelIcon, updateListener, templateChangeListener, null);
  }

  public TemplateGalleryStep(TemplateWizardState state,
                             String templateCategory,
                             @Nullable Project project,
                             @Nullable Module module,
                             @Nullable Icon sidePanelIcon,
                             UpdateListener updateListener,
                             @Nullable TemplateChangeListener templateChangeListener,
                             @Nullable Set<String> excluded) {
    super(state, project, module, sidePanelIcon, updateListener);
    myGallery.setImageProvider(new TemplateImageProvider());
    myGallery.setLabelProvider(new TemplateLabelProvider());
    myGallery.setThumbnailSize(JBUI.size(256, 256));
    myTemplateChangeListener = templateChangeListener;

    if (templateCategory != null) {
      List<MetadataListItem> templates = getTemplateList(state, templateCategory, excluded);
      setListData(templates);
      validate();
    }
  }

  /**
   * Search the given folder for a list of templates and populate the display list.
   */
  protected static List<MetadataListItem> getTemplateList(TemplateWizardState state,
                                                          String templateFolder,
                                                          @Nullable Set<String> excluded) {
    TemplateManager manager = TemplateManager.getInstance();
    List<File> templates = manager.getTemplates(templateFolder);
    List<MetadataListItem> metadataList = new ArrayList<MetadataListItem>(templates.size());
    for (File template : templates) {
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

  private void createUIComponents() {
    myGalleryScroller = new JBScrollPane();
    myGalleryScroller.setBorder(BorderFactory.createLoweredBevelBorder());
  }

  /**
   * Populate the JBList of templates from the given list of metadata.
   */
  protected void setListData(List<MetadataListItem> metadataList) {
    myGallery.setModel(JBList.createDefaultListModel(ArrayUtil.toObjectArray(metadataList)));
    if (!metadataList.isEmpty()) {
      myGallery.setSelectedIndex(0);
    }
    myGallery.addListSelectionListener(this);
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myGallery;
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
    setDescriptionHtml("");
    MetadataListItem templateListItem = getSelectedTemplate();
    if (templateListItem != null) {
      myTemplateState.setTemplateLocation(templateListItem.getTemplateFile());
      Template.convertApisToInt(myTemplateState.getParameters());
      setDescriptionHtml(templateListItem.myMetadata.getDescription());
      int minSdk = templateListItem.myMetadata.getMinSdk();
      Integer minApi = (Integer)myTemplateState.get(ATTR_MIN_API_LEVEL);
      if (minApi != null && minSdk > minApi) {
        setErrorHtml(String.format("The component %s has a minimum SDK level of %d.", templateListItem.myMetadata.getTitle(), minSdk));
        return false;
      }
      int minBuildApi = templateListItem.myMetadata.getMinBuildApi();
      Integer buildApi = (Integer)myTemplateState.get(ATTR_BUILD_API);
      if (buildApi != null && minSdk > buildApi) {
        setErrorHtml(
          String.format("The component %s has a minimum build API level of %d.", templateListItem.myMetadata.getTitle(), minBuildApi));
        return false;
      }
      int index = myGallery.getSelectedIndex();
      if (myTemplateChangeListener != null && myPreviousSelection != index) {
        myPreviousSelection = index;
        myTemplateChangeListener.templateChanged(templateListItem.toString());
      }
    }
    return true;
  }

  @Nullable
  private MetadataListItem getSelectedTemplate() {
    return myGallery.getSelectedElement();
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

  public interface TemplateChangeListener {
    void templateChanged(String templateName);
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
      return Strings.nullToEmpty(myMetadata.getTitle());
    }

    /**
     * Get the folder containing this template
     */
    public File getTemplateFile() {
      return myTemplate;
    }

    @Override
    public int compareTo(@NotNull MetadataListItem other) {
      return StringUtil.naturalCompare(this.myMetadata.getTitle(),
                                       other.myMetadata.getTitle());
    }
  }

  private static final class TemplateImageProvider implements Function<MetadataListItem, Image> {
    @Override
    public Image apply(MetadataListItem input) {
      String thumb = input.myMetadata.getThumbnailPath();
      if (thumb != null && !thumb.isEmpty()) {
        try {
          File file = new File(input.myTemplate, thumb.replace('/', File.separatorChar));
          if (file.isFile()) {
            return ImageIO.read(file);
          }
          else {
            return null;
          }
        }
        catch (IOException e) {
          Logger.getInstance(getClass()).warn(e);
        }
      }
      return null;
    }
  }

  private static final class TemplateLabelProvider implements Function<MetadataListItem, String> {
    @Override
    public String apply(MetadataListItem input) {
      return input.myMetadata.getTitle();
    }
  }


}
