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
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

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
  private boolean myComputeNewSourceSet;

  private static final TreeModel EMPTY_MODEL = new DefaultTreeModel(null);
  private final FileTreeCellRenderer myFileTreeRenderer = new FileTreeCellRenderer();
  private FileTreeModel myTreeModel;

  private AssetStudioAssetGenerator myAssetGenerator;

  public ChooseOutputLocationStep(@NotNull TemplateWizardState state,
                                  @NotNull Project project,
                                  @Nullable Icon sidePanelIcon,
                                  UpdateListener updateListener,
                                  Module module) {
    super(state, project, sidePanelIcon, updateListener);

    myModule = module;
    myAssetGenerator = new AssetStudioAssetGenerator(state);

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

  @Override
  public void updateStep() {
    super.updateStep();
    myComputeNewSourceSet = true;
    update();
  }

  private void setUpUiComponents() {
    // Populate the Module chooser list
    myModuleArray = ModuleManager.getInstance(myProject).getModules();
    populateComboBox(myModuleComboBox, myModuleArray);
    register(ATTR_TARGET_MODULE, myModuleComboBox);
    register(ATTR_TARGET_VARIANT, myVariantComboBox);

    if (myModule != null) {
      int index = -1;
      for (int i = 0; i < myModuleArray.length; ++i) {
        if (myModuleArray[i].equals(myModule)) {
          index = i;
          break;
        }
      }
      if (index != -1) {
        myModuleComboBox.setSelectedIndex(index);
      }
    }

    myOutputPreviewTree.setBorder(BorderFactory.createLoweredBevelBorder());
    // Tell the tree to ask the TreeCellRenderer for an individual height for each cell.
    myOutputPreviewTree.setRowHeight(-1);
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
        // Clear variant list
        myVariantComboBox.setModel(new DefaultComboBoxModel());
        // Remove entries from the file tree preview
        myOutputPreviewTree.setModel(EMPTY_MODEL);
        return;
      }

      IdeaAndroidProject gradleProject = facet.getIdeaAndroidProject();
      if (gradleProject != null) {
        show(myVariantComboBox, myResDirLabel);
        AndroidProject androidProject = gradleProject.getDelegate();

        DefaultComboBoxModel comboBoxModel = new DefaultComboBoxModel();
        String moduleRoot = FileUtil.toSystemIndependentName(AndroidRootUtil.getModuleDirPath(facet.getModule()));

        Set<String> knownResPaths = Sets.newHashSet();

        // Add all the project flavors as potential source sets
        Collection<ProductFlavorContainer> flavors = androidProject.getProductFlavors();
        for (ProductFlavorContainer pfc : flavors) {
          for (File f : pfc.getSourceProvider().getResDirectories()) {
            String resPath = FileUtil.getRelativePath(moduleRoot,
                                                      FileUtil.toSystemIndependentName(f.getPath()), '/');
            String name = String.format("Flavor-%s (%s)", pfc.getProductFlavor().getName(), resPath);
            comboBoxModel.addElement(new ComboBoxItem(f, name, 1, 1));
            knownResPaths.add(resPath);
          }
        }

        // Add all the build types as potential source sets
        Collection<BuildTypeContainer> buildTypes = androidProject.getBuildTypes();
        for (BuildTypeContainer btc : buildTypes) {
          for (File f : btc.getSourceProvider().getResDirectories()) {
            String resPath = FileUtil.getRelativePath(moduleRoot,
                                                      FileUtil.toSystemIndependentName(f.getPath()), '/');
            String name = String.format("BuildType-%s (%s)", btc.getBuildType().getName(), resPath);
            comboBoxModel.addElement(new ComboBoxItem(f, name, 1, 1));
            knownResPaths.add(resPath);
          }
        }

        // Add the main source set if it hasn't been taken care of yet
        for (VirtualFile f : facet.getMainIdeaSourceSet().getResDirectories()) {
          String resPath = FileUtil.getRelativePath(moduleRoot,
                                                    FileUtil.toSystemIndependentName(f.getPath()), '/');
          if (!knownResPaths.contains(resPath)) {
            String name = String.format("Main Source Set (%s)", resPath);
            comboBoxModel.insertElementAt(new ComboBoxItem(new File(f.getPath()), name, 1, 1), 0);
            knownResPaths.add(resPath);
          }
        }

        myVariantComboBox.setModel(comboBoxModel);
        myVariantComboBox.setSelectedIndex(0);
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

    if (myIdsWithNewValues.contains(ATTR_TARGET_VARIANT) || myComputeNewSourceSet) {
      File resDir = (File)myTemplateState.get(ATTR_TARGET_VARIANT);
      if (resDir == null) {
        Object selectedVariant = myVariantComboBox.getSelectedItem();
        if (selectedVariant instanceof ComboBoxItem) {
          resDir = (File)((ComboBoxItem)selectedVariant).id;
        }
      }
      if (resDir != null) {
        // Populate the output file tree
        myTreeModel = new FileTreeModel(resDir);
        myTemplateState.put(ATTR_OUTPUT_FOLDER, resDir);

        try {
        Map<String, Map<String, BufferedImage>> images = myAssetGenerator.generateImages(false);
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
              myTreeModel.forceAddFile(new File(resDir, filename), ic);
            }
          }
        } catch (Exception e) {
          // pass
        }
        myOutputPreviewTree.setModel(myTreeModel);
        myOutputPreviewTree.setCellRenderer(myFileTreeRenderer);
      }
    }
    myComputeNewSourceSet = false;
  }

  @Override
  public boolean validate() {
    if (!super.validate()) {
      return false;
    }

    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    if (facet == null) {
      setErrorHtml("The selected module does not have an Android Facet. Please choose an Android module");
      return false;
    }

    if (myTreeModel.hasConflicts()) {
      setErrorHtml("Some existing files will be overwritten by this operation. Files which replace existing files are marked" +
                   " red in the preview above.");
    }

    return true;
  }
}
