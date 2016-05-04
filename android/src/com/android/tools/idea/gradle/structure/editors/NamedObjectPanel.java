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
package com.android.tools.idea.gradle.structure.editors;

import com.android.builder.model.*;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.NamedObject;
import com.android.tools.idea.gradle.parser.ValueFactory;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiNameHelper;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.SortedList;
import org.gradle.tooling.model.UnsupportedMethodException;
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
import java.util.*;
import java.util.List;

public class NamedObjectPanel extends BuildFilePanel implements DocumentListener, ListSelectionListener, KeyValuePane.ModificationListener {
  /**
   * The PanelGroup class allows UI panels to talk to each other so that a pane that maintains a list of
   * {@link NamedObject} instances can talk to another pane that maintains a
   * {@link com.android.tools.idea.gradle.parser.BuildFileKeyType#REFERENCE} to one of those instances. For example, panels that have a
   * {@link com.android.tools.idea.gradle.parser.BuildFileKey#SIGNING_CONFIG} can pick up changes from the panel responsible for wrangling
   * the {@link com.android.tools.idea.gradle.parser.BuildFileKey#SIGNING_CONFIGS}.
   */
  public static class PanelGroup {
    private Collection<KeyValuePane> myPanes = Lists.newArrayList();

    public void valuesUpdated(BuildFileKey key, Iterable<String> values) {
      for (KeyValuePane pane : myPanes) {
        pane.updateReferenceValues(key, values);
      }
    }
  }

  private static final String DEFAULT_CONFIG = "defaultConfig";
  private static final BuildFileKey[] DEFAULT_CONFIG_KEYS =
      { BuildFileKey.APPLICATION_ID, BuildFileKey.VERSION_CODE, BuildFileKey.VERSION_NAME, BuildFileKey.MIN_SDK_VERSION,
        BuildFileKey.TARGET_SDK_VERSION, BuildFileKey.TEST_APPLICATION_ID, BuildFileKey.TEST_INSTRUMENTATION_RUNNER,
        BuildFileKey.SIGNING_CONFIG, BuildFileKey.VERSION_NAME_SUFFIX};
  private static final Set<String> HARDCODED_BUILD_TYPES = ImmutableSet.of("debug", "release");

  private JPanel myPanel;
  private JBList myList;
  private JTextField myObjectName;
  private JSplitPane mySplitPane;
  private JPanel myRightPane;
  private KeyValuePane myDetailsPane;
  private JLabel myNameWarning;
  private final BuildFileKey myBuildFileKey;
  private final String myNewItemName;
  private final SortedListModel myListModel;
  private NamedObject myCurrentObject;
  private Collection<NamedObject> myModelOnlyObjects = Lists.newArrayList();
  private Map<String, Map<BuildFileKey, Object>> myModelObjects = Maps.newHashMap();
  private final PanelGroup myPanelGroup;
  private volatile boolean myUpdating;
  private final Set<Pair<NamedObject, BuildFileKey>> myModifiedKeys = Sets.newHashSet();

  /** An object that can't be deleted because it's supplied by default by the model */
  private static class UndeletableNamedObject extends NamedObject {
    UndeletableNamedObject(@NotNull String name) {
      super(name, true);
    }

    public UndeletableNamedObject(NamedObject obj) {
      super(obj);
    }
  }

  public NamedObjectPanel(@NotNull Project project, @NotNull String moduleName, @NotNull BuildFileKey buildFileKey,
                          @NotNull String newItemName, @NotNull PanelGroup panelGroup) {
    super(project, moduleName);
    myBuildFileKey = buildFileKey;
    myNewItemName = newItemName;
    myPanelGroup = panelGroup;
    myListModel = new SortedListModel();
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
        updateCurrentObjectFromUi();
        addObject();
      }
    });
    decorator.setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton anActionButton) {
        removeObject();
      }
    });
    decorator.setRemoveActionUpdater(new AnActionButtonUpdater() {
      @Override
      public boolean isEnabled(AnActionEvent e) {
        NamedObject selectedObject = getSelectedObject();
        return selectedObject != null && !(selectedObject instanceof UndeletableNamedObject);
      }
    });
    decorator.disableUpDownActions();
    mySplitPane.setLeftComponent(decorator.createPanel());
    mySplitPane.setDividerLocation(200);
    myRightPane.setBorder(IdeBorderFactory.createBorder());

    myNameWarning.setForeground(JBColor.RED);
  }

  @Override
  public void init() {
    super.init();
    myPanelGroup.myPanes.add(myDetailsPane);
    if (myGradleBuildFile == null) {
      return;
    }
    List<NamedObject> namedObjects = (List<NamedObject>)myGradleBuildFile.getValue(myBuildFileKey);
    if (namedObjects != null) {
      for (NamedObject object : namedObjects) {
        // If this is one of the known model-provided build types, then wrap it as a UndeletableNamedObject
        // so that the user isn't allowed to delete it. In truth deleting it from this panel would just delete
        // it from the build file and not cause any harm, but making it never deletable makes its behavior
        // consistent between the case where you've customized it and you haven't.
        if (myBuildFileKey == BuildFileKey.BUILD_TYPES && HARDCODED_BUILD_TYPES.contains(object.getName())) {
          object = new UndeletableNamedObject(object);
        }
        addElement(object);
      }
    }
    // If this is a flavor panel, add a synthetic flavor entry for defaultConfig.
    if (myBuildFileKey == BuildFileKey.FLAVORS) {
      GrStatementOwner defaultConfig = myGradleBuildFile.getClosure(BuildFileKey.DEFAULT_CONFIG.getPath());
      NamedObject obj = new UndeletableNamedObject(DEFAULT_CONFIG);
      if (defaultConfig != null) {
        for (BuildFileKey key : DEFAULT_CONFIG_KEYS) {
          Object value = myGradleBuildFile.getValue(defaultConfig, key);

          obj.setValue(key, value);
        }
      }
      addElement(obj);
    }

    NamedObject.Factory objectFactory = (NamedObject.Factory)myBuildFileKey.getValueFactory();
    if (objectFactory == null) {
      throw new IllegalArgumentException("Can't instantiate a NamedObjectPanel for BuildFileKey " + myBuildFileKey.toString());
    }
    Collection<BuildFileKey> properties = objectFactory.getProperties();

    // Query the model for its view of the world, and merge it with the build file-based view.
    for (NamedObject obj : getObjectsFromModel(properties)) {
      boolean found = false;
      for (NamedObject o : myListModel) {
        if (o.getName().equals(obj.getName())) {
          found = true;
        }
      }
      if (!found) {
        NamedObject namedObject = new UndeletableNamedObject(obj.getName());
        addElement(namedObject);

        // Keep track of objects that are only in the model and not in the build file. We want to avoid creating them in the build file
        // unless some value in them is changed to non-default.
        myModelOnlyObjects.add(namedObject);
      }
      myModelObjects.put(obj.getName(), obj.getValues());
    }
    myList.updateUI();

    myDetailsPane.init(myGradleBuildFile, properties);
    if (myListModel.getSize() > 0) {
      myList.setSelectedIndex(0);
    }
    updateUiFromCurrentObject();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        updatePanelGroup();
      }
    }, ModalityState.any());
  }

  private int addElement(@NotNull NamedObject object) {
    return myListModel.add(object);
  }

  @Override
  public void apply() {
    if ((!myModified && myModifiedKeys.isEmpty()) ||  myGradleBuildFile == null) {
      return;
    }
    List<NamedObject> objects = Lists.newArrayList();
    for (NamedObject obj : myListModel) {
      // Save the defaultConfig separately and don't write it out as a regular flavor.
      if (myBuildFileKey == BuildFileKey.FLAVORS && obj.getName().equals(DEFAULT_CONFIG)) {
        GrStatementOwner defaultConfig = myGradleBuildFile.getClosure(BuildFileKey.DEFAULT_CONFIG.getPath());
        if (defaultConfig == null) {
          myGradleBuildFile.setValue(BuildFileKey.DEFAULT_CONFIG, "{}");
          defaultConfig = myGradleBuildFile.getClosure(BuildFileKey.DEFAULT_CONFIG.getPath());
        }
        assert defaultConfig != null;
        for (BuildFileKey key : DEFAULT_CONFIG_KEYS) {
          if (!isModified(obj, key)) {
            continue;
          }
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
    myGradleBuildFile.setValue(myBuildFileKey, objects, new ValueFactory.KeyFilter() {
      @Override
      public boolean shouldWriteKey(BuildFileKey key, Object object) {
        return isModified((NamedObject)object, key);
      }
    });

    myModified = false;
    myModifiedKeys.clear();
  }

  private boolean isModified(NamedObject obj, BuildFileKey key) {
    // An O(n) lookup shouldn't be inefficient for the small number of values we'll have. It's probably better than
    // myModifiedKeys.contains(Pair.create(obj, key));
    for (Pair<NamedObject, BuildFileKey> modifiedKey : myModifiedKeys) {
      if (modifiedKey.first.equals(obj) && modifiedKey.second.equals(key)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void modified(@NotNull BuildFileKey key) {
    myModifiedKeys.add(Pair.create(myCurrentObject, key));
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
    int index = addElement(new NamedObject(name));
    // Select newly added element.
    myList.getSelectionModel().setSelectionInterval(index, index);
    updateUiFromCurrentObject();
    // Make sure we scroll to selected element
    myList.ensureIndexIsVisible(index);
    myList.updateUI();
    myModified = true;
    updatePanelGroup();
  }

  private void removeObject() {
    int selectedIndex = myList.getSelectedIndex();
    if (selectedIndex < 0) {
      return;
    }
    myListModel.remove(selectedIndex);
    myList.setSelectedIndex(Math.max(0, Math.min(selectedIndex, myListModel.getSize() - 1)));
    myList.updateUI();
    myModified = true;
    updatePanelGroup();
  }

  @Nullable
  private NamedObject getNamedItem(@NotNull String name) {
    for (NamedObject object : myListModel) {
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
    return myModified || !myModifiedKeys.isEmpty();
  }

  @Override
  public void valueChanged(@NotNull ListSelectionEvent listSelectionEvent) {
    updateUiFromCurrentObject();
  }

  private void updateCurrentObjectFromUi() {
    if (myCurrentObject == null || myUpdating) {
      return;
    }
    String newName = validateName();
    if (newName != null && !myCurrentObject.getName().equals(newName)) {
      myCurrentObject.setName(newName);
      myList.updateUI();
      myModified = true;
      updatePanelGroup();
    }
  }

  private void updateUiFromCurrentObject() {
    try {
      myUpdating = true;
      NamedObject currentObject = getSelectedObject();
      myCurrentObject = currentObject;
      myObjectName.setText(currentObject != null ? currentObject.getName() : "");
      myObjectName.setEnabled(currentObject != null);
      myDetailsPane.setCurrentBuildFileObject(myCurrentObject != null ? myCurrentObject.getValues() : null);
      myDetailsPane.setCurrentModelObject(myCurrentObject != null ? myModelObjects.get(myCurrentObject.getName()) : null);
      myDetailsPane.updateUiFromCurrentObject();
      validateName();
    }
    finally {
      myUpdating = false;
    }
  }

  @NotNull
  private String validateName() {
    if (myCurrentObject == null) {
      return "";
    }
    String newName = myObjectName.getText();
    if (!PsiNameHelper.getInstance(myProject).isIdentifier(newName, LanguageLevel.JDK_1_7)) {
      myNameWarning.setText("Name must be a valid Java identifier");
      newName = myCurrentObject.getName();
    }
    else {
      myNameWarning.setText(" ");
    }
  return newName;
  }

  @Nullable
  private NamedObject getSelectedObject() {
    int selectedIndex = myList.getSelectedIndex();
    selectedIndex = Math.min(selectedIndex, myListModel.getSize() - 1);
    return selectedIndex >= 0 ? myListModel.get(selectedIndex) : null;
  }

  private void createUIComponents() {
    myDetailsPane = new KeyValuePane(myProject, this);
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
    AndroidGradleModel androidModel = AndroidGradleModel.get(facet);
    if (androidModel == null) {
      return results;
    }
    switch(myBuildFileKey) {
      case BUILD_TYPES:
        for (String name : androidModel.getBuildTypeNames()) {
          BuildTypeContainer buildTypeContainer = androidModel.findBuildType(name);
          NamedObject obj = new UndeletableNamedObject(name);
          if (buildTypeContainer == null) {
            break;
          }
          BuildType buildType = buildTypeContainer.getBuildType();
          obj.setValue(BuildFileKey.DEBUGGABLE, buildType.isDebuggable());
          obj.setValue(BuildFileKey.JNI_DEBUG_BUILD, buildType.isJniDebuggable());
          obj.setValue(BuildFileKey.RENDERSCRIPT_DEBUG_BUILD, buildType.isRenderscriptDebuggable());
          obj.setValue(BuildFileKey.RENDERSCRIPT_OPTIM_LEVEL, buildType.getRenderscriptOptimLevel());
          obj.setValue(BuildFileKey.APPLICATION_ID_SUFFIX, buildType.getApplicationIdSuffix());
          obj.setValue(BuildFileKey.VERSION_NAME_SUFFIX, getVersionNameSuffix(buildType));
          obj.setValue(BuildFileKey.MINIFY_ENABLED, buildType.isMinifyEnabled());
          obj.setValue(BuildFileKey.ZIP_ALIGN, buildType.isZipAlignEnabled());
          results.add(obj);
        }
        break;
      case FLAVORS:
        for (String name : androidModel.getProductFlavorNames()) {
          ProductFlavorContainer productFlavorContainer = androidModel.findProductFlavor(name);
          NamedObject obj = new UndeletableNamedObject(name);
          if (productFlavorContainer == null) {
            break;
          }
          ProductFlavor flavor = productFlavorContainer.getProductFlavor();
          obj.setValue(BuildFileKey.APPLICATION_ID, flavor.getApplicationId());
          Integer versionCode = flavor.getVersionCode();
          if (versionCode != null) {
            obj.setValue(BuildFileKey.VERSION_CODE, versionCode);
          }
          obj.setValue(BuildFileKey.VERSION_NAME, flavor.getVersionName());
          if (androidModel.supportsProductFlavorVersionSuffix()) {
            obj.setValue(BuildFileKey.VERSION_NAME_SUFFIX, getVersionNameSuffix(flavor));
          }
          ApiVersion minSdkVersion = flavor.getMinSdkVersion();
          if (minSdkVersion != null) {
            obj.setValue(BuildFileKey.MIN_SDK_VERSION,
                         minSdkVersion.getCodename() != null ? minSdkVersion.getCodename() : minSdkVersion.getApiLevel());
          }
          ApiVersion targetSdkVersion = flavor.getTargetSdkVersion();
          if (targetSdkVersion != null) {
            obj.setValue(BuildFileKey.TARGET_SDK_VERSION,
                         targetSdkVersion.getCodename() != null ? targetSdkVersion.getCodename() : targetSdkVersion.getApiLevel());
          }
          obj.setValue(BuildFileKey.TEST_APPLICATION_ID, flavor.getTestApplicationId());
          obj.setValue(BuildFileKey.TEST_INSTRUMENTATION_RUNNER, flavor.getTestInstrumentationRunner());
          results.add(obj);
        }
        results.add(new UndeletableNamedObject(DEFAULT_CONFIG));
        break;
      default:
        break;
    }
    return results;
  }

  // TODO: Remove once Android plugin v. 2.3 is the "recommended" version.
  @Nullable
  private static String getVersionNameSuffix(@NotNull BaseConfig config) {
    try {
      return config.getVersionNameSuffix();
    }
    catch (UnsupportedMethodException e) {
      Logger.getInstance(NamedObjectPanel.class).warn("Method 'getVersionNameSuffix' not found", e);
      return null;
    }
  }

  private void updatePanelGroup() {
    List<String> values = Lists.newArrayList();
    for (NamedObject o : myListModel) {
      values.add(o.getName());
    }
    myPanelGroup.valuesUpdated(myBuildFileKey, values);
  }

  private static class SortedListModel extends AbstractListModel implements Iterable<NamedObject> {
    private final SortedList<NamedObject> model = new SortedList<NamedObject>(new Comparator<NamedObject>() {
      @Override
      public int compare(NamedObject o1, NamedObject o2) {
        assert o1 != null;
        return o1.compareTo(o2);
      }
    });

    @Override
    public int getSize() {
      return model.size();
    }

    public NamedObject get(int index) {
      return model.get(index);
    }

    @Override
    public Object getElementAt(int index) {
      return get(index);
    }

    public int add(NamedObject o) {
      if (model.add(o)) {
        fireContentsChanged(this, 0, getSize());
      }
      return model.indexOf(o);
    }

    public void remove(int index) {
      model.remove(index);
    }

    @Override
    public Iterator<NamedObject> iterator() {
      return model.iterator();
    }
  }
}
