/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.assistant;

import static com.android.SdkConstants.ATTR_ITEM_COUNT;
import static com.android.SdkConstants.ATTR_LAYOUT_MANAGER;
import static com.android.SdkConstants.ATTR_LISTITEM;
import static com.android.SdkConstants.ATTR_SPAN_COUNT;
import static com.android.SdkConstants.FD_RES_LAYOUT;
import static com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.TOOLS_URI;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.resources.FileResourceNameValidator;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.adtui.HorizontalSpinner;
import com.android.tools.idea.common.command.NlWriteCommandActionUtil;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.android.tools.idea.uibuilder.assistant.AssistantPopupPanel;
import com.android.tools.idea.uibuilder.assistant.ComponentAssistantFactory.Context;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.components.JBList;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.jetbrains.android.facet.AndroidFacet;
import com.android.tools.idea.res.IdeResourcesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RecyclerViewAssistant extends AssistantPopupPanel {
  private static final Logger LOG = Logger.getInstance(RecyclerViewAssistant.class);
  private static final int ITEM_COUNT_DEFAULT = 10;

  private static final ImmutableList<Template> TEMPLATES = ImmutableList.of(
    Template.NONE_TEMPLATE,
    Template.fromStream("E-mail Client",
                        RecyclerViewAssistant.class.getResourceAsStream("templates/email.xml"),
                        EnumSet.of(TemplateTag.SUPPORT_LIBRARY, TemplateTag.CONSTRAINT_LAYOUT)),
    Template.fromStream("E-mail Client",
                        RecyclerViewAssistant.class.getResourceAsStream("templates/email-androidx.xml"),
                        EnumSet.of(TemplateTag.ANDROIDX, TemplateTag.CONSTRAINT_LAYOUT)),
    Template.fromStream("One Line",
                        RecyclerViewAssistant.class.getResourceAsStream("templates/one_line.xml")),
    Template.fromStream("One Line w/ Avatar",
                        RecyclerViewAssistant.class.getResourceAsStream("templates/one_line_avatar.xml")),
    Template.fromStream("Two Lines",
                        RecyclerViewAssistant.class.getResourceAsStream("templates/two_lines.xml")),
    Template.fromStream("Two Lines w/ Avatar",
                        RecyclerViewAssistant.class.getResourceAsStream("templates/two_lines_avatar.xml")),
    Template.fromStream("Three Lines",
                        RecyclerViewAssistant.class.getResourceAsStream("templates/three_lines.xml")),
    Template.fromStream("Three Lines w/ Avatar",
                        RecyclerViewAssistant.class.getResourceAsStream("templates/three_lines_avatar.xml")),
    Template.fromStream("Grid",
                        RecyclerViewAssistant.class.getResourceAsStream("templates/avatar.xml"), EnumSet.of(TemplateTag.GRID)));

  private static final int LONGEST_TEMPLATE = TEMPLATES.stream()
                                                       .map(template -> template.component2().length())
                                                       .max(Integer::compare)
                                                       .orElse(0);

  private final NlComponent myComponent;
  private final String myOriginalListItemValue;
  private final String myOriginalLayoutManager;
  private final String myOriginalSpanCountValue;
  private final Project myProject;
  private final String myResourceName;
  private final HorizontalSpinner<Template> mySpinner;
  private final JBIntSpinner myItemCount;
  @Nullable private PsiFile myCreatedFile;


  /**
   * Returns, from the array of available templates, the index of the template that is currently being
   * used or -1 if none of the templates is being used.
   * This method helps finding if the layout pointed by reference, is pointing to an existing template.
   */
  private static int getIndexOfMatchingTemplate(@NotNull Template[] availableTemplates, @NotNull AndroidFacet facet,
                                         @NotNull ResourceReference reference) {
    List<ResourceItem> items = StudioResourceRepositoryManager.getAppResources(facet).getResources(reference);
    if (items.isEmpty()) {
      return -1;
    }

    ResourceItem item = items.get(0);
    File layoutFile = item.getSource() != null ? item.getSource().toFile() : null;
    if (layoutFile == null || layoutFile.length() > LONGEST_TEMPLATE) {
      // If the item does not point to a file (all layouts do), return -1.
      // We also have a shortcut to avoid loading long files unnecessarily
      return -1;
    }

    try {
      String strValue = Files.toString(layoutFile, Charsets.UTF_8);

      for (int i = 0; i < availableTemplates.length; i++) {
        if (availableTemplates[i].hasSameContent(strValue)) {
          return i;
        }
      }
    }
    catch (IOException ignore) {
    }

    return -1;
  }

  public RecyclerViewAssistant(@NotNull Context context) {
    super();

    myComponent = context.getComponent();
    AndroidFacet facet = myComponent.getModel().getFacet();
    myProject = facet.getModule().getProject();

    Template[] availableTemplates = TEMPLATES.stream()
                                    .filter(template -> template.availableFor(myComponent.getModel().getModule()))
                                    .toArray(Template[]::new);
    mySpinner = HorizontalSpinner.forModel(JBList.createDefaultListModel(availableTemplates));

    String itemCountAttribute = myComponent.getAttribute(TOOLS_URI, ATTR_ITEM_COUNT);
    int count = parseItemCountAttribute(itemCountAttribute);

    myItemCount = new JBIntSpinner(count, 0, 50);
    myItemCount.setOpaque(false);
    ((JSpinner.NumberEditor)myItemCount.getEditor()).getTextField().setEditable(false);
    ((JSpinner.NumberEditor)myItemCount.getEditor()).getTextField().setHorizontalAlignment(SwingConstants.LEADING);

    JPanel content = new JPanel(new VerticalFlowLayout());
    content.setOpaque(false);

    content.add(AssistantUiKt.assistantLabel("Item template"));
    content.add(mySpinner);
    content.add(AssistantUiKt.assistantLabel("Item count"));
    content.add(myItemCount);

    String resourceName = null;
    String originalLayoutManager = myComponent.getAttribute(TOOLS_URI, ATTR_LAYOUT_MANAGER);
    String originalListItem = myComponent.getAttribute(TOOLS_URI, ATTR_LISTITEM);

    if (originalListItem != null) {
      // If the RecyclerView listitem is already pointing to a layout, we verify if that layout is one of the templates
      // we use. If it is, we pre-select it on the drop-down.
      ResourceUrl url = ResourceUrl.parse(originalListItem);
      ResourceReference reference = url != null ? url.resolve(ResourceNamespace.TODO(), ResourceNamespace.Resolver.EMPTY_RESOLVER) : null;
      if (reference != null) {
        int originalTemplateIndex = getIndexOfMatchingTemplate(availableTemplates, facet, reference);
        if (originalTemplateIndex >= 0) {
          // This means that the current tools:listitem is one of our pre-defined templates
          // pre-select that version in the templates combobox
          mySpinner.setSelectedIndex(originalTemplateIndex);
          resourceName = reference.getName();
          // For the case where we are handling a template, we consider the "Default" option to remove
          // both attributes
          originalLayoutManager = null;
          originalListItem = null;
        }
      }
    }
    myResourceName = resourceName == null ? getTemplateName(facet, "recycler_view_item") : resourceName;
    myOriginalListItemValue = originalListItem;
    myOriginalLayoutManager = originalLayoutManager;
    myOriginalSpanCountValue = myComponent.getAttribute(TOOLS_URI, ATTR_SPAN_COUNT);

    if (myOriginalSpanCountValue != null) {
      // If there is a spanCount value, set the value in the spinner
      try {
        myItemCount.setNumber(Integer.parseUnsignedInt(myOriginalSpanCountValue));
      }
      catch (NumberFormatException ignore) {
        // Ignore incorrectly formatted numbers
      }
    }

    addContent(content);

    ApplicationManager.getApplication().invokeLater(this::fireSelectionUpdated);

    // All the content is now setup so we can add the listeners
    mySpinner.addListSelectionListener(event -> {
      if (event.getValueIsAdjusting()) {
        return;
      }

      fireSelectionUpdated();
    });
    myItemCount.addChangeListener(new ChangeListener() {

      @Override
      public void stateChanged(ChangeEvent e) {
        setItemCount(myComponent, myItemCount.getNumber());
      }
    });
  }

  private static int parseItemCountAttribute(@Nullable String attribute) {
    if (attribute != null) {
      try {
        return Integer.parseInt(attribute);
      }
      catch (NumberFormatException ignore) {
      }
    }
    return ITEM_COUNT_DEFAULT;
  }

  private void fireSelectionUpdated() {
    Template template = mySpinner.getModel().getElementAt(mySpinner.getSelectedIndex());
    if (template == Template.NONE_TEMPLATE) {
      setOriginalState();
    }
    else {
      myCreatedFile = setTemplate(myProject, myComponent, myResourceName, template);
    }
  }


  @NotNull
  private static String getTemplateName(@NotNull AndroidFacet facet, @NotNull String templateRootName) {
    LocalResourceRepository LocalResourceRepository = StudioResourceRepositoryManager.getAppResources(facet);
    String resourceNameRoot = FileResourceNameValidator.getValidResourceFileName(templateRootName);

    String resourceName;
    int index = 0;
    do {
      resourceName = resourceNameRoot + (index < 1 ? "" : "_" + index);
      index++;
    }
    while (!LocalResourceRepository.getResources(ResourceNamespace.TODO(), ResourceType.LAYOUT, resourceName).isEmpty());
    return resourceName;
  }

  @Nullable
  private static PsiFile setTemplate(@NotNull Project project,
                                     @NotNull NlComponent component,
                                     @NotNull String resourceName,
                                     @NotNull Template template) {
    String content = template.getMyTemplate();
    // The layout file must live in a subdirectory of the resource directory.
    VirtualFile resourceDir = component.getModel().getVirtualFile().getParent().getParent();
    assert resourceDir != null;

    return WriteCommandAction.runWriteCommandAction(project, (Computable<PsiFile>)() -> {
      List<VirtualFile> files = IdeResourcesUtil.findOrCreateStateListFiles(
        project, resourceDir, ResourceFolderType.LAYOUT, ResourceType.LAYOUT, resourceName, Collections.singletonList(FD_RES_LAYOUT));
      if (files == null || files.isEmpty()) {
        return null;
      }

      VirtualFile file = files.get(0);
      CommandProcessor.getInstance().addAffectedFiles(project, file);
      try {
        try (OutputStream stream = file.getOutputStream(null)) {
          stream.write(content.getBytes(Charsets.UTF_8));
        }
      }
      catch (IOException e) {
        LOG.debug(e);
      }
      component.setAttribute(TOOLS_URI, ATTR_LISTITEM, LAYOUT_RESOURCE_PREFIX + resourceName);
      component.setAttribute(TOOLS_URI, ATTR_SPAN_COUNT, template.hasTag(TemplateTag.GRID) ? "5" : null);
      component.setAttribute(TOOLS_URI, "layoutManager", template.hasTag(TemplateTag.GRID) ? "GridLayoutManager" : null);
      VirtualFile virtualFile = component.getBackend().getAffectedFile();
      if (virtualFile != null) {
        CommandProcessor.getInstance().addAffectedFiles(project, virtualFile);
      }

      PsiManager manager = PsiManager.getInstance(project);
      PsiFile psiFile = manager.findFile(file);
      if (psiFile != null) {
        manager.reloadFromDisk(psiFile);
      }
      return psiFile;
    });
  }

  /**
   * Set the design-time itemCount attribute in the given component
   */
  private static void setItemCount(@NotNull NlComponent component, int newCount) {
    NlWriteCommandActionUtil.run(component, "Set itemCount", () -> {
      String itemCountNewValue = ITEM_COUNT_DEFAULT == newCount ? null : Integer.toString(newCount);
      component.setAttribute(TOOLS_URI, ATTR_ITEM_COUNT, itemCountNewValue);
    });
  }

  private void setOriginalState() {
    if (myCreatedFile == null) {
      // Nothing to restore
      return;
    }

    AndroidFacet facet = myComponent.getModel().getFacet();
    Project project = facet.getModule().getProject();
    // onClosed is invoked when the dialog is closed so we run the clean-up it later when the dialog has effectively closed
    ApplicationManager.getApplication().invokeLater(() -> WriteCommandAction.runWriteCommandAction(project, () -> {
      myCreatedFile.delete();
      myCreatedFile = null;
      myComponent.setAttribute(TOOLS_URI, ATTR_LISTITEM, myOriginalListItemValue);
      myComponent.setAttribute(TOOLS_URI, "spanCount", myOriginalSpanCountValue);
      myComponent.setAttribute(TOOLS_URI, ATTR_LAYOUT_MANAGER, myOriginalLayoutManager);
      CommandProcessor.getInstance().addAffectedFiles(project, myComponent.getTagDeprecated().getContainingFile().getVirtualFile());
    }));
  }

  @NotNull
  public static JComponent createComponent(@NotNull Context context) {
    return new RecyclerViewAssistant(context);
  }
}
