/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.uipreview;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.*;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.idea.rendering.ResourceNameValidator;
import com.google.common.collect.ImmutableMap;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.jetbrains.android.actions.CreateResourceFileAction;
import org.jetbrains.android.actions.CreateXmlResourceDialog;
import org.jetbrains.android.actions.CreateXmlResourcePanel;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.refactoring.AndroidBaseLayoutRefactoringAction;
import org.jetbrains.android.refactoring.AndroidExtractStyleAction;
import org.jetbrains.android.resourceManagers.FileResourceProcessor;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Resource Chooser, with previews. Based on ResourceDialog in the android-designer.
 * <P>
 * TODO:
 * <ul>
 *   <li> Finish color parsing</li>
 *   <li> Perform validation (such as cyclic layout resource detection for layout selection)</li>
 *   <li> Render drawables using layoutlib, e.g. drawable XML files, .9.png's, etc.</li>
 *   <li> Offer to create more resource types</li>
 * </ul>
 */
public class ChooseResourceDialog extends DialogWrapper implements TreeSelectionListener {
  private static final String TYPE_KEY = "ResourceType";

  private static final String TEXT = "Text";
  private static final String COMBO = "Combo";
  private static final String IMAGE = "Image";
  private static final String NONE = "None";

  private static final Icon RESOURCE_ITEM_ICON = AllIcons.Css.Property;

  private final Module myModule;
  @Nullable private final XmlTag myTag;

  private final JBTabbedPane myContentPanel;
  private final ResourcePanel myProjectPanel;
  private final ResourcePanel mySystemPanel;

  private ResourceDialogTabComponent myColorPickerPanel;
  private ColorPicker myColorPicker;

  private ResourceDialogTabComponent myStateListPickerPanel;
  private StateListPicker myStateListPicker;

  private ResourcePickerListener myResourcePickerListener;

  private boolean myAllowCreateResource = true;
  private final Action myNewResourceAction = new AbstractAction("New Resource", AllIcons.General.ComboArrowDown) {
    @Override
    public void actionPerformed(ActionEvent e) {
      JComponent component = (JComponent)e.getSource();
      ActionPopupMenu popupMenu = createNewResourcePopupMenu();
      popupMenu.getComponent().show(component, 0, component.getHeight());
    }
  };
  private final AnAction myNewResourceValueAction = new AnAction() {
    @Override
    public void actionPerformed(AnActionEvent e) {
      ResourceType type = (ResourceType)getTemplatePresentation().getClientProperty(TYPE_KEY);
      createNewResourceValue(type);
    }
  };
  private final AnAction myNewResourceFileAction = new AnAction() {
    @Override
    public void actionPerformed(AnActionEvent e) {
      ResourceType type = (ResourceType)getTemplatePresentation().getClientProperty(TYPE_KEY);
      createNewResourceFile(type);
    }
  };
  private final AnAction myExtractStyleAction = new AnAction("Extract Style...") {
    @Override
    public void actionPerformed(AnActionEvent e) {
      extractStyle();
    }
  };

  private String myResultResourceName;

  private boolean myOverwriteResource = false;
  private ResourceNameVisibility myResourceNameVisibility;

  public interface ResourcePickerListener {
    void resourceChanged(String resource);
  }

  public ChooseResourceDialog(@NotNull Module module, @NotNull ResourceType[] types, @Nullable String value, @Nullable XmlTag tag) {
    this(module, null, types, value, false, tag, ResourceNameVisibility.HIDE, null);
  }

  public ChooseResourceDialog(@NotNull Module module,
                              @NotNull Configuration configuration,
                              @NotNull ResourceType[] types,
                              @NotNull String value,
                              boolean isFrameworkValue,
                              @NotNull ResourceNameVisibility resourceNameVisibility,
                              @Nullable String resourceNameSuggestion) {
    this(module, configuration, types, value, isFrameworkValue, null, resourceNameVisibility, resourceNameSuggestion);
  }

  private ChooseResourceDialog(@NotNull Module module,
                               @Nullable Configuration configuration,
                               @NotNull ResourceType[] types,
                               @Nullable String value,
                               boolean isFrameworkValue,
                               @Nullable XmlTag tag,
                               @NotNull ResourceNameVisibility resourceNameVisibility,
                               @Nullable String resourceNameSuggestion) {
    super(module.getProject());
    myModule = module;
    myTag = tag;
    myResourceNameVisibility = resourceNameVisibility;

    setTitle("Resources");

    AndroidFacet facet = AndroidFacet.getInstance(module);
    myProjectPanel = new ResourcePanel(facet, types, false);
    mySystemPanel = new ResourcePanel(facet, types, true);

    myContentPanel = new JBTabbedPane();
    myContentPanel.addTab("Project", myProjectPanel.myComponent);
    myContentPanel.addTab("System", mySystemPanel.myComponent);

    myProjectPanel.myTreeBuilder.expandAll(null);
    mySystemPanel.myTreeBuilder.expandAll(null);

    if (value != null && value.startsWith("@")) {
      value = StringUtil.replace(value, "+", "");
      int index = value.indexOf('/');
      if (index != -1) {
        ResourcePanel panel;
        String type;
        String name = value.substring(index + 1);
        if (value.startsWith(SdkConstants.ANDROID_PREFIX)) {
          panel = mySystemPanel;
          type = value.substring(SdkConstants.ANDROID_PREFIX.length(), index);
        }
        else {
          panel = myProjectPanel;
          type = value.substring(1, index);
        }
        myContentPanel.setSelectedComponent(panel.myComponent);
        panel.select(type, name);
      }
    }

    Color color = null;
    if (configuration != null) {
      assert value != null;

      ResourceResolver resolver = configuration.getResourceResolver();
      assert resolver != null;
      ResourceValue resValue = resolver.findResValue(value, isFrameworkValue);
      ResourceHelper.StateList stateList = resValue != null ? ResourceHelper.resolveStateList(resolver, resValue, module.getProject()) : null;

      if (stateList != null) {
        final ResourceFolderType resFolderType = stateList.getType();
        final ResourceType resType = ResourceType.getEnum(resFolderType.getName());
        assert resType != null;

        myStateListPicker = new StateListPicker(stateList, module, configuration);
        myStateListPickerPanel = new ResourceDialogTabComponent(new JPanel(new BorderLayout()), resType, resFolderType);
        myStateListPickerPanel.setBorder(null);
        myStateListPickerPanel.addCenter(myStateListPicker);
        myStateListPickerPanel.setChangeFileNameVisible(false);

        if (myResourceNameVisibility != ResourceNameVisibility.HIDE) {
          myStateListPickerPanel.addResourceDialogSouthPanel(resourceNameSuggestion, true);
        }

        myStateListPickerPanel
          .setValidator(ResourceNameValidator.create(true, AppResourceRepository.getAppResources(myModule, true), resType, true));
      }
      else {
        color = ResourceHelper.resolveColor(resolver, resValue, module.getProject());
      }
    }

    if (ArrayUtil.contains(ResourceType.COLOR, types) || ArrayUtil.contains(ResourceType.DRAWABLE, types)) {
      if (color == null) {
        color = ResourceHelper.parseColor(value);
      }
      myColorPicker = new ColorPicker(myDisposable, color, true, new ColorPickerListener() {
        @Override
        public void colorChanged(Color color) {
          notifyResourcePickerListeners(ResourceHelper.colorToString(color));
        }

        @Override
        public void closed(@Nullable Color color) {
        }
      });
      myColorPicker.pickARGB();

      myColorPickerPanel = new ResourceDialogTabComponent(new JPanel(new BorderLayout()), ResourceType.COLOR, ResourceFolderType.VALUES);
      myColorPickerPanel.setBorder(null);
      myColorPickerPanel.addCenter(myColorPicker);
      myContentPanel.addTab("Color", myColorPickerPanel);
      myContentPanel.setSelectedIndex(myContentPanel.getTabCount() - 1);

      if (myResourceNameVisibility != ResourceNameVisibility.HIDE) {
        myColorPickerPanel.addResourceDialogSouthPanel(resourceNameSuggestion, false);
      }

      myColorPickerPanel.setValidator(
        ResourceNameValidator.create(false, AppResourceRepository.getAppResources(myModule, true), ResourceType.COLOR, false));
    }

    if (myStateListPicker != null) {
      myContentPanel.addTab("StateList", myStateListPickerPanel);
      myContentPanel.setSelectedIndex(myContentPanel.getTabCount() - 1);
    }

    myContentPanel.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        valueChanged(null);
      }
    });

    valueChanged(null);
    init();
    // we need to trigger this once before the window is made visible to update any extra labels
    doValidate();
  }

  public void setContrastParameters(@NotNull ImmutableMap<String, Color> contrastColorsWithDescription,
                                    boolean isBackground,
                                    boolean displayWarning) {
    if (myColorPicker != null) {
      myColorPicker.setContrastParameters(contrastColorsWithDescription, isBackground, displayWarning);
    }
    if (myStateListPicker != null) {
      myStateListPicker.setContrastParameters(contrastColorsWithDescription, isBackground);
    }
  }

  @Override
  protected boolean postponeValidation() {
    return false;
  }

  public enum ResourceNameVisibility {
    /**
     * Don't show field with resource name at all.
     */
    HIDE,

    /**
     * Force creation of named color.
     */
    FORCE
  }

  private String getErrorString(@NotNull ResourceDialogTabComponent tabComponent) {
    myOverwriteResource = false;
    String result = null;
    ResourceNameValidator validator = tabComponent.getValidator();
    if (validator != null) {
      String enteredName = tabComponent.getResourceNameField().getText();
      if (validator.doesResourceExist(enteredName)) {
        result = String.format("Saving this color will override existing resource %1$s.", enteredName);
        myOverwriteResource = true;
      } else {
        result = validator.getErrorText(enteredName);
      }
    }

    return result;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    Component selectedComponent = myContentPanel.getSelectedComponent();

    final boolean okActionEnabled;
    ValidationInfo error = null;

    if (selectedComponent == mySystemPanel.myComponent || selectedComponent == myProjectPanel.myComponent) {
      boolean isProjectPanel = selectedComponent == myProjectPanel.myComponent;
      ResourcePanel panel = isProjectPanel ? myProjectPanel : mySystemPanel;
      ResourceItem element = getSelectedElement(panel.myTreeBuilder, ResourceItem.class);
      okActionEnabled = element != null;
    }
    else {
      // if name is hidden, then we allow any value
      if (myResourceNameVisibility != ResourceNameVisibility.HIDE) {
        ResourceDialogTabComponent tabComponent = (ResourceDialogTabComponent)selectedComponent;
        final String errorText = getErrorString(tabComponent);
        if (errorText != null && !myOverwriteResource) {
          tabComponent.getResourceNameMessage().setText("");
          error = new ValidationInfo(errorText, tabComponent.getResourceNameField());
        }
        else {
          tabComponent.getResourceNameMessage().setText(errorText != null ? errorText : "");
          error = tabComponent.getLocationSettings().doValidate();
        }
      }

      if (error == null && selectedComponent == myStateListPickerPanel) {
        error = myStateListPicker.getFrameworkResourceError();
      }

      if (error == null && selectedComponent == myStateListPickerPanel) {
        int minDirectoriesApi = ThemeEditorUtils.getMinFolderApi(myStateListPickerPanel.getLocationSettings().getDirNames(), myModule);
        error = myStateListPicker.getApiError(minDirectoriesApi);
      }

      okActionEnabled = error == null;
    }

    // Need to always manually update the setOKActionEnabled as the DialogWrapper
    // only updates it if we go from having a error string to not having one
    // or the other way round, but not if the error string state has not changed.
    setOKActionEnabled(okActionEnabled);

    return error;
  }

  public void setResourcePickerListener(ResourcePickerListener resourcePickerListener) {
    myResourcePickerListener = resourcePickerListener;
  }

  protected void notifyResourcePickerListeners(String resource) {
    if (myResourcePickerListener != null) {
      myResourcePickerListener.resourceChanged(resource);
    }
  }

  public void generateColorSuggestions(@NotNull Color primaryColor, @NotNull String attributeName) {
    List<Color> suggestedColors = null;
    if (MaterialColors.PRIMARY_MATERIAL_ATTR.equals(attributeName)) {
      suggestedColors = MaterialColorUtils.suggestPrimaryColors();
    }
    else if (MaterialColors.PRIMARY_DARK_MATERIAL_ATTR.equals(attributeName)) {
      suggestedColors = MaterialColorUtils.suggestPrimaryDarkColors(primaryColor);
    }
    else if (MaterialColors.ACCENT_MATERIAL_ATTR.equals(attributeName)) {
      suggestedColors = MaterialColorUtils.suggestAccentColors(primaryColor);
    }
    if (suggestedColors != null) {
      myColorPicker.setRecommendedColors(suggestedColors);
    }
  }

  private ActionPopupMenu createNewResourcePopupMenu() {
    ActionManager actionManager = ActionManager.getInstance();
    DefaultActionGroup actionGroup = new DefaultActionGroup();

    ResourceGroup resourceGroup = getSelectedElement(myProjectPanel.myTreeBuilder, ResourceGroup.class);
    if (resourceGroup == null) {
      resourceGroup = getSelectedElement(myProjectPanel.myTreeBuilder, ResourceItem.class).getGroup();
    }

    if (AndroidResourceUtil.XML_FILE_RESOURCE_TYPES.contains(resourceGroup.getType())) {
      myNewResourceFileAction.getTemplatePresentation().setText("New " + resourceGroup + " File...");
      myNewResourceFileAction.getTemplatePresentation().putClientProperty(TYPE_KEY, resourceGroup.getType());
      actionGroup.add(myNewResourceFileAction);
    }
    if (AndroidResourceUtil.VALUE_RESOURCE_TYPES.contains(resourceGroup.getType())) {
      String title = "New " + resourceGroup + " Value...";
      if (resourceGroup.getType() == ResourceType.LAYOUT) {
        title = "New Layout Alias";
      }
      myNewResourceValueAction.getTemplatePresentation().setText(title);
      myNewResourceValueAction.getTemplatePresentation().putClientProperty(TYPE_KEY, resourceGroup.getType());
      actionGroup.add(myNewResourceValueAction);
    }
    if (myTag != null && ResourceType.STYLE.equals(resourceGroup.getType())) {
      final boolean enabled = AndroidBaseLayoutRefactoringAction.getLayoutViewElement(myTag) != null &&
                              AndroidExtractStyleAction.doIsEnabled(myTag);
      myExtractStyleAction.getTemplatePresentation().setEnabled(enabled);
      actionGroup.add(myExtractStyleAction);
    }

    return actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, actionGroup);
  }

  private void createNewResourceValue(ResourceType resourceType) {
    CreateXmlResourceDialog dialog = new CreateXmlResourceDialog(myModule, resourceType, null, null, true);
    dialog.setTitle("New " + StringUtil.capitalize(resourceType.getDisplayName()) + " Value Resource");
    if (!dialog.showAndGet()) {
      return;
    }

    Module moduleToPlaceResource = dialog.getModule();
    if (moduleToPlaceResource == null) {
      return;
    }

    String fileName = dialog.getFileName();
    List<String> dirNames = dialog.getDirNames();
    String resValue = dialog.getValue();
    String resName = dialog.getResourceName();
    if (!AndroidResourceUtil.createValueResource(moduleToPlaceResource, resName, resourceType, fileName, dirNames, resValue)) {
      return;
    }

    PsiDocumentManager.getInstance(myModule.getProject()).commitAllDocuments();

    myResultResourceName = "@" + resourceType.getName() + "/" + resName;
    close(OK_EXIT_CODE);
  }

  private void createNewResourceFile(ResourceType resourceType) {
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    XmlFile newFile = CreateResourceFileAction.createFileResource(facet, resourceType, null, null, null, true, null);

    if (newFile != null) {
      String name = newFile.getName();
      int index = name.lastIndexOf('.');
      if (index != -1) {
        name = name.substring(0, index);
      }
      myResultResourceName = "@" + resourceType.getName() + "/" + name;
      close(OK_EXIT_CODE);
    }
  }

  private void extractStyle() {
    final String resName = AndroidExtractStyleAction.doExtractStyle(myModule, myTag, false, null);
    if (resName == null) {
      return;
    }
    myResultResourceName = "@style/" + resName;
    close(OK_EXIT_CODE);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myProjectPanel.myTree;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  @NotNull
  @Override
  protected Action[] createLeftSideActions() {
    return myAllowCreateResource ? new Action[]{myNewResourceAction} : new Action[0];
  }

  public ChooseResourceDialog setAllowCreateResource(boolean allowCreateResource) {
    myAllowCreateResource = allowCreateResource;
    return this;
  }

  public boolean getAllowCreateResource() {
    return myAllowCreateResource;
  }

  /**
   * Expands the location settings panel
   */
  public void openLocationSettings() {
    ResourceDialogTabComponent tabComponent = (ResourceDialogTabComponent)myContentPanel.getSelectedComponent();
    tabComponent.openLocationSettings();
  }

  @Override
  protected void dispose() {
    super.dispose();
    Disposer.dispose(myProjectPanel.myTreeBuilder);
    Disposer.dispose(mySystemPanel.myTreeBuilder);
  }

  public String getResourceName() {
    return myResultResourceName;
  }

  @Override
  protected void doOKAction() {
    valueChanged(null);
    if (myContentPanel.getSelectedComponent() == myColorPickerPanel && myResourceNameVisibility != ResourceNameVisibility.HIDE) {
      String colorName = myColorPickerPanel.getResourceNameField().getText();
      Module module = myColorPickerPanel.getLocationSettings().getModule();
      String fileName = myColorPickerPanel.getLocationSettings().getFileName();
      List<String> dirNames = myColorPickerPanel.getLocationSettings().getDirNames();
      assert module != null;
      AndroidFacet facet = AndroidFacet.getInstance(module);
      assert facet != null;

      if (!AndroidResourceUtil.changeColorResource(facet, colorName, myResultResourceName, fileName, dirNames)) {
        // Changing color resource has failed, one possible reason is that color isn't defined in the project.
        // Trying to create the color instead.
        AndroidResourceUtil.createValueResource(module, colorName, ResourceType.COLOR, fileName, dirNames, myResultResourceName);
      }

      myResultResourceName = SdkConstants.COLOR_RESOURCE_PREFIX + colorName;
    }
    else if (myContentPanel.getSelectedComponent() == myStateListPickerPanel && myResourceNameVisibility != ResourceNameVisibility.HIDE) {
      String stateListName = myStateListPickerPanel.getResourceNameField().getText();
      List<String> dirNames = myStateListPickerPanel.getLocationSettings().getDirNames();
      ResourceFolderType resourceFolderType = ResourceFolderType.getFolderType(dirNames.get(0));
      ResourceType resourceType = ResourceType.getEnum(resourceFolderType.getName());

      List<VirtualFile> files = null;
      if (resourceType != null) {
        files = AndroidResourceUtil.findOrCreateStateListFiles(myModule, resourceFolderType, resourceType, stateListName, dirNames);
      }
      if (files != null) {
        myStateListPicker.updateStateList(files);
      }

      if (resourceFolderType == ResourceFolderType.COLOR) {
        myResultResourceName = SdkConstants.COLOR_RESOURCE_PREFIX + stateListName;
      }
      else if (resourceFolderType == ResourceFolderType.DRAWABLE) {
        myResultResourceName = SdkConstants.DRAWABLE_PREFIX + stateListName;
      }
    }
    super.doOKAction();
  }

  @Nullable
  private static <T> T getSelectedElement(AbstractTreeBuilder treeBuilder, Class<T> elementClass) {
    Set<T> elements = treeBuilder.getSelectedElements(elementClass);
    return elements.isEmpty() ? null : elements.iterator().next();
  }

  @Override
  public void valueChanged(@Nullable TreeSelectionEvent e) {
    Component selectedComponent = myContentPanel.getSelectedComponent();

    if (selectedComponent == myColorPickerPanel) {
      Color color = myColorPicker.getColor();
      myNewResourceAction.setEnabled(false);
      myResultResourceName = ResourceHelper.colorToString(color);
    }
    else if (selectedComponent == myStateListPickerPanel) {
      myNewResourceAction.setEnabled(false);
      myResultResourceName = null;
    }
    else {
      boolean isProjectPanel = selectedComponent == myProjectPanel.myComponent;
      ResourcePanel panel = isProjectPanel ? myProjectPanel : mySystemPanel;
      ResourceItem element = getSelectedElement(panel.myTreeBuilder, ResourceItem.class);

      myNewResourceAction.setEnabled(isProjectPanel && !panel.myTreeBuilder.getSelectedElements().isEmpty());

      if (element == null) {
        myResultResourceName = null;
      }
      else {
        String prefix = panel == myProjectPanel ? "@" : SdkConstants.ANDROID_PREFIX;
        myResultResourceName = prefix + element.getName();
      }

      panel.showPreview(element);
    }
    notifyResourcePickerListeners(myResultResourceName);
  }

  private class ResourcePanel {
    public final Tree myTree;
    public final AbstractTreeBuilder myTreeBuilder;
    public final JBSplitter myComponent;

    private final JPanel myPreviewPanel;
    private final JTextArea myTextArea;
    private final JTextArea myComboTextArea;
    private final JComboBox myComboBox;
    private final JLabel myImageComponent;
    private final JLabel myNoPreviewComponent;

    private final ResourceGroup[] myGroups;
    private final ResourceManager myManager;

    public ResourcePanel(AndroidFacet facet, ResourceType[] types, boolean system) {
      myTree = new Tree();
      myTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
      myTree.setScrollsOnExpand(true);
      myTree.setRootVisible(false);
      myTree.setShowsRootHandles(true);
      new DoubleClickListener() {
        @Override
        protected boolean onDoubleClick(MouseEvent e) {
          if (!myTreeBuilder.getSelectedElements(ResourceItem.class).isEmpty()) {
            close(OK_EXIT_CODE);
            return true;
          }
          return false;
        }
      }.installOn(myTree);

      ToolTipManager.sharedInstance().registerComponent(myTree);
      TreeUtil.installActions(myTree);

      myManager = facet.getResourceManager(system ? AndroidUtils.SYSTEM_RESOURCE_PACKAGE : null);

      if (ArrayUtil.contains(ResourceType.DRAWABLE, types) && !ArrayUtil.contains(ResourceType.COLOR, types)) {
        myGroups = new ResourceGroup[types.length + 1];
        myGroups[types.length] = new ResourceGroup(ResourceType.COLOR, myManager, false);
      }
      else {
        myGroups = new ResourceGroup[types.length];
      }

      for (int i = 0; i < types.length; i++) {
        myGroups[i] = new ResourceGroup(types[i], myManager);
      }

      myTreeBuilder =
        new AbstractTreeBuilder(myTree, (DefaultTreeModel)myTree.getModel(), new TreeContentProvider(myGroups), null);
      myTreeBuilder.initRootNode();

      TreeSelectionModel selectionModel = myTree.getSelectionModel();
      selectionModel.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
      selectionModel.addTreeSelectionListener(ChooseResourceDialog.this);

      myTree.setCellRenderer(new NodeRenderer());
      new TreeSpeedSearch(myTree, TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING, true);

      myComponent = new JBSplitter(true, 0.8f);
      myComponent.setSplitterProportionKey("android.resource_dialog_splitter");

      myComponent.setFirstComponent(ScrollPaneFactory.createScrollPane(myTree));

      myPreviewPanel = new JPanel(new CardLayout());
      myComponent.setSecondComponent(myPreviewPanel);

      myTextArea = new JTextArea(5, 20);
      myTextArea.setEditable(false);
      myPreviewPanel.add(ScrollPaneFactory.createScrollPane(myTextArea), TEXT);

      myComboTextArea = new JTextArea(5, 20);
      myComboTextArea.setEditable(false);

      myComboBox = new JComboBox();
      myComboBox.setMaximumRowCount(15);
      myComboBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          List<ResourceElement> resources = (List<ResourceElement>)myComboBox.getClientProperty(COMBO);
          myComboTextArea.setText(getResourceElementValue(resources.get(myComboBox.getSelectedIndex())));
        }
      });

      JPanel comboPanel = new JPanel(new BorderLayout(0, 1) {
        @Override
        public void layoutContainer(Container target) {
          super.layoutContainer(target);
          Rectangle bounds = myComboBox.getBounds();
          Dimension size = myComboBox.getPreferredSize();
          size.width += 20;
          myComboBox.setBounds((int)bounds.getMaxX() - size.width, bounds.y, size.width, size.height);
        }
      });
      comboPanel.add(ScrollPaneFactory.createScrollPane(myComboTextArea), BorderLayout.CENTER);
      comboPanel.add(myComboBox, BorderLayout.SOUTH);
      myPreviewPanel.add(comboPanel, COMBO);

      myImageComponent = new JLabel();
      myImageComponent.setHorizontalAlignment(SwingConstants.CENTER);
      myImageComponent.setVerticalAlignment(SwingConstants.CENTER);
      myPreviewPanel.add(myImageComponent, IMAGE);

      myNoPreviewComponent = new JLabel("No Preview");
      myNoPreviewComponent.setHorizontalAlignment(SwingConstants.CENTER);
      myNoPreviewComponent.setVerticalAlignment(SwingConstants.CENTER);
      myPreviewPanel.add(myNoPreviewComponent, NONE);
    }

    public void showPreview(@Nullable ResourceItem element) {
      CardLayout layout = (CardLayout)myPreviewPanel.getLayout();

      if (element == null || element.getGroup().getType() == ResourceType.ID) {
        layout.show(myPreviewPanel, NONE);
        return;
      }

      try {
        VirtualFile file = element.getFile();
        if (file == null) {
          String value = element.getPreviewString();
          if (value == null) {
            List<ResourceElement> resources = element.getPreviewResources();

            if (resources == null) {
              long time = System.currentTimeMillis();
              resources = myManager.findValueResources(element.getGroup().getType().getName(), element.toString());
              if (ApplicationManagerEx.getApplicationEx().isInternal()) {
                System.out.println("Time: " + (System.currentTimeMillis() - time)); // XXX
              }

              int size = resources.size();
              if (size == 1) {
                value = getResourceElementValue(resources.get(0));
                element.setPreviewString(value);
              }
              else if (size > 1) {
                resources = new ArrayList<ResourceElement>(resources);
                Collections.sort(resources, new Comparator<ResourceElement>() {
                  @Override
                  public int compare(ResourceElement element1, ResourceElement element2) {
                    PsiDirectory directory1 = element1.getXmlTag().getContainingFile().getParent();
                    PsiDirectory directory2 = element2.getXmlTag().getContainingFile().getParent();

                    if (directory1 == null && directory2 == null) {
                      return 0;
                    }
                    if (directory2 == null) {
                      return 1;
                    }
                    if (directory1 == null) {
                      return -1;
                    }

                    return directory1.getName().compareTo(directory2.getName());
                  }
                });

                DefaultComboBoxModel model = new DefaultComboBoxModel();
                String defaultSelection = null;
                for (int i = 0; i < size; i++) {
                  ResourceElement resource = resources.get(i);
                  PsiDirectory directory = resource.getXmlTag().getContainingFile().getParent();
                  String name = directory == null ? "unknown-" + i : directory.getName();
                  model.addElement(name);
                  if (defaultSelection == null && "values".equalsIgnoreCase(name)) {
                    defaultSelection = name;
                  }
                }
                element.setPreviewResources(resources, model, defaultSelection);

                showComboPreview(element);
                return;
              }
              else {
                layout.show(myPreviewPanel, NONE);
                return;
              }
            }
            else {
              showComboPreview(element);
              return;
            }
          }
          if (value == null) {
            layout.show(myPreviewPanel, NONE);
            return;
          }

          myTextArea.setText(value);
          layout.show(myPreviewPanel, TEXT);
        }
        else if (ImageFileTypeManager.getInstance().isImage(file)) {
          Icon icon = element.getPreviewIcon();
          if (icon == null) {
            icon = new SizedIcon(100, 100, new ImageIcon(file.getPath()));
            element.setPreviewIcon(icon);
          }
          myImageComponent.setIcon(icon);
          layout.show(myPreviewPanel, IMAGE);
        }
        else if (file.getFileType() == XmlFileType.INSTANCE) {
          String value = element.getPreviewString();
          if (value == null) {
            value = new String(file.contentsToByteArray());
            element.setPreviewString(value);
          }
          myTextArea.setText(value);
          myTextArea.setEditable(false);
          layout.show(myPreviewPanel, TEXT);
        }
        else {
          layout.show(myPreviewPanel, NONE);
        }
      }
      catch (IOException e) {
        layout.show(myPreviewPanel, NONE);
      }
    }

    private void showComboPreview(ResourceItem element) {
      List<ResourceElement> resources = element.getPreviewResources();
      String selection = (String)myComboBox.getSelectedItem();
      if (selection == null) {
        selection = element.getPreviewComboDefaultSelection();
      }

      int index = element.getPreviewComboModel().getIndexOf(selection);
      if (index == -1) {
        index = 0;
      }

      myComboBox.setModel(element.getPreviewComboModel());
      myComboBox.putClientProperty(COMBO, resources);
      myComboBox.setSelectedIndex(index);
      myComboTextArea.setText(getResourceElementValue(resources.get(index)));

      CardLayout layout = (CardLayout)myPreviewPanel.getLayout();
      layout.show(myPreviewPanel, COMBO);
    }

    private void select(String type, String name) {
      for (ResourceGroup group : myGroups) {
        if (type.equalsIgnoreCase(group.getName())) {
          for (ResourceItem item : group.getItems()) {
            if (name.equals(item.toString())) {
              myTreeBuilder.select(item);
              return;
            }
          }
          return;
        }
      }
    }
  }

  private static String getResourceElementValue(ResourceElement element) {
    String text = element.getRawText();
    if (StringUtil.isEmpty(text)) {
      return element.getXmlTag().getText();
    }
    return text;
  }

  public static class ResourceGroup {
    private List<ResourceItem> myItems = new ArrayList<ResourceItem>();
    private final ResourceType myType;

    public ResourceGroup(ResourceType type, ResourceManager manager) {
      this(type, manager, true);
    }

    public ResourceGroup(ResourceType type, ResourceManager manager, boolean includeFileResources) {
      myType = type;

      final String resourceType = type.getName();

      Collection<String> resourceNames = manager.getValueResourceNames(resourceType);
      for (String resourceName : resourceNames) {
        myItems.add(new ResourceItem(this, resourceName, null, RESOURCE_ITEM_ICON));
      }
      final Set<String> fileNames = new HashSet<String>();

      if (includeFileResources) {
        manager.processFileResources(resourceType, new FileResourceProcessor() {
          @Override
          public boolean process(@NotNull VirtualFile resFile, @NotNull String resName, @NotNull String resFolderType) {
            if (fileNames.add(resName)) {
              myItems.add(new ResourceItem(ResourceGroup.this, resName, resFile, resFile.getFileType().getIcon()));
            }
            return true;
          }
        });
      }

      if (type == ResourceType.ID) {
        for (String id : manager.getIds(true)) {
          if (!resourceNames.contains(id)) {
            myItems.add(new ResourceItem(this, id, null, RESOURCE_ITEM_ICON));
          }
        }
      }

      Collections.sort(myItems, new Comparator<ResourceItem>() {
        @Override
        public int compare(ResourceItem resource1, ResourceItem resource2) {
          return resource1.toString().compareTo(resource2.toString());
        }
      });
    }

    public ResourceType getType() {
      return myType;
    }

    public String getName() {
      return myType.getName();
    }

    public List<ResourceItem> getItems() {
      return myItems;
    }

    @Override
    public String toString() {
      return myType.getDisplayName();
    }
  }

  public static class ResourceItem {
    private final ResourceGroup myGroup;
    private final String myName;
    private final VirtualFile myFile;
    private final Icon myIcon;
    private String myPreviewString;
    private List<ResourceElement> myPreviewResources;
    private DefaultComboBoxModel myPreviewComboModel;
    private String myDefaultSelection;
    private Icon myPreviewIcon;

    public ResourceItem(@NotNull ResourceGroup group, @NotNull String name, @Nullable VirtualFile file, Icon icon) {
      myGroup = group;
      myName = name;
      myFile = file;
      myIcon = icon;
    }

    public ResourceGroup getGroup() {
      return myGroup;
    }

    public String getName() {
      return myGroup.getName() + "/" + myName;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public Icon getIcon() {
      return myIcon;
    }

    public String getPreviewString() {
      return myPreviewString;
    }

    public void setPreviewString(String previewString) {
      myPreviewString = previewString;
    }

    public List<ResourceElement> getPreviewResources() {
      return myPreviewResources;
    }

    public DefaultComboBoxModel getPreviewComboModel() {
      return myPreviewComboModel;
    }

    public String getPreviewComboDefaultSelection() {
      return myDefaultSelection;
    }

    public void setPreviewResources(List<ResourceElement> previewResources,
                                    DefaultComboBoxModel previewComboModel,
                                    String defaultSelection) {
      myPreviewResources = previewResources;
      myPreviewComboModel = previewComboModel;
      myDefaultSelection = defaultSelection;
    }

    public Icon getPreviewIcon() {
      return myPreviewIcon;
    }

    public void setPreviewIcon(Icon previewIcon) {
      myPreviewIcon = previewIcon;
    }

    @Override
    public String toString() {
      return myName;
    }
  }

  private static class TreeContentProvider extends AbstractTreeStructure {
    private final Object myTreeRoot = new Object();
    private final ResourceGroup[] myGroups;

    public TreeContentProvider(ResourceGroup[] groups) {
      myGroups = groups;
    }

    @Override
    public Object getRootElement() {
      return myTreeRoot;
    }

    @Override
    public Object[] getChildElements(Object element) {
      if (element == myTreeRoot) {
        return myGroups;
      }
      if (element instanceof ResourceGroup) {
        ResourceGroup group = (ResourceGroup)element;
        return group.getItems().toArray();
      }
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public Object getParentElement(Object element) {
      if (element instanceof ResourceItem) {
        ResourceItem resource = (ResourceItem)element;
        return resource.getGroup();
      }
      return null;
    }

    @NotNull
    @Override
    public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
      TreeNodeDescriptor descriptor = new TreeNodeDescriptor(parentDescriptor, element, element == null ? null : element.toString());
      if (element instanceof ResourceGroup) {
        descriptor.setIcon(AllIcons.Nodes.TreeClosed);
      }
      else if (element instanceof ResourceItem) {
        descriptor.setIcon(((ResourceItem)element).getIcon());
      }
      return descriptor;
    }

    @Override
    public boolean hasSomethingToCommit() {
      return false;
    }

    @Override
    public void commit() {
    }
  }

  // Copied from com.intellij.designer.componentTree.TreeNodeDescriptor
  public static final class TreeNodeDescriptor extends NodeDescriptor {
    private final Object myElement;

    public TreeNodeDescriptor(@Nullable NodeDescriptor parentDescriptor, Object element) {
      super(null, parentDescriptor);
      myElement = element;
    }

    public TreeNodeDescriptor(@Nullable NodeDescriptor parentDescriptor, Object element, String name) {
      this(parentDescriptor, element);
      myName = name;
    }

    @Override
    public boolean update() {
      return true;
    }

    @Override
    public Object getElement() {
      return myElement;
    }
  }

  public static class SizedIcon implements Icon {
    private final int myWidth;
    private final int myHeight;
    private final Image myImage;

    public SizedIcon(int maxWidth, int maxHeight, Image image) {
      myWidth = Math.min(maxWidth, image.getWidth(null));
      myHeight = Math.min(maxHeight, image.getHeight(null));
      myImage = image;
    }

    public SizedIcon(int maxWidth, int maxHeight, ImageIcon icon) {
      this(maxWidth, maxHeight, icon.getImage());
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      g.drawImage(myImage, x, y, myWidth, myHeight, null);
    }

    @Override
    public int getIconWidth() {
      return myWidth;
    }

    @Override
    public int getIconHeight() {
      return myHeight;
    }
  }

  private class ResourceDialogTabComponent extends JBScrollPane {
    private final JPanel myCenterPanel;
    private final ResourceType myResourceType;
    private final ResourceDialogSouthPanel mySouthPanel = new ResourceDialogSouthPanel();
    private ResourceNameValidator myValidator;
    private final CreateXmlResourcePanel myLocationSettings;

    public ResourceDialogTabComponent(@NotNull JPanel centerPanel, @NotNull ResourceType resourceType,
                                      @NotNull ResourceFolderType folderType) {
      super(centerPanel);
      myCenterPanel = centerPanel;
      myResourceType = resourceType;
      myLocationSettings = new CreateXmlResourcePanel(myModule, resourceType, null, folderType);
    }

    public void addResourceDialogSouthPanel(@Nullable String resourceName, final boolean allowXmlFile) {
      if (resourceName != null) {
        mySouthPanel.getResourceNameField().setText(resourceName);
      }

      myCenterPanel.add(mySouthPanel.getFullPanel(), BorderLayout.SOUTH);

      mySouthPanel.setExpertPanel(myLocationSettings.getPanel());
      myLocationSettings.addModuleComboActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          Module module = myLocationSettings.getModule();
          assert module != null;
          myValidator = ResourceNameValidator
            .create(allowXmlFile, AppResourceRepository.getAppResources(module, true), myResourceType, allowXmlFile);
        }
      });
    }

    public void addCenter(@NotNull Component component) {
      myCenterPanel.add(component);
    }

    public void setValidator(@NotNull ResourceNameValidator validator) {
      myValidator = validator;
    }

    @Nullable
    public ResourceNameValidator getValidator() {
      return myValidator;
    }

    @NotNull
    public JLabel getResourceNameMessage() {
      return mySouthPanel.getResourceNameMessage();
    }

    @NotNull
    public JTextField getResourceNameField() {
      return mySouthPanel.getResourceNameField();
    }

    @NotNull
    public CreateXmlResourcePanel getLocationSettings() {
      return myLocationSettings;
    }

    public void openLocationSettings() {
      mySouthPanel.setOn(true);
    }

    public void setChangeFileNameVisible(boolean isVisible) {
      myLocationSettings.setChangeFileNameVisible(isVisible);
    }
  }
}