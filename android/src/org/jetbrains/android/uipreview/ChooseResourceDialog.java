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
import com.android.tools.idea.editors.theme.MaterialColorUtils;
import com.android.tools.idea.editors.theme.MaterialColors;
import com.android.tools.idea.editors.theme.StateListPicker;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.javadoc.AndroidJavaDocRenderer;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.idea.rendering.ResourceNameValidator;
import com.android.tools.idea.ui.SearchField;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.event.*;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.text.View;
import java.awt.*;
import java.awt.event.*;
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
public class ChooseResourceDialog extends DialogWrapper {
  private static final String TYPE_KEY = "ResourceType";

  private static final String TEXT = "Text";
  private static final String COMBO = "Combo";
  private static final String NONE = "None";

  private static final Icon RESOURCE_ITEM_ICON = AllIcons.Css.Property;
  public static final String APP_NAMESPACE_LABEL = "Project";

  @NotNull private final Module myModule;
  @Nullable private final XmlTag myTag;

  private final JComponent myContentPanel;
  private final JTabbedPane myTabbedPane;
  private final ResourcePanel[] myPanels;
  private final SearchTextField mySearchBox;
  private final JComponent myViewOption;
  private boolean isGridMode;

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
  private boolean myUseGlobalUndo;

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

    if (ArrayUtil.contains(ResourceType.DRAWABLE, types) && !ArrayUtil.contains(ResourceType.COLOR, types)) {
      myPanels = new ResourcePanel[types.length + 1];
      myPanels[types.length] = new ResourcePanel(facet, ResourceType.COLOR, false);
    }
    else {
      myPanels = new ResourcePanel[types.length];
    }

    for (int i = 0; i < types.length; i++) {
      myPanels[i] = new ResourcePanel(facet, types[i], true);
    }

    final ToggleAction listView = new ToggleAction(null, "list", AndroidIcons.Views.ListView) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return !isGridMode;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        setGridMode(!state);
      }
    };

    final ToggleAction gridView = new ToggleAction(null, "grid", AndroidIcons.Views.GridView) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return isGridMode;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        setGridMode(state);
      }
    };

    mySearchBox = new SearchField(true);
    mySearchBox.setMaximumSize(new Dimension(JBUI.scale(300), mySearchBox.getMaximumSize().height));
    mySearchBox.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        Condition condition = new Condition() {
          private String text = mySearchBox.getText();
          @Override
          public boolean value(Object o) {
            return StringUtil.containsIgnoreCase(o.toString(), text);
          }
        };
        for (ResourcePanel panel : myPanels) {
          panel.myList.setFilter(condition);
        }
      }
    });

    myViewOption = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, new DefaultActionGroup(listView, gridView), true).getComponent();
    myViewOption.setBorder(null);
    myViewOption.setMaximumSize(new Dimension(JBUI.scale(100), myViewOption.getMaximumSize().height));

    //noinspection UndesirableClassUsage We install our own special UI, intellij stuff will break it
    myTabbedPane = new JTabbedPane(SwingConstants.LEFT);
    myTabbedPane.setUI(new SimpleTabUI());
    for (ResourcePanel panel : myPanels) {
      myTabbedPane.addTab(panel.getType().getDisplayName(), panel.myComponent);
      panel.expandAll();
    }

    if (value != null && value.startsWith("@")) {
      value = StringUtil.replace(value, "+", "");
      int index = value.indexOf('/');
      if (index != -1) {
        String name = value.substring(index + 1);
        String namespace;
        String type;
        if (value.startsWith(SdkConstants.ANDROID_PREFIX)) {
          namespace = SdkConstants.ANDROID_NS_NAME;
          // TODO actually get the package name from the resource
          type = value.substring(SdkConstants.ANDROID_PREFIX.length(), index);
        }
        else {
          namespace = null;
          type = value.substring(1, index);
        }

        ResourcePanel panel = null;
        for (ResourcePanel aPanel : myPanels) {
          if (aPanel.getType().getName().equals(type)) {
            panel = aPanel;
            break;
          }
        }
        if (panel == null) {
          throw new IllegalStateException("can not find panel for type: " + type);
        }

        myTabbedPane.setSelectedComponent(panel.myComponent);
        panel.select(namespace, name);
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
      myTabbedPane.addTab("new Color", myColorPickerPanel);
      myTabbedPane.setSelectedIndex(myTabbedPane.getTabCount() - 1);

      if (myResourceNameVisibility != ResourceNameVisibility.HIDE) {
        myColorPickerPanel.addResourceDialogSouthPanel(resourceNameSuggestion, false);
      }

      myColorPickerPanel.setValidator(
        ResourceNameValidator.create(false, AppResourceRepository.getAppResources(myModule, true), ResourceType.COLOR, false));
    }

    if (myStateListPicker != null) {
      myTabbedPane.addTab("new StateList", myStateListPickerPanel);
      myTabbedPane.setSelectedIndex(myTabbedPane.getTabCount() - 1);
    }

    myTabbedPane.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        valueChanged();
      }
    });

    JComponent toolbar = Box.createHorizontalBox();
    toolbar.add(mySearchBox);
    toolbar.add(Box.createHorizontalStrut(JBUI.scale(20)));
    toolbar.add(myViewOption);
    toolbar.setBorder(new CompoundBorder(JBUI.Borders.customLine(OnePixelDivider.BACKGROUND, 0, 0, 1, 0), JBUI.Borders.empty(8)));

    myContentPanel = new JPanel(new BorderLayout());
    myContentPanel.add(myTabbedPane);
    myContentPanel.add(toolbar, BorderLayout.NORTH);

    valueChanged();
    init();
    // we need to trigger this once before the window is made visible to update any extra labels
    doValidate();
  }

  @NotNull
  @Override
  protected DialogStyle getStyle() {
    // will draw the line between the main panel and the action buttons.
    return DialogStyle.COMPACT;
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

  @Nullable("when it is not a ResourcePanel that is currently selected (e.g creating a new color)")
  private ResourcePanel getSelectedPanel() {
    Component selectedComponent = myTabbedPane.getSelectedComponent();
    for (ResourcePanel panel : myPanels) {
      if (panel.myComponent == selectedComponent) {
        return panel;
      }
    }
    // TODO when the color picker is inside the color type tab, this method will never return null
    return null;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    Component selectedComponent = myTabbedPane.getSelectedComponent();

    final boolean okActionEnabled;
    ValidationInfo error = null;

    ResourcePanel panel = getSelectedPanel();
    if (panel != null) {
      ResourceItem element = panel.getSelectedElement();
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

    ResourcePanel panel = getSelectedPanel();
    assert panel != null; // this popup menu is ONLY visible when the panel is not null
    ResourceType resourceType = panel.getType();

    if (AndroidResourceUtil.XML_FILE_RESOURCE_TYPES.contains(resourceType)) {
      myNewResourceFileAction.getTemplatePresentation().setText("New " + resourceType + " File...");
      myNewResourceFileAction.getTemplatePresentation().putClientProperty(TYPE_KEY, resourceType);
      actionGroup.add(myNewResourceFileAction);
    }
    if (AndroidResourceUtil.VALUE_RESOURCE_TYPES.contains(resourceType)) {
      String title = "New " + resourceType + " Value...";
      if (resourceType == ResourceType.LAYOUT) {
        title = "New Layout Alias";
      }
      myNewResourceValueAction.getTemplatePresentation().setText(title);
      myNewResourceValueAction.getTemplatePresentation().putClientProperty(TYPE_KEY, resourceType);
      actionGroup.add(myNewResourceValueAction);
    }
    if (myTag != null && ResourceType.STYLE.equals(resourceType)) {
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
    // TODO.should select the first list?
    return myPanels[0].myList;
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
    ResourceDialogTabComponent tabComponent = (ResourceDialogTabComponent)myTabbedPane.getSelectedComponent();
    tabComponent.openLocationSettings();
  }

  public String getResourceName() {
    return myResultResourceName;
  }

  @Override
  protected void doOKAction() {
    valueChanged();
    if (myTabbedPane.getSelectedComponent() == myColorPickerPanel && myResourceNameVisibility != ResourceNameVisibility.HIDE) {
      String colorName = myColorPickerPanel.getResourceNameField().getText();
      Module module = myColorPickerPanel.getLocationSettings().getModule();
      String fileName = myColorPickerPanel.getLocationSettings().getFileName();
      List<String> dirNames = myColorPickerPanel.getLocationSettings().getDirNames();
      assert module != null;
      AndroidFacet facet = AndroidFacet.getInstance(module);
      assert facet != null;

      if (!AndroidResourceUtil.changeColorResource(facet, colorName, myResultResourceName, fileName, dirNames, myUseGlobalUndo)) {
        // Changing color resource has failed, one possible reason is that color isn't defined in the project.
        // Trying to create the color instead.
        AndroidResourceUtil.createValueResource(module, colorName, ResourceType.COLOR, fileName, dirNames, myResultResourceName);
      }

      myResultResourceName = SdkConstants.COLOR_RESOURCE_PREFIX + colorName;
    }
    else if (myTabbedPane.getSelectedComponent() == myStateListPickerPanel && myResourceNameVisibility != ResourceNameVisibility.HIDE) {
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

  void setGridMode(boolean gridMode) {
    isGridMode = gridMode;
    for (ResourcePanel panel : myPanels) {
      if (panel.supportsGridMode()) {
        panel.setGridMode(isGridMode);
      }
    }
  }

  private void valueChanged() {
    Component selectedComponent = myTabbedPane.getSelectedComponent();

    if (selectedComponent == myColorPickerPanel) {
      myNewResourceAction.setEnabled(false);
      myViewOption.setVisible(false);
      mySearchBox.setEnabled(false);

      Color color = myColorPicker.getColor();
      myResultResourceName = ResourceHelper.colorToString(color);
    }
    else if (selectedComponent == myStateListPickerPanel) {
      myNewResourceAction.setEnabled(false);
      myViewOption.setVisible(false);
      mySearchBox.setEnabled(false);

      myResultResourceName = null;
    }
    else {
      ResourcePanel panel = getSelectedPanel();
      assert panel != null; // we are only ever here if we have a panel
      ResourceItem element = panel.getSelectedElement();

      myNewResourceAction.setEnabled(true);
      myViewOption.setVisible(panel.supportsGridMode());
      mySearchBox.setEnabled(true);

      if (element == null) {
        myResultResourceName = null;
      }
      else {
        // TODO actually get prefix name from namespace
        String prefix = element.getGroup().getNamespace() == null ? "@" : SdkConstants.ANDROID_PREFIX;
        myResultResourceName = prefix + element.getPath();
      }

      panel.showPreview(element);
    }
    notifyResourcePickerListeners(myResultResourceName);
  }

  public void setUseGlobalUndo(boolean useGlobalUndo) {
    myUseGlobalUndo = useGlobalUndo;
  }

  private class ResourcePanel {

    /**
     * list of namespaces that we can get resources from, null means the application,
     */
    private final String[] NAMESPACES = {null, SdkConstants.ANDROID_NS_NAME};

    public final @NotNull JBSplitter myComponent;
    private final @NotNull TreeGrid myList;
    private final @NotNull JPanel myPreviewPanel;
    private final @NotNull JTextPane myHtmlTextArea;
    private final JTextArea myComboTextArea;
    private final JComboBox myComboBox;
    private final @NotNull JLabel myNoPreviewComponent;

    private final @NotNull ResourceGroup[] myGroups;
    private final @NotNull ResourceType myType;

    public ResourcePanel(@NotNull AndroidFacet facet, @NotNull ResourceType type, boolean includeFileResources) {
      myType = type;

      myGroups = new ResourceGroup[NAMESPACES.length];
      for (int c = 0; c < NAMESPACES.length; c++) {
        ResourceManager manager = facet.getResourceManager(NAMESPACES[c]);
        myGroups[c] = new ResourceGroup(NAMESPACES[c], type, manager, includeFileResources);
      }

      AbstractTreeStructure treeContentProvider = new TreeContentProvider(myGroups);

      myComponent = new JBSplitter(false, 0.5f);
      myComponent.setSplitterProportionKey("android.resource_dialog_splitter");

      myList = new TreeGrid(treeContentProvider);

      myList.addListSelectionListener(new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
          ChooseResourceDialog.this.valueChanged();
        }
      });
      new DoubleClickListener() {
        @Override
        protected boolean onDoubleClick(MouseEvent e) {
          if (myList.getSelectedElement() != null) {
            close(OK_EXIT_CODE);
            return true;
          }
          return false;
        }
      }.installOn(myList);

      JComponent firstComponent = ScrollPaneFactory.createScrollPane(myList, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                                                            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      firstComponent.setPreferredSize(JBUI.size(200,600));

      myComponent.setFirstComponent(firstComponent);

      myPreviewPanel = new JPanel(new CardLayout());
      myComponent.setSecondComponent(myPreviewPanel);

      myHtmlTextArea = new JTextPane();
      myHtmlTextArea.setEditable(false);
      myHtmlTextArea.setContentType(UIUtil.HTML_MIME);
      myPreviewPanel.add(ScrollPaneFactory.createScrollPane(myHtmlTextArea), TEXT);
      myHtmlTextArea.setPreferredSize(JBUI.size(400, 400));

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

      myNoPreviewComponent = new JLabel("No Preview");
      myNoPreviewComponent.setHorizontalAlignment(SwingConstants.CENTER);
      myNoPreviewComponent.setVerticalAlignment(SwingConstants.CENTER);
      myPreviewPanel.add(myNoPreviewComponent, NONE);

      // setup default list look and feel
      setGridMode(isGridMode);
    }

    @NotNull
    public ResourceType getType() {
      return myType;
    }

    public void showPreview(@Nullable ResourceItem element) {
      CardLayout layout = (CardLayout)myPreviewPanel.getLayout();

      if (element == null || element.getGroup().getType() == ResourceType.ID) {
        layout.show(myPreviewPanel, NONE);
        return;
      }

      String doc = AndroidJavaDocRenderer.render(myModule, element.getGroup().getType(), element.getName(), SdkConstants.ANDROID_NS_NAME.equals(element.getGroup().getNamespace()));
      myHtmlTextArea.setText(doc);
      layout.show(myPreviewPanel, TEXT);
    }

    void setGridMode(boolean gridView) {
      if (gridView) {
        assert supportsGridMode();
        final ListCellRenderer gridRenderer = new DefaultListCellRenderer() {
          {
            setHorizontalTextPosition(SwingConstants.CENTER);
            setVerticalTextPosition(SwingConstants.BOTTOM);
            setHorizontalAlignment(SwingConstants.CENTER);
          }
          @Override
          public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component component = super.getListCellRendererComponent(list, value, index, isSelected, false);
            // TODO show deprecated resources with a strikeout
            ResourceItem rItem = (ResourceItem) value;
            setIcon(rItem.getIcon(JBUI.scale(80)));
            return component;
          }
        };
        myList.setFixedCellWidth(JBUI.scale(90));
        myList.setFixedCellHeight(JBUI.scale(100));
        myList.setCellRenderer(gridRenderer);
        myList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
      }
      else {
        final ListCellRenderer listRenderer = new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component component = super.getListCellRendererComponent(list, value, index, isSelected, false);
            // TODO show deprecated resources with a strikeout
            ResourceItem rItem = (ResourceItem) value;
            setIcon(rItem.getIcon(JBUI.scale(28)));
            return component;
          }
        };
        myList.setFixedCellWidth(-1);
        myList.setFixedCellHeight(JBUI.scale(32));
        myList.setCellRenderer(listRenderer);
        myList.setLayoutOrientation(JList.VERTICAL);
      }
    }

    boolean supportsGridMode() {
      return myType == ResourceType.COLOR || myType == ResourceType.DRAWABLE || myType == ResourceType.MIPMAP;
    }

    private void select(@Nullable String namespace, @NotNull String name) {
      for (ResourceGroup group : myGroups) {
        if (Objects.equal(namespace, group.getNamespace())) {
          for (ResourceItem item : group.getItems()) {
            if (name.equals(item.getName())) {
              myList.setSelectedElement(item);
              return;
            }
          }
        }
      }
    }

    public void expandAll() {
      myList.expandAll();
    }

    public ResourceItem getSelectedElement() {
      return (ResourceItem) myList.getSelectedElement();
    }

    private void showComboPreview(@NotNull List<ResourceElement> resources) {
      assert resources.size() > 1;

      resources = Lists.newArrayList(resources);
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
      for (int i = 0; i < resources.size(); i++) {
        ResourceElement resource = resources.get(i);
        PsiDirectory directory = resource.getXmlTag().getContainingFile().getParent();
        String name = directory == null ? "unknown-" + i : directory.getName();
        model.addElement(name);
        if (defaultSelection == null && "values".equalsIgnoreCase(name)) {
          defaultSelection = name;
        }
      }

      String selection = (String)myComboBox.getSelectedItem();
      if (selection == null) {
        selection = defaultSelection;
      }

      int index = model.getIndexOf(selection);
      if (index == -1) {
        index = 0;
      }

      myComboBox.setModel(model);
      myComboBox.putClientProperty(COMBO, resources);
      myComboBox.setSelectedIndex(index);
      myComboTextArea.setText(getResourceElementValue(resources.get(index)));

      CardLayout layout = (CardLayout)myPreviewPanel.getLayout();
      layout.show(myPreviewPanel, COMBO);
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
    private final String myNamespace;
    private final ResourceType myType;
    private final ResourceManager myManager;

    public ResourceGroup(@Nullable String namespace, @NotNull ResourceType type, @NotNull ResourceManager manager, boolean includeFileResources) {
      myType = type;
      myManager = manager;
      myNamespace = namespace;

      final String resourceType = type.getName();

      Collection<String> resourceNames = manager.getValueResourceNames(resourceType);
      for (String resourceName : resourceNames) {
        myItems.add(new ResourceItem(this, resourceName, null));
      }
      final Set<String> fileNames = new HashSet<String>();

      if (includeFileResources) {
        manager.processFileResources(resourceType, new FileResourceProcessor() {
          @Override
          public boolean process(@NotNull VirtualFile resFile, @NotNull String resName, @NotNull String resFolderType) {
            if (fileNames.add(resName)) {
              myItems.add(new ResourceItem(ResourceGroup.this, resName, resFile));
            }
            return true;
          }
        });
      }

      if (type == ResourceType.ID) {
        for (String id : manager.getIds(true)) {
          if (!resourceNames.contains(id)) {
            myItems.add(new ResourceItem(this, id, null));
          }
        }
      }

      Collections.sort(myItems, new Comparator<ResourceItem>() {
        @Override
        public int compare(ResourceItem resource1, ResourceItem resource2) {
          return resource1.getName().compareTo(resource2.getName());
        }
      });
    }

    @NotNull
    public ResourceType getType() {
      return myType;
    }

    @NotNull
    public ResourceManager getManager() {
      return myManager;
    }

    @Nullable("null for app namespace")
    public String getNamespace() {
      return myNamespace;
    }

    public List<ResourceItem> getItems() {
      return myItems;
    }

    @Override
    public String toString() {
      return myNamespace == null ? APP_NAMESPACE_LABEL : myNamespace;
    }
  }

  public static class ResourceItem {
    private final ResourceGroup myGroup;
    private final String myName;
    private final VirtualFile myFile;
    private Icon myIcon;

    public ResourceItem(@NotNull ResourceGroup group, @NotNull String name, @Nullable VirtualFile file) {
      myGroup = group;
      myName = name;
      myFile = file;
    }

    public ResourceGroup getGroup() {
      return myGroup;
    }

    public String getName() {
      return myName;
    }

    public String getPath() {
      return myGroup.getType().getName() + "/" + myName;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    @Override
    public String toString() {
      // we need to return JUST the name so quicksearch in JList works
      return getName();
    }

    public Icon getIcon(int size) {
      if (myIcon == null || size != myIcon.getIconWidth()) {
        // TODO, for resources with no icon, when we use RESOURCE_ITEM_ICON, we are re-doing the lookup each time.
        Icon icon = RESOURCE_ITEM_ICON;
        if (myFile != null) {
          if (ImageFileTypeManager.getInstance().isImage(myFile)) {
            icon = new SizedIcon(size, new ImageIcon(myFile.getPath()));
          }
          else {
            icon = myFile.getFileType().getIcon();
          }
        }
        if (myGroup.getType() == ResourceType.COLOR) {
          long time = System.currentTimeMillis();
          List<ResourceElement> resources = myGroup.getManager().findValueResources(myGroup.getType().getName(), myName);
          if (ApplicationManagerEx.getApplicationEx().isInternal()) {
            System.out.println("Time: " + (System.currentTimeMillis() - time)); // XXX
          }
          if (!resources.isEmpty()) {
            String value = getResourceElementValue(resources.get(0));
            if (value.startsWith("#")) {
              Color color = ResourceHelper.parseColor(value);
              if (color != null) { // maybe null for invalid color
                icon = new ColorIcon(size, color);
              }
            }
          }
          // TODO maybe have a different icon when the resource points to more then 1 color
        }
        myIcon = icon;
      }
      return myIcon;
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
        descriptor.setIcon(((ResourceItem)element).getIcon(AllIcons.Nodes.TreeClosed.getIconWidth()));
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
    private final int mySize;
    private final Image myImage;

    public SizedIcon(int size, Image image) {
      mySize = size;
      myImage = image;
    }

    public SizedIcon(int size, ImageIcon icon) {
      this(size, icon.getImage());
    }

    @Override
    public void paintIcon(Component c, Graphics g, int i, int j) {
      double scale = Math.min(getIconHeight()/(double)myImage.getHeight(c),getIconWidth()/(double)myImage.getWidth(c));
      int x = (int) (getIconWidth() - (myImage.getWidth(c) * scale)) / 2;
      int y = (int) (getIconHeight() - (myImage.getHeight(c) * scale)) / 2;
      ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(myImage, i + x, j + y, (int) (myImage.getWidth(c) * scale), (int) (myImage.getHeight(c) * scale), null);
    }

    @Override
    public int getIconWidth() {
      return mySize;
    }

    @Override
    public int getIconHeight() {
      return mySize;
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

  private static class SimpleTabUI extends BasicTabbedPaneUI {

    @Override
    protected void installDefaults() {
      super.installDefaults();
      tabInsets = JBUI.insets(8);
      selectedTabPadInsets = JBUI.emptyInsets();
    }

    @Override
    protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
      // dont want tab border
    }

    @Override
    protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
      // dont want border here either
    }

    @Override
    protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
      // dont want a background
    }

    @Override
    protected int getTabLabelShiftX(int tabPlacement, int tabIndex, boolean isSelected) {
      return super.getTabLabelShiftX(tabPlacement, tabIndex, false);
    }

    @Override
    protected int getTabLabelShiftY(int tabPlacement, int tabIndex, boolean isSelected) {
      return super.getTabLabelShiftY(tabPlacement, tabIndex, false);
    }

    @Override
    protected void layoutLabel(int tabPlacement,
                               FontMetrics metrics, int tabIndex,
                               String title, Icon icon,
                               Rectangle tabRect, Rectangle iconRect,
                               Rectangle textRect, boolean isSelected ) {
      textRect.x = textRect.y = iconRect.x = iconRect.y = 0;

      View v = getTextViewForTab(tabIndex);
      if (v != null) {
        tabPane.putClientProperty("html", v);
      }

      // CHANGE FROM DEFAULT: take tab insets into account
      Insets insets = getTabInsets(tabPlacement, tabIndex);
      tabRect = new Rectangle(tabRect);
      tabRect.x += insets.left;
      tabRect.y += insets.top;
      tabRect.width = tabRect.width - insets.left - insets.right;
      tabRect.height = tabRect.height - insets.top - insets.bottom;

      SwingUtilities.layoutCompoundLabel(tabPane,
                                         metrics, title, icon,
                                         SwingUtilities.CENTER,
                                         SwingUtilities.LEADING, // CHANGE FROM DEFAULT
                                         SwingUtilities.CENTER,
                                         SwingUtilities.TRAILING,
                                         tabRect,
                                         iconRect,
                                         textRect,
                                         textIconGap);

      tabPane.putClientProperty("html", null);

      int xNudge = getTabLabelShiftX(tabPlacement, tabIndex, isSelected);
      int yNudge = getTabLabelShiftY(tabPlacement, tabIndex, isSelected);
      iconRect.x += xNudge;
      iconRect.y += yNudge;
      textRect.x += xNudge;
      textRect.y += yNudge;
    }

    @Override
    protected void paintText(Graphics g, int tabPlacement,
                             Font font, FontMetrics metrics, int tabIndex,
                             String title, Rectangle textRect,
                             boolean isSelected) {

      g.setFont(font);

      View v = getTextViewForTab(tabIndex);
      if (v != null) {
        // html
        v.paint(g, textRect);
      } else {
        // plain text
        int mnemIndex = tabPane.getDisplayedMnemonicIndexAt(tabIndex);

        if (tabPane.isEnabled() && tabPane.isEnabledAt(tabIndex)) {
          Color fg = tabPane.getForegroundAt(tabIndex);
          if (isSelected && (fg instanceof UIResource)) {
            Color selectedFG = JBColor.BLUE; // CHANGE FROM DEFAULT
            if (selectedFG != null) {
              fg = selectedFG;
            }
          }
          g.setColor(fg);
          SwingUtilities2.drawStringUnderlineCharAt(tabPane, g,
                                                    title, mnemIndex,
                                                    textRect.x, textRect.y + metrics.getAscent());
        } else { // tab disabled
          g.setColor(tabPane.getBackgroundAt(tabIndex).brighter());
          SwingUtilities2.drawStringUnderlineCharAt(tabPane, g,
                                                    title, mnemIndex,
                                                    textRect.x, textRect.y + metrics.getAscent());
          g.setColor(tabPane.getBackgroundAt(tabIndex).darker());
          SwingUtilities2.drawStringUnderlineCharAt(tabPane, g,
                                                    title, mnemIndex,
                                                    textRect.x - 1, textRect.y + metrics.getAscent() - 1);
        }
      }
    }
  }
}
