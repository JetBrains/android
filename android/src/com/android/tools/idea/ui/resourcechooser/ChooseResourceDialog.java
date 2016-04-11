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
package com.android.tools.idea.ui.resourcechooser;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.ResourceUrl;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.*;
import com.android.tools.idea.editors.theme.attributes.editors.DrawableRendererEditor;
import com.android.tools.idea.editors.theme.attributes.editors.GraphicalResourceRendererEditor;
import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.javadoc.AndroidJavaDocRenderer;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.res.ProjectResourceRepository;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.res.ResourceNameValidator;
import com.android.tools.idea.ui.SearchField;
import com.android.tools.swing.ui.SwatchComponent;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.ObjectArrays;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.MultiMap;
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
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
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
public class ChooseResourceDialog extends DialogWrapper {

  private static final Logger LOG = Logger.getInstance(ChooseResourceDialog.class);

  private static final String TYPE_KEY = "ResourceType";
  private static final String FOLDER_TYPE_KEY = "ResourceFolderType";

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

  // if we are picking a resource that can't be a color, then all these are null
  private @Nullable ResourceEditorTab myColorPickerPanel;
  private @Nullable ColorPicker myColorPicker;

  // we can ONLY ever have the statelist picker in {@link ResourceType#COLOR} or {@link ResourceType#DRAWABLE} mode.
  // We only ever need one stateList picker because Android can never allow picking both types for any attribute.
  private @Nullable ResourceEditorTab myStateListPickerPanel;
  private @Nullable StateListPicker myStateListPicker;

  private @Nullable ResourcePickerListener myResourcePickerListener;
  private @NotNull ImmutableMap<String, Color> myContrastColorsWithDescription = ImmutableMap.of();
  private boolean myIsBackgroundColor;

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
      ResourceFolderType type = (ResourceFolderType)getTemplatePresentation().getClientProperty(FOLDER_TYPE_KEY);
      createNewResourceFile(type);
    }
  };
  private final AnAction myExtractStyleAction = new AnAction("Extract Style...") {
    @Override
    public void actionPerformed(AnActionEvent e) {
      extractStyle();
    }
  };

  private final AnAction myNewResourceReferenceAction = new AnAction() {
    @Override
    public void actionPerformed(AnActionEvent e) {
      ResourcePanel panel = getSelectedPanel();
      panel.showNewResource(panel.myReferencePanel);
    }
  };

  private String myResultResourceName;

  private boolean myUseGlobalUndo;
  private RenderTask myRenderTask;

  public interface ResourcePickerListener {
    void resourceChanged(@Nullable String resource);
  }

  public ChooseResourceDialog(@NotNull Module module, @NotNull ResourceType[] types, @Nullable String value, @Nullable XmlTag tag) {
    this(module, types, value, false, tag, ResourceNameVisibility.SHOW, null);
  }

  public ChooseResourceDialog(@NotNull Module module,
                              @NotNull ResourceType[] types,
                              @NotNull String value,
                              boolean isFrameworkValue,
                              @NotNull ResourceNameVisibility resourceNameVisibility,
                              @Nullable String resourceNameSuggestion) {
    this(module, types, value, isFrameworkValue, null, resourceNameVisibility, resourceNameSuggestion);
  }

  private ChooseResourceDialog(@NotNull Module module,
                               @NotNull ResourceType[] types,
                               final @Nullable String value,
                               boolean isFrameworkValue,
                               @Nullable XmlTag tag,
                               @NotNull ResourceNameVisibility resourceNameVisibility,
                               @Nullable String resourceNameSuggestion) {
    super(module.getProject());
    myModule = module;
    myTag = tag;
    if (resourceNameSuggestion != null &&
        (resourceNameSuggestion.startsWith(SdkConstants.PREFIX_RESOURCE_REF) ||
         resourceNameSuggestion.startsWith(SdkConstants.PREFIX_THEME_REF) ||
         resourceNameSuggestion.startsWith("#"))) {
      throw new IllegalArgumentException("invalid name suggestion " + resourceNameSuggestion);
    }
    setTitle("Resources");

    boolean allowDrawables = ArrayUtil.contains(ResourceType.DRAWABLE, types);
    boolean allowColors = ArrayUtil.contains(ResourceType.COLOR, types);

    if (allowDrawables && !allowColors) {
      types = ObjectArrays.concat(types, ResourceType.COLOR);
      allowColors = true;
    }

    Configuration configuration = ThemeEditorUtils.getConfigurationForModule(myModule);
    ResourceResolver resolver = getResourceResolver();

    MultiMap<ResourceType, String> attrs = new MultiMap<ResourceType, String>();
    String themeName = configuration.getTheme();
    assert themeName != null;
    for (ItemResourceValue item : ResolutionUtils.getThemeAttributes(resolver, themeName)) {
      ResourceType type = ResolutionUtils.getAttrType(item, configuration);
      if (type != null) {
        attrs.putValue(type, ResolutionUtils.getQualifiedItemName(item));
      }
    }

    ResourceValue resValue = null;
    if (value != null) {
      resValue = resolver.findResValue(value, isFrameworkValue);
    }

    if (allowDrawables || allowColors) {

      final ResourceType stateListType;
      final ResourceFolderType stateListFolderType;
      if (allowDrawables) {
        stateListType = ResourceType.DRAWABLE;
        stateListFolderType = ResourceFolderType.DRAWABLE;
      }
      else {
        stateListType = ResourceType.COLOR;
        stateListFolderType = ResourceFolderType.COLOR;
      }

      ResourceHelper.StateList stateList = null;
      if (resValue != null) {
        stateList = ResourceHelper.resolveStateList(resolver, resValue, myModule.getProject());
        if (stateList != null && stateList.getType() != stateListType) {
          // this is very strange, this means we have asked to open the resource picker to allow drawables but with a color statelist
          // or to 'not allow drawables', but with a drawables statelist, must be a user error, this should not normally happen.
          LOG.warn("StateList type mismatch " + stateList.getType() + " " + stateListType);
          stateList = null;
        }
      }

      myStateListPicker = new StateListPicker(stateList, myModule, configuration);
      myStateListPickerPanel = new ResourceEditorTab(myModule, "Statelist", myStateListPicker, ResourceNameVisibility.FORCE,
                                                     true, stateListFolderType, false, stateListType) {
        @Override
        @Nullable("if there is no error")
        public ValidationInfo doValidate() {
          ValidationInfo error = super.doValidate();
          if (error == null) {
            int minDirectoriesApi = ThemeEditorUtils.getMinFolderApi(getLocationSettings().getDirNames(), myModule);
            error = myStateListPicker.doValidate(minDirectoriesApi);
          }
          return error;
        }

        @NotNull
        @Override
        public String doSave() {
          String stateListName = getSelectedPanel().myEditorPanel.getResourceName();
          Module module = getSelectedModule();
          VirtualFile resDir = getResourceDirectory();
          List<String> dirNames = getLocationSettings().getDirNames();
          ResourceFolderType resourceFolderType = ResourceFolderType.getFolderType(dirNames.get(0));
          ResourceType resourceType = ResourceType.getEnum(resourceFolderType.getName());

          Project project = module.getProject();
          List<VirtualFile> files = null;
          if (resDir == null) {
            AndroidUtils.reportError(project, AndroidBundle.message("check.resource.dir.error", module.getName()));
          } else {
            if (resourceType != null) {
              files = AndroidResourceUtil.findOrCreateStateListFiles(project, resDir, resourceFolderType, resourceType,
                                                                     stateListName, dirNames);
            }
          }
          if (files != null) {
            assert myStateListPicker != null;
            ResourceHelper.StateList stateList = myStateListPicker.getStateList();
            assert stateList != null;
            AndroidResourceUtil.updateStateList(project, stateList, files);
          }

          if (resourceFolderType == ResourceFolderType.COLOR) {
            return SdkConstants.COLOR_RESOURCE_PREFIX + stateListName;
          }
          assert resourceFolderType == ResourceFolderType.DRAWABLE;
          return SdkConstants.DRAWABLE_PREFIX + stateListName;
        }
      };

      Color color = null;
      if (resValue != null) {
        color = ResourceHelper.resolveColor(resolver, resValue, myModule.getProject());
      }

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

      myColorPickerPanel = new ResourceEditorTab(myModule, "Color", myColorPicker, resourceNameVisibility,
                                                 false, ResourceFolderType.VALUES, true, ResourceType.COLOR) {
        @NotNull
        @Override
        public String doSave() {
          String value = ResourceHelper.colorToString(myColorPicker.getColor());
          if (getResourceNameVisibility() == ResourceNameVisibility.FORCE ||
              (getResourceNameVisibility() == ResourceNameVisibility.SHOW && !getSelectedPanel().myEditorPanel.getResourceName().isEmpty())) {
            value = saveValuesResource(getSelectedPanel().myEditorPanel.getResourceName(), value, getLocationSettings());
          }
          // else we use the value we got at the start of the method
          return value;
        }
      };
    }

    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assert facet != null;

    myPanels = new ResourcePanel[types.length];
    for (int i = 0; i < types.length; i++) {
      // only show color state lists if we are not showing drawables
      myPanels[i] = new ResourcePanel(facet, types[i], types[i] != ResourceType.COLOR || !allowDrawables,
                                      resourceNameSuggestion, attrs.get(types[i]));
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
        final String text = mySearchBox.getText();
        Condition colorCondition = null;
        if (text.startsWith("#")) {
          final Color color = ResourceHelper.parseColor(text);
          if (color != null) {
            colorCondition = new Condition<ResourceItem>() {
              @Override
              public boolean value(@NotNull ResourceItem item) {
                assert item.getGroup().getType() == ResourceType.COLOR; // we don't want to search non-colors
                return ResourceHelper.resolveMultipleColors(getResourceResolver(), item.getResourceValue(), myModule.getProject()).contains(color);
              }
            };
          }
        }
        Condition condition = new Condition<ResourceItem>() {
          @Override
          public boolean value(@NotNull ResourceItem item) {
            if (item.getGroup().getType() == ResourceType.STRING) {
              String string = ResourceHelper.resolveStringValue(getResourceResolver(), item.getResourceUrl());
              if (StringUtil.containsIgnoreCase(string, text)) {
                return true;
              }
            }
            return StringUtil.containsIgnoreCase(item.getName(), text);
          }
        };

        for (ResourcePanel panel : myPanels) {
          if (panel.getType() == ResourceType.COLOR && colorCondition != null) {
            panel.myList.setFilter(colorCondition);
          }
          else {
            panel.myList.setFilter(condition);
          }
        }
      }
    });

    myViewOption = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, new DefaultActionGroup(listView, gridView), true).getComponent();
    myViewOption.setBorder(null);
    myViewOption.setMaximumSize(new Dimension(JBUI.scale(100), myViewOption.getMaximumSize().height));

    //noinspection UndesirableClassUsage We install our own special UI, intellij stuff will break it
    myTabbedPane = new JTabbedPane(SwingConstants.LEFT);
    myTabbedPane.setName("ResourceTypeTabs"); // for UI tests
    myTabbedPane.setUI(new SimpleTabUI());
    for (ResourcePanel panel : myPanels) {
      myTabbedPane.addTab(panel.getType().getDisplayName(), panel.myComponent);
      panel.expandAll();
    }

    // "@color/black" or "@android:color/black"
    if (resValue != null) {
      ResourceType type;
      if (resValue instanceof ItemResourceValue) { // type is always null for ItemResourceValue
        type = ResolutionUtils.getAttrType((ItemResourceValue)resValue, configuration);
      }
      else {
        type = resValue.getResourceType();
      }
      ResourcePanel panel = null;
      for (ResourcePanel aPanel : myPanels) {
        if (aPanel.getType().equals(type)) {
          panel = aPanel;
          break;
        }
      }
      // panel is null if the reference is incorrect, e.g. "@sdfgsdfgs" (user error).
      if (panel != null) {
        myTabbedPane.setSelectedComponent(panel.myComponent);
        panel.select(resValue);
      }
    }

    myTabbedPane.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        setupViewOptions();
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

    setupViewOptions();
    init();
    // we need to trigger this once before the window is made visible to update any extra labels
    doValidate();
  }

  private void setupViewOptions() {
    myViewOption.setVisible(getSelectedPanel().supportsGridMode());
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
    myContrastColorsWithDescription = contrastColorsWithDescription;
    myIsBackgroundColor = isBackground;
  }

  @Override
  protected boolean postponeValidation() {
    return false;
  }

  public enum ResourceNameVisibility {
    /**
     * Show field, but do not force name to be used.
     */
    SHOW,

    /**
     * Force creation of named color.
     */
    FORCE
  }

  @NotNull
  private ResourcePanel getSelectedPanel() {
    Component selectedComponent = myTabbedPane.getSelectedComponent();
    for (ResourcePanel panel : myPanels) {
      if (panel.myComponent == selectedComponent) {
        return panel;
      }
    }
    throw new IllegalStateException();
  }

  @Override
  @Nullable("if there is no error")
  protected ValidationInfo doValidate() {
    return getSelectedPanel().doValidate();
  }

  public void setResourcePickerListener(@Nullable ResourcePickerListener resourcePickerListener) {
    myResourcePickerListener = resourcePickerListener;
  }

  private void notifyResourcePickerListeners(@Nullable String resource) {
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
      assert myColorPicker != null;
      myColorPicker.setRecommendedColors(suggestedColors);
    }
  }

  private ActionPopupMenu createNewResourcePopupMenu() {
    ActionManager actionManager = ActionManager.getInstance();
    DefaultActionGroup actionGroup = new DefaultActionGroup();

    ResourcePanel panel = getSelectedPanel();
    ResourceType resourceType = panel.getType();

    ResourceFolderType folderType = AndroidResourceUtil.XML_FILE_RESOURCE_TYPES.get(resourceType);
    if (folderType != null) {
      myNewResourceFileAction.getTemplatePresentation().setText("New " + folderType.getName() + " File...");
      myNewResourceFileAction.getTemplatePresentation().putClientProperty(FOLDER_TYPE_KEY, folderType);
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
    if (Arrays.asList(GraphicalResourceRendererEditor.COLORS_AND_DRAWABLES).contains(resourceType)) {
      myNewResourceReferenceAction.getTemplatePresentation().setText("New " + resourceType + " Reference...");
      actionGroup.add(myNewResourceReferenceAction);
    }

    return actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, actionGroup);
  }

  private void createNewResourceValue(ResourceType resourceType) {
    if (resourceType == ResourceType.COLOR && myColorPickerPanel != null) {
      getSelectedPanel().showNewResource(myColorPickerPanel);
      return;
    }

    CreateXmlResourceDialog dialog = new CreateXmlResourceDialog(myModule, resourceType, null, null, true,
                                                                 null, null);
    dialog.setTitle("New " + StringUtil.capitalize(resourceType.getDisplayName()) + " Value Resource");
    if (!dialog.showAndGet()) {
      return;
    }

    Project project = myModule.getProject();
    final VirtualFile resDir = dialog.getResourceDirectory();
    if (resDir == null) {
      AndroidUtils.reportError(project, AndroidBundle.message("check.resource.dir.error", myModule));
      return;
    }

    String fileName = dialog.getFileName();
    List<String> dirNames = dialog.getDirNames();
    String resValue = dialog.getValue();
    String resName = dialog.getResourceName();
    if (!AndroidResourceUtil.createValueResource(project, resDir, resName, resourceType, fileName, dirNames, resValue)) {
      return;
    }

    PsiDocumentManager.getInstance(myModule.getProject()).commitAllDocuments();

    myResultResourceName = "@" + resourceType.getName() + "/" + resName;
    close(OK_EXIT_CODE);
  }

  private void createNewResourceFile(ResourceFolderType folderType) {
    // if we are not showing the stateList picker, and we do have a stateList in it, then we can open it to allow the user to edit it.
    if (myStateListPicker != null && myStateListPicker.getStateList() != null &&
        folderType == myStateListPicker.getStateList().getFolderType()) {
      assert myStateListPickerPanel != null;
      getSelectedPanel().showNewResource(myStateListPickerPanel);
      return;
    }

    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assert facet != null;
    XmlFile newFile = CreateResourceFileAction.createFileResource(facet, folderType, null, null, null, true, null,
                                                                  null, null);

    if (newFile != null) {
      String name = newFile.getName();
      int index = name.lastIndexOf('.');
      if (index != -1) {
        name = name.substring(0, index);
      }
      myResultResourceName = "@" + folderType.getName() + "/" + name;
      close(OK_EXIT_CODE);
    }
  }

  private void extractStyle() {
    assert myTag != null;
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
    if (myColorPickerPanel != null) {
      myColorPickerPanel.setLocationSettingsOpen(true);
    }
    if (myStateListPickerPanel != null) {
      myStateListPickerPanel.setLocationSettingsOpen(true);
    }
    for (ResourcePanel panel : myPanels) {
      panel.myReferencePanel.setLocationSettingsOpen(true);
    }
  }

  public String getResourceName() {
    return myResultResourceName;
  }

  @Override
  protected void doOKAction() {
    ResourcePanel resourcePanel = getSelectedPanel();
    ResourceEditorTab editor = resourcePanel.getCurrentResourceEditor();

    // we are about to close, and potentially create/edit resources, that may cause all sorts of refreshes, so lets clear any live preview values.
    notifyResourcePickerListeners(null);

    if (editor != null) {
      myResultResourceName = editor.doSave();
    }
    else {
      ResourceItem element = resourcePanel.getSelectedElement();
      myResultResourceName = element != null ? element.getResourceUrl() : null;
    }
    super.doOKAction();
  }

  private void setGridMode(boolean gridMode) {
    isGridMode = gridMode;
    for (ResourcePanel panel : myPanels) {
      if (panel.supportsGridMode()) {
        panel.setGridMode(isGridMode);
      }
    }
  }

  public void setUseGlobalUndo(boolean useGlobalUndo) {
    myUseGlobalUndo = useGlobalUndo;
  }

  @NotNull
  private Icon getIcon(@NotNull ResourceItem item, int size) {
    Icon icon = item.getIcon();
    if (icon != null && size == icon.getIconWidth()) {
      return icon;
    }

    VirtualFile file = item.getFile();
    ResourceGroup group = item.getGroup();

    if (file != null && ImageFileTypeManager.getInstance().isImage(file)) {
      icon = new SizedIcon(size, new ImageIcon(file.getPath()));
    }
    else if (group.getType() == ResourceType.DRAWABLE || group.getType() == ResourceType.MIPMAP) {
      // TODO can we just use ResourceUrl here instead?
      BufferedImage image = getRenderTask().renderDrawable(item.getResourceValue());
      if (image != null) {
        icon = new SizedIcon(size, image);
      }
      // TODO maybe have a different icon for state list drawable
    }
    else if (group.getType() == ResourceType.COLOR) {
      Color color = ResourceHelper.resolveColor(getResourceResolver(), item.getResourceValue(), myModule.getProject());
      if (color != null) { // maybe null for invalid color
        icon = new ColorIcon(size, color);
      }
      // TODO maybe have a different icon when the resource points to more then 1 color
    }

    if (icon == null) {
      // TODO, for resources with no icon, when we use RESOURCE_ITEM_ICON, we should not redo the lookup each time.
      icon = file == null || file.getFileType().getIcon() == null ? RESOURCE_ITEM_ICON : file.getFileType().getIcon();
    }
    item.setIcon(icon);
    return icon;
  }

  @NotNull
  private SwatchComponent.SwatchIcon getSwatchIcon(@Nullable String name) {
    return StateListPicker.getSwatchIcon(name, getResourceResolver(), getRenderTask());
  }

  @NotNull
  private ResourceResolver getResourceResolver() {
    Configuration config = ThemeEditorUtils.getConfigurationForModule(myModule);
    ResourceResolver resolver = config.getResourceResolver();
    assert resolver != null;
    return resolver;
  }

  @NotNull
  private RenderTask getRenderTask() {
    if (myRenderTask == null) {
      myRenderTask = DrawableRendererEditor.configureRenderTask(myModule, ThemeEditorUtils.getConfigurationForModule(myModule));
      myRenderTask.setMaxRenderSize(150, 150); // don't make huge images here
    }
    return myRenderTask;
  }

  private static String getResourceElementValue(ResourceElement element) {
    String text = element.getRawText();
    if (StringUtil.isEmpty(text)) {
      return element.getXmlTag().getText();
    }
    return text;
  }

  /**
   * Saves any value that can be saved into the values.xml file and does not require its own file.
   * @param value of the resource being edited to be saved
   * @return the value that is returned by the resource chooser.
   */
  @NotNull
  private String saveValuesResource(@NotNull String name, @NotNull String value, @NotNull CreateXmlResourcePanel locationSettings) {
    ResourceType type = locationSettings.getType();
    String fileName = locationSettings.getFileName();
    List<String> dirNames = locationSettings.getDirNames();

    Project project = myModule.getProject();
    final VirtualFile resDir = locationSettings.getResourceDirectory();
    if (resDir == null) {
      AndroidUtils.reportError(project, AndroidBundle.message("check.resource.dir.error", myModule.getName()));
    } else {
      if (!AndroidResourceUtil.changeValueResource(project, resDir, name, type, value, fileName, dirNames, myUseGlobalUndo)) {
        // Changing value resource has failed, one possible reason is that resource isn't defined in the project.
        // Trying to create the resource instead.
        AndroidResourceUtil.createValueResource(project, resDir, name, type, fileName, dirNames, value);
      }
    }
    return SdkConstants.PREFIX_RESOURCE_REF + type + "/" + name;
  }

  @NotNull
  private static ResourceType[] getAllowedTypes(@NotNull ResourceType type) {
    switch(type) {
      case COLOR:
        return GraphicalResourceRendererEditor.COLORS_ONLY;
      case DRAWABLE:
        return GraphicalResourceRendererEditor.DRAWABLES_ONLY;
      default:
        return new ResourceType[] { type };
    }
  }

  private class ResourcePanel {

    private static final String NONE = "None";
    private static final String TEXT = "Text";
    private static final String EDITOR = "Editor";

    private static final String COMBO = "Combo";

    /**
     * list of namespaces that we can get resources from, null means the application,
     */
    private final String[] NAMESPACES = {null, SdkConstants.ANDROID_NS_NAME};

    public final @NotNull JBSplitter myComponent;
    private final @NotNull TreeGrid myList;
    private final @NotNull JPanel myPreviewPanel;

    private final @NotNull JLabel myNoPreviewComponent;
    private final @NotNull JTextPane myHtmlTextArea;
    private final @NotNull EditResourcePanel myEditorPanel;

    private final @NotNull ResourceComponent myReferenceComponent;
    private final @NotNull ResourceEditorTab myReferencePanel;

    private final @NotNull ResourceGroup[] myGroups;
    private final @NotNull ResourceType myType;

    // TODO can be removed
    private final JTextArea myComboTextArea;
    private final JComboBox myComboBox;

    public ResourcePanel(@NotNull AndroidFacet facet, @NotNull ResourceType type, boolean includeFileResources,
                         @Nullable String resourceNameSuggestion, @NotNull Collection<String> attrs) {
      myType = type;

      myGroups = new ResourceGroup[NAMESPACES.length + 1];
      for (int c = 0; c < NAMESPACES.length; c++) {
        myGroups[c] = new ResourceGroup(NAMESPACES[c] == null ? APP_NAMESPACE_LABEL : NAMESPACES[c], type, facet, NAMESPACES[c], includeFileResources);
      }

      myGroups[NAMESPACES.length] = new ResourceGroup("Theme attributes", myType, attrs);

      AbstractTreeStructure treeContentProvider = new TreeContentProvider(myGroups);

      myComponent = new JBSplitter(false, 0.5f);
      myComponent.setSplitterProportionKey("android.resource_dialog_splitter");

      myList = new TreeGrid(treeContentProvider);

      myList.addListSelectionListener(new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
          showPreview(getSelectedElement(), true);
          notifyResourcePickerListeners(getValueForLivePreview());
        }
      });
      new DoubleClickListener() {
        @Override
        protected boolean onDoubleClick(MouseEvent e) {
          ResourceItem selected = getSelectedElement();
          if (selected != null) {
            myResultResourceName = selected.getResourceUrl();
            close(OK_EXIT_CODE);
            return true;
          }
          return false;
        }
      }.installOn(myList);

      JScrollPane firstComponent = ScrollPaneFactory.createScrollPane(myList, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                                                            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      firstComponent.getVerticalScrollBar().setUnitIncrement(JBUI.scale(16));
      firstComponent.setBorder(null);
      firstComponent.setPreferredSize(JBUI.size(200,600));

      myComponent.setFirstComponent(firstComponent);

      myPreviewPanel = new JPanel(new CardLayout());
      myComponent.setSecondComponent(myPreviewPanel);

      myHtmlTextArea = new JTextPane();
      myHtmlTextArea.setEditable(false);
      myHtmlTextArea.setContentType(UIUtil.HTML_MIME);
      myPreviewPanel.add(ScrollPaneFactory.createScrollPane(myHtmlTextArea, true), TEXT);
      myHtmlTextArea.setPreferredSize(JBUI.size(400, 400));

      myNoPreviewComponent = new JLabel("No Preview");
      myNoPreviewComponent.setHorizontalAlignment(SwingConstants.CENTER);
      myNoPreviewComponent.setVerticalAlignment(SwingConstants.CENTER);
      myPreviewPanel.add(myNoPreviewComponent, NONE);

      myReferenceComponent = new ResourceComponent(myModule.getProject(), true);
      myReferenceComponent.addSwatchListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {

          final String attributeValue = myReferenceComponent.getValueText();
          ResourceUrl attributeValueUrl = ResourceUrl.parse(attributeValue);
          boolean isFrameworkValue = attributeValueUrl != null && attributeValueUrl.framework;
          String nameSuggestion = attributeValueUrl != null ? attributeValueUrl.name : null;
          final ChooseResourceDialog dialog = new ChooseResourceDialog(myReferencePanel.getSelectedModule(), getAllowedTypes(myType), attributeValue, isFrameworkValue,
                                                                       ResourceNameVisibility.FORCE, nameSuggestion);
          if (myResourcePickerListener != null) {
            dialog.setResourcePickerListener(myResourcePickerListener);
          }
          if (!myContrastColorsWithDescription.isEmpty()) {
            dialog.setContrastParameters(myContrastColorsWithDescription, myIsBackgroundColor, true);
          }
          dialog.show();

          if (dialog.isOK()) {
            String resourceName = dialog.getResourceName();
            myReferenceComponent.setValueText(resourceName);
            myReferenceComponent.repaint();
          }
          else {
            // reset live preview to original value
            notifyResourcePickerListeners(myReferenceComponent.getValueText());
          }
        }
      });
      myReferenceComponent.addTextDocumentListener(new com.intellij.openapi.editor.event.DocumentAdapter() {
        @Override
        public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent e) {
          // This is run inside a WriteAction and updateIcon may need an APP_RESOURCES_LOCK from AndroidFacet.
          // To prevent a potential deadlock, we call updateIcon in another thread.
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              SwatchComponent.SwatchIcon icon = getSwatchIcon(myReferenceComponent.getValueText());
              if (icon instanceof SwatchComponent.ColorIcon) {
                SwatchComponent.ColorIcon colorIcon = (SwatchComponent.ColorIcon)icon;
                myReferenceComponent.setWarning(ColorUtils.getContrastWarningMessage(myContrastColorsWithDescription,  colorIcon.getColor(), myIsBackgroundColor));
              }
              else {
                myReferenceComponent.setWarning(null);
              }
              myReferenceComponent.setSwatchIcon(icon);
              notifyResourcePickerListeners(myReferenceComponent.getValueText());
              myReferenceComponent.repaint();
            }
          });
        }
      });
      // TODO, what if we change module in the resource editor, we should update the auto complete to match
      myReferenceComponent.setCompletionStrings(ResourceHelper.getCompletionFromTypes(facet, getAllowedTypes(myType)));

      Box referenceComponentPanel = new Box(BoxLayout.Y_AXIS);
      referenceComponentPanel.setName("ReferenceEditor"); // for UI tests
      referenceComponentPanel.add(myReferenceComponent);
      referenceComponentPanel.add(Box.createVerticalGlue());
      myReferencePanel = new ResourceEditorTab(myModule, "Reference", referenceComponentPanel, ResourceNameVisibility.FORCE,
                                               false, ResourceFolderType.VALUES, true, myType) {
        @Override
        @Nullable("if there is no error")
        public ValidationInfo doValidate() {
          ValidationInfo error = super.doValidate();
          if (error == null) {
            int minDirectoriesApi = ThemeEditorUtils.getMinFolderApi(getLocationSettings().getDirNames(), myModule);
            IAndroidTarget target = ThemeEditorUtils.getConfigurationForModule(myModule).getTarget();
            assert target != null;
            final AndroidTargetData androidTargetData = AndroidTargetData.getTargetData(target, myModule);
            assert androidTargetData != null;
            error = myReferenceComponent.doValidate(minDirectoriesApi, androidTargetData);
          }
          return error;
        }

        @NotNull
        @Override
        public String doSave() {
          return saveValuesResource(myEditorPanel.getResourceName(), myReferenceComponent.getValueText(), getLocationSettings());
        }
      };

      // setup default list look and feel
      setGridMode(isGridMode);

      myEditorPanel = new EditResourcePanel(resourceNameSuggestion);
      myEditorPanel.addVariantActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          // user has selected a different variant for the current resource, so we need to display it
          getSelectedPanel().editResourceItem(myEditorPanel.getSelectedVariant());
        }
      });

      myEditorPanel.addTab(myReferencePanel);
      if (myType == ResourceType.COLOR) {
        assert myColorPickerPanel != null;
        myEditorPanel.addTab(myColorPickerPanel);
      }
      if (myStateListPicker != null && myStateListPicker.getStateList() != null && myType == myStateListPicker.getStateList().getType()) {
        assert myStateListPickerPanel != null;
        myEditorPanel.addTab(myStateListPickerPanel);
      }
      myPreviewPanel.add(myEditorPanel, EDITOR);

      showPreview(null, true);

      // TODO this code can prob be removed
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
    }

    @NotNull
    public ResourceType getType() {
      return myType;
    }

    public void showPreview(@Nullable ResourceItem element, boolean allowEditor) {
      CardLayout layout = (CardLayout)myPreviewPanel.getLayout();

      // TODO maybe have a element of "new Color" and "new StateList"

      if (element != null && element.isAttr()) {
        ResourceUrl url = ResourceUrl.parse(element.getResourceUrl());
        assert url != null;
        String doc = AndroidJavaDocRenderer.render(myModule, ThemeEditorUtils.getConfigurationForModule(myModule), url);
        myHtmlTextArea.setText(doc);
        layout.show(myPreviewPanel, TEXT);
        return;
      }

      if (allowEditor) {
        if ((myType == ResourceType.COLOR || myType == ResourceType.DRAWABLE) && element != null) {
          ProjectResourceRepository repository = ProjectResourceRepository.getProjectResources(myModule, true);
          assert repository != null;
          boolean inProject = repository.hasResourceItem(element.getGroup().getType(), element.getName());
          if (inProject) {
            layout.show(myPreviewPanel, EDITOR);
            myEditorPanel.setResourceName(element.getName());
            com.android.ide.common.res2.ResourceItem defaultValue = setupVariants();
            if (defaultValue != null) {
              editResourceItem(defaultValue);
              return;
            }
          }
        }

        if (element == null && myStateListPicker != null && myStateListPicker.getStateList() != null && myStateListPicker.getStateList().getType() == myType) {
          assert myStateListPickerPanel != null;
          layout.show(myPreviewPanel, EDITOR);
          myEditorPanel.setSelectedTab(myStateListPickerPanel);
          return;
        }

        if (element == null && myType == ResourceType.COLOR) {
          assert myColorPickerPanel != null;
          layout.show(myPreviewPanel, EDITOR);
          myEditorPanel.setSelectedTab(myColorPickerPanel);
          return;
        }
      }

      if (element == null || element.getGroup().getType() == ResourceType.ID) {
        layout.show(myPreviewPanel, NONE);
        return;
      }

      String doc = AndroidJavaDocRenderer.render(myModule, element.getGroup().getType(), element.getName(), element.isFramework());
      if (doc != null) {
        myHtmlTextArea.setText(doc);
        layout.show(myPreviewPanel, TEXT);
      }
      else {
        VirtualFile file = element.getFile();
        if (file != null && file.getFileType() == XmlFileType.INSTANCE) {
          String value;
          try {
            value = new String(file.contentsToByteArray());
          }
          catch (IOException ex) {
            LOG.warn("cant read file " + file, ex);
            value = ex.toString();
          }
          myComboBox.setVisible(false);
          myComboTextArea.setText(value);
          layout.show(myPreviewPanel, COMBO);
        }
        else {
          layout.show(myPreviewPanel, NONE);
        }
      }
    }

    public void editResourceItem(@NotNull com.android.ide.common.res2.ResourceItem selected) {
      ResourceValue resourceValue = selected.getResourceValue(false);
      assert resourceValue != null;

      @NotNull ResourceEditorTab resourceEditorTab;
      String value = resourceValue.getValue();
      if (value != null && (value.startsWith(SdkConstants.PREFIX_RESOURCE_REF) || value.startsWith(SdkConstants.PREFIX_THEME_REF))) {
        myReferenceComponent.setValueText(value);
        resourceEditorTab = myReferencePanel;
      }
      else {
        ResourceHelper.StateList stateList = ResourceHelper.resolveStateList(getResourceResolver(), resourceValue, myModule.getProject());
        if (stateList != null) { // if this is not a statelist, it may be just a normal color
          assert myStateListPickerPanel != null;
          assert myStateListPicker != null;

          if (stateList.getType() != myStateListPickerPanel.getLocationSettings().getType()) {
            LOG.warn("StateList type mismatch " + stateList.getType() + " " + myStateListPickerPanel.getLocationSettings().getType());
            showPreview(getSelectedElement(), false);
            return;
          }
          myStateListPicker.setStateList(stateList);
          if (myStateListPickerPanel.getFullPanel().getParent() == null) {
            myEditorPanel.addTab(myStateListPickerPanel);
          }
          resourceEditorTab = myStateListPickerPanel;
        }
        else {
          Color color = ResourceHelper.parseColor(resourceValue.getValue());
          if (color != null) { // if invalid color because of user error or a reference to another color
            assert myColorPickerPanel != null;
            assert myColorPicker != null;

            myColorPicker.setColor(color);
            resourceEditorTab = myColorPickerPanel;
          }
          else {
            // we are an actual image, so we need to just display it.
            showPreview(getSelectedElement(), false);
            return;
          }
        }
      }

      myEditorPanel.setSelectedTab(resourceEditorTab);

      setLocationFromResourceItem(selected);
    }

    @Nullable("if there is no error")
    public ValidationInfo doValidate() {
      final boolean okActionEnabled;
      ValidationInfo error = null;

      ResourceEditorTab editor = getCurrentResourceEditor();
      if (editor != null) {
        String overwriteResource = "";

        // if name is hidden, then we allow any value
        if (editor.getResourceNameVisibility() == ResourceNameVisibility.FORCE ||
            (editor.getResourceNameVisibility() == ResourceNameVisibility.SHOW && !myEditorPanel.getResourceName().isEmpty())) {
          ResourceNameValidator validator = editor.getValidator();
          String enteredName = myEditorPanel.getResourceName();
          if (validator.doesResourceExist(enteredName)) {
            overwriteResource = String.format("Saving this color will override existing resource %1$s.", enteredName);
          }
          else {
            String errorText = validator.getErrorText(enteredName);
            if (errorText != null) {
              error = new ValidationInfo(errorText, myEditorPanel.getResourceNameField());
            }
          }

          // the name of the resource must have changed, lets re-load the variants.
          if (!overwriteResource.equals(myEditorPanel.getResourceNameMessage())) {
            com.android.ide.common.res2.ResourceItem defaultResourceItem = setupVariants();
            if (defaultResourceItem != null) {
              setLocationFromResourceItem(defaultResourceItem);
            }
          }
        }

        if (!overwriteResource.equals(myEditorPanel.getResourceNameMessage())) {
          myEditorPanel.setResourceNameMessage(overwriteResource);
        }

        if (error == null) {
          error = editor.doValidate();
        }

        okActionEnabled = error == null;
      }
      else {
        okActionEnabled = getSelectedElement() != null;
      }

      // Need to always manually update the setOKActionEnabled as the DialogWrapper
      // only updates it if we go from having a error string to not having one
      // or the other way round, but not if the error string state has not changed.
      setOKActionEnabled(okActionEnabled);

      return error;
    }

    private void setLocationFromResourceItem(@NotNull com.android.ide.common.res2.ResourceItem item) {
      VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(item.getFile());
      assert file != null;
      // TODO as we only show variants that are specific to the folderType, and we have different folderTypes for different Editor tabs, reset does not always work.
      // TODO CreateXmlResourcePanel should show all variants irrespective of folderType and we should have just 1 CreateXmlResourcePanel per EditResourcePanel.
      for (ResourceEditorTab editor : myEditorPanel.getAllTabs()) {
        editor.getLocationSettings().resetFromFile(file, myModule.getProject());
      }
    }

    /**
     * @return the default value for this config, otherwise the first value.
     */
    @Nullable("no resources exist for this name")
    private com.android.ide.common.res2.ResourceItem setupVariants() {
      AndroidFacet facet = AndroidFacet.getInstance(myModule);
      assert facet != null;
      List<com.android.ide.common.res2.ResourceItem> resources = facet.getAppResources(true).getResourceItem(myType, myEditorPanel.getResourceName());
      assert resources != null;
      com.android.ide.common.res2.ResourceItem defaultValue = ThemeEditorUtils.getConfigurationForModule(myModule).getFullConfig().findMatchingConfigurable(resources);
      if (defaultValue == null && !resources.isEmpty()) {
        // we may not have ANY value that works in current config, then just pick the first one
        defaultValue = resources.get(0);
      }
      myEditorPanel.setVariant(resources, defaultValue);
      return defaultValue;
    }

    @Nullable("if not editing any resource")
    public ResourceEditorTab getCurrentResourceEditor() {
      return myEditorPanel.isVisible() ? myEditorPanel.getSelectedTab() : null;
    }

    boolean supportsGridMode() {
      return myType == ResourceType.COLOR || myType == ResourceType.DRAWABLE || myType == ResourceType.MIPMAP;
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
            setIcon(ChooseResourceDialog.this.getIcon(rItem, JBUI.scale(80)));
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
            setIcon(ChooseResourceDialog.this.getIcon(rItem, JBUI.scale(28)));
            return component;
          }
        };
        myList.setFixedCellWidth(10); // we use ANY fixed value here, as the width will stretch anyway, but we don't want the list to have to calculate it.
        myList.setFixedCellHeight(JBUI.scale(32));
        myList.setCellRenderer(listRenderer);
        myList.setLayoutOrientation(JList.VERTICAL);
      }
    }

    void showNewResource(@NotNull ResourceEditorTab tab) {
      myList.setSelectedElement(null);
      CardLayout layout = (CardLayout)myPreviewPanel.getLayout();
      layout.show(myPreviewPanel, EDITOR);
      myEditorPanel.setSelectedTab(tab);
      myEditorPanel.setResourceName("");
      for (ResourceEditorTab editor : myEditorPanel.getAllTabs()) {
        editor.getLocationSettings().resetToDefault();
      }
    }

    /**
     * @param value can also be an instance of {@link ItemResourceValue} for ?attr/ values
     */
    private void select(@NotNull ResourceValue value) {
      boolean isAttr = value instanceof ItemResourceValue;
      for (ResourceGroup group : myGroups) {
        for (ResourceItem item : group.getItems()) {
          if (isAttr) {
            if (item.isAttr() && ((ItemResourceValue)value).isFrameworkAttr() == item.isFramework() && value.getName().equals(item.getName())) {
              myList.setSelectedElement(item);
              return;
            }
          }
          else {
            if (!item.isAttr() && value.isFramework() == item.isFramework() && value.getName().equals(item.getName())) {
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

    @Nullable("if nothing is selected")
    public String getValueForLivePreview() {
      if (myColorPicker != null && myColorPicker.isShowing()) {
        return ResourceHelper.colorToString(myColorPicker.getColor());
      }
      ResourceItem element = getSelectedElement();
      return element != null ? element.getResourceUrl() : null;
    }

    // TODO this method can possibly be removed
    private void showComboPreview(@NotNull ResourceItem item) {
      // this assumes that item has more then 1 version
      long time = System.currentTimeMillis();
      AndroidFacet facet = AndroidFacet.getInstance(myModule);
      assert facet != null;
      ResourceManager manager = facet.getResourceManager(item.getNamespace());
      assert manager != null;
      List<ResourceElement> resources = manager.findValueResources(item.getGroup().getType().getName(), item.getName());
      if (ApplicationManagerEx.getApplicationEx().isInternal()) {
        System.out.println("Time: " + (System.currentTimeMillis() - time)); // XXX
      }

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
        if (model.getIndexOf(name) >= 0) {
          // DefaultComboBoxModel uses a object (not a index) to keep the selected item, so each item needs to be unique
          Module module = resource.getModule();
          if (module != null) {
            name = name + " (" + module.getName() + ")";
          }
          if (model.getIndexOf(name) >= 0) {
            name = name + " (" + i + ")";
          }
        }
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
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasSomethingToCommit() {
      return false;
    }

    @Override
    public void commit() {
    }
  }
}
