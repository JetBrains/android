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

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.adtui.HorizontalSpinner;
import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.uibuilder.property.assistant.AssistantPopupPanel;
import com.android.tools.idea.uibuilder.property.assistant.ComponentAssistantFactory.Context;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
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
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.*;

public class RecyclerViewAssistant extends AssistantPopupPanel {
  private static final int ITEM_COUNT_DEFAULT = 10;
  private static Logger LOG = Logger.getInstance(RecyclerViewAssistant.class);

  private static final ImmutableList<Template> TEMPLATES = ImmutableList.of(
    Template.NONE_TEMPLATE,
    Template.fromStream("E-mail Client",
                        RecyclerViewAssistant.class.getResourceAsStream("templates/email.xml")),
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
                        RecyclerViewAssistant.class.getResourceAsStream("templates/avatar.xml"), true));

  private final NlComponent myComponent;
  private final String myOriginalListItemValue;
  private final String myOriginalLayoutManager;
  private final String myOriginalSpanCountValue;
  private final Project myProject;
  private final String myResourceName;
  private final HorizontalSpinner<Template> mySpinner;
  private final JBIntSpinner myItemCount;
  @Nullable private PsiFile myCreatedFile;


  public RecyclerViewAssistant(@NotNull Context context) {
    super();

    myComponent = context.getComponent();
    AndroidFacet facet = myComponent.getModel().getFacet();
    VirtualFile resourceDir = ResourceFolderManager.getInstance(facet).getPrimaryFolder();
    assert resourceDir != null;
    myProject = facet.getModule().getProject();
    myResourceName = getTemplateName(facet, "recycler_view_item");

    mySpinner = HorizontalSpinner.forModel(
      JBList.createDefaultListModel(TEMPLATES.toArray(new Template[0])));

    mySpinner.addListSelectionListener(event -> {
      if (event.getValueIsAdjusting()) {
        return;
      }

      fireSelectionUpdated();
    });

    String itemCountAttribute = myComponent.getAttribute(TOOLS_URI, ATTR_ITEM_COUNT);
    int count = parseItemCountAttribute(itemCountAttribute);

    myItemCount = new JBIntSpinner(count, 0, 50);
    myItemCount.setOpaque(false);
    myItemCount.addChangeListener(new ChangeListener() {

      @Override
      public void stateChanged(ChangeEvent e) {
        setItemCount(myComponent, myItemCount.getNumber());
      }
    });
    ((JSpinner.NumberEditor)myItemCount.getEditor()).getTextField().setEditable(false);
    ((JSpinner.NumberEditor)myItemCount.getEditor()).getTextField().setHorizontalAlignment(SwingConstants.LEADING);

    JPanel content = new JPanel(new VerticalFlowLayout());
    content.setOpaque(false);

    content.add(AssistantUiKt.assistantLabel("Item template"));
    content.add(mySpinner);
    content.add(AssistantUiKt.assistantLabel("Item count"));
    content.add(myItemCount);

    myOriginalListItemValue = myComponent.getAttribute(TOOLS_URI, ATTR_LISTITEM);
    myOriginalSpanCountValue = myComponent.getAttribute(TOOLS_URI, ATTR_SPAN_COUNT);
    myOriginalLayoutManager = myComponent.getAttribute(TOOLS_URI, ATTR_LAYOUT_MANAGER);

    addContent(content);

    ApplicationManager.getApplication().invokeLater(this::fireSelectionUpdated);
  }

  private static int parseItemCountAttribute(@Nullable String attribute) {
    if (attribute != null) {
      try {
        return Integer.parseInt(attribute);
      } catch(NumberFormatException ignore) {
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
    LocalResourceRepository LocalResourceRepository = ResourceRepositoryManager.getAppResources(facet);
    String resourceNameRoot = AndroidResourceUtil.getValidResourceFileName(templateRootName);

    String resourceName;
    int index = 0;
    do {
      resourceName = resourceNameRoot + (index < 1 ? "" : "_" + index);
      index++;
    } while (!LocalResourceRepository.getResourceItems(ResourceNamespace.TODO, ResourceType.LAYOUT, resourceName).isEmpty());
    return resourceName;
  }

  @Nullable
  private static PsiFile setTemplate(@NotNull Project project,
                                     @NotNull NlComponent component,
                                     @NotNull String resourceName,
                                     @NotNull Template template) {
    String content = template.getMyTemplate();
    AndroidFacet facet = component.getModel().getFacet();
    VirtualFile resourceDir = ResourceFolderManager.getInstance(facet).getPrimaryFolder();
    assert resourceDir != null;

    return WriteCommandAction.runWriteCommandAction(project, (Computable<PsiFile>)() -> {
      List<VirtualFile> files = AndroidResourceUtil.findOrCreateStateListFiles(
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
      component.setAttribute(TOOLS_URI, ATTR_SPAN_COUNT, template.isGrid() ? "5" : null);
      component.setAttribute(TOOLS_URI, "layoutManager", template.isGrid() ? "GridLayoutManager" : null);
      CommandProcessor.getInstance().addAffectedFiles(project, component.getTag().getContainingFile().getVirtualFile());

      return PsiManager.getInstance(project).findFile(file);
    });
  }

  /**
   * Set the design-time itemCount attribute in the given component
   */
  private static void setItemCount(@NotNull NlComponent component, int newCount) {
    NlWriteCommandAction.run(component, "Set itemCount", () -> {
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
      CommandProcessor.getInstance().addAffectedFiles(project, myComponent.getTag().getContainingFile().getVirtualFile());
    }));
  }

  @NotNull
  public static JComponent createComponent(@NotNull Context context) {
    return new RecyclerViewAssistant(context);
  }
}
