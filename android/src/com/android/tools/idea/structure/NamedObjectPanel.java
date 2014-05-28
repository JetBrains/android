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
package com.android.tools.idea.structure;

import com.android.builder.model.*;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.NamedObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class NamedObjectPanel extends BuildFilePanel implements DocumentListener, ListSelectionListener {
  private static final String DEFAULT_CONFIG = "defaultConfig";
  private static final BuildFileKey[] DEFAULT_CONFIG_KEYS =
      { BuildFileKey.PACKAGE_NAME, BuildFileKey.VERSION_CODE, BuildFileKey.VERSION_NAME, BuildFileKey.MIN_SDK_VERSION,
        BuildFileKey.TARGET_SDK_VERSION, BuildFileKey.PACKAGE_NAME, BuildFileKey.TEST_INSTRUMENTATION_RUNNER };

  private JPanel myPanel;
  private JBList myList;
  private JTextField myObjectName;
  private JSplitPane mySplitPane;
  private JPanel myRightPane;
  private KeyValuePane myDetailsPane;
  private final BuildFileKey myBuildFileKey;
  private final String myNewItemName;
  private final DefaultListModel myListModel;
  private NamedObject myCurrentObject;
  private Collection<NamedObject> myModelOnlyObjects = Lists.newArrayList();
  private Map<String, Map<BuildFileKey, Object>> myModelObjects = Maps.newHashMap();

  public NamedObjectPanel(@NotNull Project project, @NotNull String moduleName, @NotNull BuildFileKey buildFileKey,
                          @NotNull String newItemName) {
    super(project, moduleName);
    myBuildFileKey = buildFileKey;
    myNewItemName = newItemName;
    myListModel = new DefaultListModel();
    myObjectName.getDocument().addDocumentListener(this);

    myList = new JBList();
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.addListSelectionListener(this);
    myList.setModel(myListModel);
    myList.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList jList, Object o, int i, boolean b, boolean b2) {
        return super.getListCellRendererComponent(jList, ((NamedObject)o).getName(), i, b, b2);
      }
    });
    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myList);
    decorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        addObject();
      }
    });
    decorator.setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton anActionButton) {
        removeObject();
      }
    });
    decorator.disableUpDownActions();
    mySplitPane.setLeftComponent(decorator.createPanel());
    mySplitPane.setDividerLocation(200);
    myRightPane.setBorder(IdeBorderFactory.createBorder());
  }

  @Override
  public void init() {
    super.init();
    if (myGradleBuildFile == null) {
      return;
    }
    List<NamedObject> namedObjects = (List<NamedObject>)myGradleBuildFile.getValue(myBuildFileKey);
    if (namedObjects != null) {
      for (NamedObject object : namedObjects) {
        myListModel.addElement(object);
      }
    }
    // If this is a flavor panel, add a synthetic flavor entry for defaultConfig.
    if (myBuildFileKey == BuildFileKey.FLAVORS) {
      GrStatementOwner defaultConfig = myGradleBuildFile.getClosure(BuildFileKey.DEFAULT_CONFIG.getPath());
      NamedObject obj = new NamedObject("defaultConfig");
      if (defaultConfig != null) {
        for (BuildFileKey key : DEFAULT_CONFIG_KEYS) {
          obj.setValue(key, myGradleBuildFile.getValue(defaultConfig, key));
        }
      }
      myListModel.addElement(obj);
    }

    NamedObject.Factory objectFactory = (NamedObject.Factory)myBuildFileKey.getValueFactory();
    if (objectFactory == null) {
      throw new IllegalArgumentException("Can't instantiate a NamedObjectPanel for BuildFileKey " + myBuildFileKey.toString());
    }
    Collection<BuildFileKey> properties = objectFactory.getProperties();

    // Query the model for its view of the world, and merge it with the build file-based view.
    for (NamedObject obj : getObjectsFromModel(properties)) {
      boolean found = false;
      for (int i = 0; i < myListModel.size(); i++) {
        if (((NamedObject)myListModel.get(i)).getName().equals(obj.getName())) {
          found = true;
        }
      }
      if (!found) {
        NamedObject namedObject = new NamedObject(obj.getName());
        myListModel.addElement(namedObject);

        // Keep track of objects that are only in the model and not in the build file. We want to avoid creating them in the build file
        // unless some value in them is changed to non-default.
        myModelOnlyObjects.add(namedObject);
      }
      myModelObjects.put(obj.getName(), obj.getValues());
    }
    myList.updateUI();

    myDetailsPane.init(myGradleBuildFile, properties);
    if (myListModel.size() > 0) {
      myList.setSelectedIndex(0);
    }
    updateUiFromCurrentObject();
  }

  @Override
  public void apply() {
    if (!myModified ||  myGradleBuildFile == null) {
      return;
    }
    List<NamedObject> objects = Lists.newArrayList();
    for (int i = 0; i < myListModel.size(); i++) {
      NamedObject obj = (NamedObject)myListModel.get(i);
      // Save the defaultConfig separately and don't write it out as a regular flavor.
      if (myBuildFileKey == BuildFileKey.FLAVORS && obj.getName().equals(DEFAULT_CONFIG)) {
        GrStatementOwner defaultConfig = myGradleBuildFile.getClosure(BuildFileKey.DEFAULT_CONFIG.getPath());
        if (defaultConfig == null) {
          myGradleBuildFile.setValue(BuildFileKey.DEFAULT_CONFIG, "{}");
          defaultConfig = myGradleBuildFile.getClosure(BuildFileKey.DEFAULT_CONFIG.getPath());
        }
        assert defaultConfig != null;
        for (BuildFileKey key : DEFAULT_CONFIG_KEYS) {
          Object value = obj.getValue(key);
          if (value != null) {
            myGradleBuildFile.setValue(defaultConfig, key, value);
          } else {
            myGradleBuildFile.removeValue(defaultConfig, key);
          }
        }
      } else if (myModelOnlyObjects.contains(obj) && isObjectEmpty(obj)) {
        // If this object wasn't in the build file to begin with and doesn't have non-default values, don't write it out.
        continue;
      } else {
        objects.add(obj);
      }
    }
    myGradleBuildFile.setValue(myBuildFileKey, objects);

    myModified = false;
    myDetailsPane.clearModified();
  }

  private static boolean isObjectEmpty(NamedObject obj) {
    for (Object o : obj.getValues().values()) {
      if (o != null) {
        return false;
      }
    }
    return true;
  }

  private void addObject() {
    int num = 1;
    String name = myNewItemName;
    while (getNamedItem(name) != null) {
      name = myNewItemName + num++;
    }
    myListModel.addElement(new NamedObject(name));
    myList.setSelectedIndex(myListModel.size() - 1);
    myList.updateUI();
    myModified = true;
  }

  private void removeObject() {
    int selectedIndex = myList.getSelectedIndex();
    if (selectedIndex < 0) {
      return;
    }
    myListModel.remove(selectedIndex);
    myList.setSelectedIndex(Math.max(0, Math.min(selectedIndex, myListModel.size() - 1)));
    myList.updateUI();
    myModified = true;
  }

  @Nullable
  private NamedObject getNamedItem(@NotNull String name) {
    for (int i = 0; i < myListModel.size(); i++) {
      NamedObject object = (NamedObject)myListModel.get(i);
      if (object.getName().equals(name)) {
        return object;
      }
    }
    return null;
  }

  @Override
  public void insertUpdate(@NotNull DocumentEvent documentEvent) {
    updateCurrentObjectFromUi();
  }

  @Override
  public void removeUpdate(@NotNull DocumentEvent documentEvent) {
    updateCurrentObjectFromUi();
  }

  @Override
  public void changedUpdate(@NotNull DocumentEvent documentEvent) {
    updateCurrentObjectFromUi();
  }

  @Override
  protected void addItems(@NotNull JPanel parent) {
    add(myPanel, BorderLayout.CENTER);
  }

  @Override
  public boolean isModified() {
    return myModified || myDetailsPane.isModified();
  }

  @Override
  public void valueChanged(@NotNull ListSelectionEvent listSelectionEvent) {
    updateUiFromCurrentObject();
  }

  private void updateCurrentObjectFromUi() {
    String newName = myObjectName.getText();
    if (newName != null && !myCurrentObject.getName().equals(newName)) {
      myCurrentObject.setName(newName);
      myList.updateUI();
      myModified = true;
    }
  }

  private void updateUiFromCurrentObject() {
    int selectedIndex = myList.getSelectedIndex();
    NamedObject currentObject = selectedIndex >= 0 ? (NamedObject)myListModel.get(selectedIndex) : null;
    myCurrentObject = currentObject;
    myObjectName.setText(currentObject != null ? currentObject.getName() : "");
    myObjectName.setEnabled(currentObject != null);
    myDetailsPane.setCurrentBuildFileObject(myCurrentObject != null ? myCurrentObject.getValues() : null);
    myDetailsPane.setCurrentModelObject(myCurrentObject != null ? myModelObjects.get(myCurrentObject.getName()) : null);
    myDetailsPane.updateUiFromCurrentObject();
  }

  private void createUIComponents() {
    myDetailsPane = new KeyValuePane(myProject);
  }

  private Collection<NamedObject> getObjectsFromModel(Collection<BuildFileKey> properties) {
     Collection<NamedObject> results = Lists.newArrayList();
    if (myModule == null) {
      return results;
    }
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    if (facet == null) {
      return results;
    }
    IdeaAndroidProject gradleProject = facet.getIdeaAndroidProject();
    if (gradleProject == null) {
      return results;
    }
    switch(myBuildFileKey) {
      case BUILD_TYPES:
        for (String name : gradleProject.getBuildTypeNames()) {
          BuildTypeContainer buildTypeContainer = gradleProject.findBuildType(name);
          NamedObject obj = new NamedObject(name);
          if (buildTypeContainer == null) {
            break;
          }
          BuildType buildType = buildTypeContainer.getBuildType();
          obj.setValue(BuildFileKey.DEBUGGABLE, buildType.isDebuggable());
          obj.setValue(BuildFileKey.JNI_DEBUG_BUILD, buildType.isJniDebugBuild());
          obj.setValue(BuildFileKey.RENDERSCRIPT_DEBUG_BUILD, buildType.isRenderscriptDebugBuild());
          obj.setValue(BuildFileKey.RENDERSCRIPT_OPTIM_LEVEL, buildType.getRenderscriptOptimLevel());
          obj.setValue(BuildFileKey.PACKAGE_NAME_SUFFIX, buildType.getPackageNameSuffix());
          obj.setValue(BuildFileKey.VERSION_NAME_SUFFIX, buildType.getVersionNameSuffix());
          obj.setValue(BuildFileKey.RUN_PROGUARD, buildType.isRunProguard());
          obj.setValue(BuildFileKey.ZIP_ALIGN, buildType.isZipAlign());
          results.add(obj);
        }
        break;
      case FLAVORS:
        for (String name : gradleProject.getProductFlavorNames()) {
          ProductFlavorContainer productFlavorContainer = gradleProject.findProductFlavor(name);
          NamedObject obj = new NamedObject(name);
          if (productFlavorContainer == null) {
            break;
          }
          ProductFlavor flavor = productFlavorContainer.getProductFlavor();
          obj.setValue(BuildFileKey.PACKAGE_NAME, flavor.getPackageName());
          int versionCode = flavor.getVersionCode();
          if (versionCode >= 0) {
            obj.setValue(BuildFileKey.VERSION_CODE, versionCode);
          }
          obj.setValue(BuildFileKey.VERSION_NAME, flavor.getVersionName());
          ApiVersion minSdkVersion = flavor.getMinSdkVersion();
          if (minSdkVersion != null && minSdkVersion.getApiLevel() >= 0) {
            obj.setValue(BuildFileKey.MIN_SDK_VERSION, minSdkVersion.getApiLevel());
          }
          ApiVersion targetSdkVersion = flavor.getTargetSdkVersion();
          if (targetSdkVersion != null && targetSdkVersion.getApiLevel() >= 0) {
            obj.setValue(BuildFileKey.TARGET_SDK_VERSION, targetSdkVersion.getApiLevel());
          }
          obj.setValue(BuildFileKey.TEST_PACKAGE_NAME, flavor.getTestPackageName());
          obj.setValue(BuildFileKey.TEST_INSTRUMENTATION_RUNNER, flavor.getTestInstrumentationRunner());
          results.add(obj);
        }
        results.add(new NamedObject(DEFAULT_CONFIG));
        break;
      default:
        break;
    }
    return results;
  }
}
