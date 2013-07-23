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
package com.android.tools.idea.wizard;

import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.google.common.io.Files;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.templates.TemplateMetadata.*;

/**
 * ChooseTemplateStep is a wizard page that shows the user a list of templates of a given type and lets the user choose one.
 */
public class ChooseTemplateStep extends TemplateWizardStep implements ListSelectionListener {
  private static final Logger LOG = Logger.getInstance("#" + ChooseTemplateStep.class.getName());
  private final TemplateChangeListener myTemplateChangeListener;

  private JPanel myPanel;
  private JBList myTemplateList;
  private ImageComponent myTemplateImage;
  private JLabel myDescription;
  private JLabel myError;
  private int myPreviousSelection = -1;

  public interface TemplateChangeListener {
    void templateChanged();
  }

  public ChooseTemplateStep(TemplateWizardState state, String templateCategory, @Nullable Project project, @Nullable Icon sidePanelIcon,
                            UpdateListener updateListener, TemplateChangeListener templateChangeListener) {
    super(state, project, sidePanelIcon, updateListener);
    myTemplateChangeListener = templateChangeListener;

    myTemplateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    TemplateManager manager = TemplateManager.getInstance();
    List<File> templates = manager.getTemplates(templateCategory);
    List<MetadataListItem> metadataList = new ArrayList<MetadataListItem>(templates.size());
    for (int i = 0, n = templates.size(); i < n; i++) {
      File template = templates.get(i);
      TemplateMetadata metadata = manager.getTemplate(template);
      if (metadata == null || !metadata.isSupported()) {
        continue;
      }
      metadataList.add(new MetadataListItem(template, metadata));
    }

    myTemplateList.setModel(JBList.createDefaultListModel(ArrayUtil.toObjectArray(metadataList)));
    if (!metadataList.isEmpty()) {
      myTemplateList.setSelectedIndex(0);
    }
    myTemplateList.addListSelectionListener(this);
    validate();
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
      MetadataListItem template = (MetadataListItem)myTemplateList.getModel().getElementAt(index);

      if (template != null) {
        myTemplateState.setTemplateLocation(template.myTemplate);
        myTemplateState.convertApisToInt();
        String thumb = template.myMetadata.getThumbnailPath();
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
        }
        setDescriptionHtml(template.myMetadata.getDescription());
        int minSdk = template.myMetadata.getMinSdk();
        Integer minApi = (Integer)myTemplateState.get(ATTR_MIN_API);
        if (minApi != null && minSdk > minApi) {
          setErrorHtml(String.format("The activity %s has a minimum SDK level of %d.", template.myMetadata.getTitle(), minSdk));
          return false;
        }
        int minBuildApi = template.myMetadata.getMinBuildApi();
        Integer buildApi = (Integer)myTemplateState.get(ATTR_BUILD_API);
        if (buildApi != null && minSdk > buildApi) {
          setErrorHtml(String.format("The activity %s has a minimum build API level of %d.", template.myMetadata.getTitle(), minBuildApi));
          return false;
        }
        if (myTemplateChangeListener != null && myPreviousSelection != index) {
          myPreviousSelection = index;
          myTemplateChangeListener.templateChanged();
        }
      }
    }
    return true;
  }

  @Override
  protected JLabel getDescription() {
    return myDescription;
  }

  @Override
  protected JLabel getError() {
    return myError;
  }

  private static class MetadataListItem {
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
  }
}
