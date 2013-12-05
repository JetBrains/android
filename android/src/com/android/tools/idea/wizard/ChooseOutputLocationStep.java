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

import com.android.builder.model.*;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.intellij.ide.structureView.impl.java.JavaFileTreeModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBList;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This step allows the user to select a build variant and provides a preview
 * of the assets that are about to be created.
 */
public class ChooseOutputLocationStep extends TemplateWizardStep {

  public static final String ATTR_TARGET_MODULE = "targetModule";
  public static final String ATTR_TARGET_VARIANT = "targetVariant";
  public static final String ATTR_OUTPUT_FOLDER = "outputFolder";

  private JComboBox myModuleComboBox;
  private JLabel myDescription;
  private JPanel myPanel;
  private JComboBox myVariantComboBox;
  private Tree myOutputPreviewTree;
  private JLabel myResDirLabel;

  private Module myModule;
  private Module[] myModuleArray;

  public ChooseOutputLocationStep(@NotNull TemplateWizardState state,
                                  @NotNull Project project,
                                  @Nullable Icon sidePanelIcon,
                                  UpdateListener updateListener,
                                  Module module) {
    super(state, project, sidePanelIcon, updateListener);

    myModule = module;

    init();
  }

  public void init() {
    setUpUiComponents();
  }

  @NotNull
  @Override
  protected JLabel getDescription() {
    return myDescription;
  }

  @NotNull
  @Override
  protected JLabel getError() {
    return myDescription;
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  private void setUpUiComponents() {
    // Populate the Module chooser list
    myModuleArray = ModuleManager.getInstance(myProject).getModules();
    populateComboBox(myModuleComboBox, myModuleArray);
    register(ATTR_TARGET_MODULE, myModuleComboBox);
    register(ATTR_TARGET_VARIANT, myVariantComboBox);

    myOutputPreviewTree.setBorder(BorderFactory.createLoweredBevelBorder());
  }

  @Override
  public void updateParams() {
    super.updateParams();
    myModule = myModuleArray[myModuleComboBox.getSelectedIndex()];
  }

  @Override
  public void deriveValues() {
    if (myIdsWithNewValues.contains(ATTR_TARGET_MODULE)) {
      // Populate the Build Flavor and Build Type lists
      AndroidFacet facet = AndroidFacet.getInstance(myModule);
      if (facet == null) {
        return;
      }

      IdeaAndroidProject gradleProject = facet.getIdeaAndroidProject();
      if (gradleProject != null) {
        show(myVariantComboBox, myResDirLabel);
        AndroidProject androidProject = gradleProject.getDelegate();

        DefaultComboBoxModel comboBoxModel = new DefaultComboBoxModel();
        String moduleRoot = FileUtil.toSystemIndependentName(AndroidRootUtil.getModuleDirPath(facet.getModule()));

        Collection<ProductFlavorContainer> flavors = androidProject.getProductFlavors();
        for (ProductFlavorContainer pfc : flavors) {
          for (File f : pfc.getSourceProvider().getResDirectories()) {
            String resPath = FileUtil.getRelativePath(moduleRoot,
                                                      FileUtil.toSystemIndependentName(f.getPath()), '/');
            String name = String.format("Flavor-%s (%s)", pfc.getProductFlavor().getName(), resPath);
            comboBoxModel.addElement(new ComboBoxItem(f, name, 1, 1));
          }
        }

        Collection<BuildTypeContainer> buildTypes = androidProject.getBuildTypes();
        for (BuildTypeContainer btc : buildTypes) {
          for (File f : btc.getSourceProvider().getResDirectories()) {
            String resPath = FileUtil.getRelativePath(moduleRoot,
                                                      FileUtil.toSystemIndependentName(f.getPath()), '/');
            String name = String.format("BuildType-%s (%s)", btc.getBuildType().getName(), resPath);
            comboBoxModel.addElement(new ComboBoxItem(f, name, 1, 1));
          }
        }
        myVariantComboBox.setModel(comboBoxModel);
      } else {
        hide(myVariantComboBox, myResDirLabel);
        VirtualFile resourceDir = facet.getPrimaryResourceDir();
        if (resourceDir != null) {
          myTemplateState.put(ATTR_TARGET_VARIANT, new File(resourceDir.getPath()));
        } else {
          return;
        }
      }
      myIdsWithNewValues.add(ATTR_TARGET_VARIANT);
    }

    if (myIdsWithNewValues.contains(ATTR_TARGET_VARIANT)) {
      File resDir = (File)myTemplateState.get(ATTR_TARGET_VARIANT);
      if (resDir == null) {
        resDir = (File)((ComboBoxItem)myVariantComboBox.getSelectedItem()).id;
      }
      if (resDir != null) {
        // Populate the output file tree
        FileTreeModel myTreeModel = new FileTreeModel(resDir);
        myTemplateState.put(ATTR_OUTPUT_FOLDER, resDir);

        try {
        Map<String, Map<String, BufferedImage>> images = ((AssetStudioWizardState)myTemplateState).generateImages(false);
          for (String density : images.keySet()) {
            Map<String, BufferedImage> filenameMap = images.get(density);
            for (String filename : filenameMap.keySet()) {
              Image image = filenameMap.get(filename);
              Icon ic = null;
              if (image != null) {
                while (image.getHeight(null) > 200) {
                  image = image.getScaledInstance(image.getWidth(null)/2, image.getHeight(null)/2,Image.SCALE_SMOOTH);
                }
                ic = new ImageIcon(image);
              }
              myTreeModel.addFile(new File(resDir, filename), ic);
            }
          }
        } catch (Exception e) {
          // pass
        }
        myOutputPreviewTree.setModel(myTreeModel);
        myOutputPreviewTree.setCellRenderer(new FileTreeCellRenderer());
      }
    }
  }
}
