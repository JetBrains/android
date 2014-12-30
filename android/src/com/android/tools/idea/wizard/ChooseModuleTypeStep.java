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
package com.android.tools.idea.wizard;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.wizard.WizardConstants.SELECTED_MODULE_TYPE_KEY;

/**
 * This step allows the user to select which type of module they want to create.
 */
public class ChooseModuleTypeStep extends DynamicWizardStepWithHeaderAndDescription {
  private final Iterable<ModuleTemplateProvider> myModuleTypesProviders;
  private JPanel myPanel;
  private JBList myModuleTypeList;
  private ASGallery<ModuleTemplate> myFormFactorGallery;
  private JPanel myModulesPanel;
  private boolean myIsSynchronizingSelection = false;

  @NotNull
  @Override
  protected WizardStepHeaderSettings getStepHeader() {
    return NewModuleWizardDynamic.buildHeader();
  }

  public ChooseModuleTypeStep(Iterable<ModuleTemplateProvider> moduleTypesProviders, @Nullable Disposable parentDisposable) {
    super("Choose Module Type", "Select an option below to create your new module", parentDisposable);
    myModuleTypeList.setCellRenderer(new TemplateListCellRenderer());
    myModuleTypesProviders = moduleTypesProviders;
    myModuleTypeList.setBorder(BorderFactory.createLineBorder(UIUtil.getBorderColor()));
    myFormFactorGallery.setBorder(BorderFactory.createLineBorder(UIUtil.getBorderColor()));
    setBodyComponent(myPanel);
  }

  @Nullable
  @Contract("null->null")
  public static Image iconToImage(@Nullable Icon icon) {
    if (icon == null) {
      return null;
    }
    else {
      BufferedImage image = UIUtil.createImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_4BYTE_ABGR);

      Graphics2D graphics = image.createGraphics();
      graphics.setBackground(JBColor.background());
      graphics.setColor(JBColor.background());
      icon.paintIcon(null, graphics, 0, 0);
      return image;
    }
  }

  @Override
  public void init() {
    super.init();
    ImmutableList.Builder<ModuleTemplate> galleryTemplates = ImmutableList.builder();
    ImmutableList.Builder<ModuleTemplate> extrasTemplates = ImmutableList.builder();
    Set<FormFactorUtils.FormFactor> formFactorSet = Sets.newHashSet();
    for (ModuleTemplateProvider provider : myModuleTypesProviders) {
      for (ModuleTemplate moduleTemplate : provider.getModuleTemplates()) {
        if (moduleTemplate.isGalleryModuleType()) {
          galleryTemplates.add(moduleTemplate);
        }
        else {
          extrasTemplates.add(moduleTemplate);
        }
        FormFactorUtils.FormFactor formFactor = moduleTemplate.getFormFactor();
        if (formFactor != null) {
          formFactorSet.add(formFactor);
        }
      }
    }

    for (final FormFactorUtils.FormFactor formFactor : formFactorSet) {
      registerValueDeriver(FormFactorUtils.getInclusionKey(formFactor), new ValueDeriver<Boolean>() {
        @Nullable
        @Override
        public Boolean deriveValue(@NotNull ScopedStateStore state,
                                   @Nullable ScopedStateStore.Key changedKey,
                                   @Nullable Boolean currentValue) {
          ModuleTemplate moduleTemplate = myState.get(SELECTED_MODULE_TYPE_KEY);
          return moduleTemplate != null && Objects.equal(formFactor, moduleTemplate.getFormFactor());
        }
      });
    }

    List<ModuleTemplate> galleryTemplatesList = galleryTemplates.build();
    List<ModuleTemplate> extrasTemplatesList = extrasTemplates.build();

    myModuleTypeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myModuleTypeList.setModel(JBList.createDefaultListModel(ArrayUtil.toObjectArray(extrasTemplatesList)));
    myFormFactorGallery.setModel(JBList.createDefaultListModel(ArrayUtil.toObjectArray(galleryTemplatesList)));
    ModuleTypeBinding binding = new ModuleTypeBinding();
    register(SELECTED_MODULE_TYPE_KEY, myPanel, binding);

    myModuleTypeList.addListSelectionListener(new ModuleTypeSelectionListener(true));
    myFormFactorGallery.addListSelectionListener(new ModuleTypeSelectionListener(false));

    if (!galleryTemplatesList.isEmpty()) {
      myState.put(SELECTED_MODULE_TYPE_KEY, galleryTemplatesList.get(0));
    }
  }

  @Override
  public boolean commitStep() {
    ModuleTemplate selected = myState.get(SELECTED_MODULE_TYPE_KEY);
    if (selected != null) {
      selected.updateWizardStateOnSelection(myState);
    }
    return true;
  }

  @NotNull
  @Override
  public String getStepName() {
    return "Choose Module Type Step";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    ModuleTemplate moduleTemplate = myState.get(SELECTED_MODULE_TYPE_KEY);
    return moduleTemplate == null || moduleTemplate.isGalleryModuleType() ? myFormFactorGallery : myModuleTypeList;
  }

  private void createUIComponents() {
    myFormFactorGallery = new ASGallery<ModuleTemplate>(JBList.createDefaultListModel(), new Function<ModuleTemplate, Image>() {
      @Override
      public Image apply(ModuleTemplate input) {
        return iconToImage(input.getIcon());
      }
    }, Functions.toStringFunction(), new Dimension(128, 128));
    myFormFactorGallery.setCellMargin(new Insets(0, 25, 0, 25)); // Margins so the text is not clipped
  }

  private static class TemplateListCellRenderer implements ListCellRenderer {
    private final JPanel myPanel = new JPanel(new BorderLayout(32, 0));
    private final JLabel myDescriptionLabel = new JLabel();
    private JLabel myLabel = new JLabel();

    public TemplateListCellRenderer() {
      myLabel.setFont(UIUtil.getListFont());
      myDescriptionLabel.setFont(UIUtil.getToolTipFont());
      myDescriptionLabel.setVerticalAlignment(SwingConstants.TOP);
      myPanel.add(myLabel, BorderLayout.NORTH);
      myPanel.add(myDescriptionLabel, BorderLayout.CENTER);
    }

    private static int getInsetsWidth(Insets insets) {
      return insets.left + insets.right;
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      Color bg = UIUtil.getListBackground();
      Color fg = UIUtil.getListForeground();
      Color descriptionFg = UIUtil.getInactiveTextColor();

      Border border = BorderFactory.createCompoundBorder(UIUtil.getTableFocusCellHighlightBorder(),
                                                         IdeBorderFactory.createEmptyBorder(5));

      if (isSelected) {
        bg = UIUtil.getListSelectionBackground();
        fg = UIUtil.getListSelectionForeground();
        descriptionFg = fg;
      }
      if (!cellHasFocus) {
        border = IdeBorderFactory.createEmptyBorder(border.getBorderInsets(myLabel));
      }

      myPanel.setBorder(border);
      if (value instanceof ModuleTemplate) {
        String name = ((ModuleTemplate)value).getName();
        myLabel.setText(name);
        String description = ((ModuleTemplate)value).getDescription();
        if (!StringUtil.isEmpty(description) && !BasicHTML.isHTMLString(description)) {
          description = "<html>" + description + "</html>";
        }
        if (!StringUtil.isEmpty(description)) {
          View htmlView = BasicHTML.createHTMLView(myDescriptionLabel, description);
          int width = 630 - (getInsetsWidth(border.getBorderInsets(myPanel)) + getInsetsWidth(list.getInsets()));
          htmlView.setSize(width, 500);
          int preferredSpan = (int)htmlView.getPreferredSpan(View.Y_AXIS);
          myDescriptionLabel.setPreferredSize(new Dimension(width, preferredSpan));
        }
        myDescriptionLabel.setText(description);
      }
      myLabel.setForeground(fg);
      myDescriptionLabel.setForeground(descriptionFg);
      myPanel.setBackground(bg);
      return myPanel;
    }
  }

  private class ModuleTypeBinding extends ComponentBinding<ModuleTemplate, JPanel> {
    @Override
    public void setValue(@Nullable ModuleTemplate newValue, @NotNull JPanel component) {
      if (newValue == null) {
        myModuleTypeList.clearSelection();
        myFormFactorGallery.setSelectedElement(null);
      }
      else if (!newValue.isGalleryModuleType()) {
        myModuleTypeList.setSelectedValue(newValue, true);
        myFormFactorGallery.setSelectedElement(null);
      }
      else {
        myModuleTypeList.clearSelection();
        myFormFactorGallery.setSelectedElement(newValue);
      }
    }

    @Nullable
    @Override
    public ModuleTemplate getValue(@NotNull JPanel component) {
      ModuleTemplate moduleTemplate = myFormFactorGallery.getSelectedElement();
      if (moduleTemplate == null) {
        moduleTemplate = (ModuleTemplate)myModuleTypeList.getSelectedValue();
      }
      return moduleTemplate;
    }
  }

  private class ModuleTypeSelectionListener implements ListSelectionListener {
    private final boolean myGallery;

    public ModuleTypeSelectionListener(boolean gallery) {
      myGallery = gallery;
    }


    @Override
    public void valueChanged(ListSelectionEvent e) {
      if (myIsSynchronizingSelection) {
        return;
      }
      myIsSynchronizingSelection = true;
      if (myGallery) {
        myFormFactorGallery.setSelectedElement(null);
      }
      else {
        myModuleTypeList.clearSelection();
      }
      myIsSynchronizingSelection = false;
      saveState(myPanel);
      invokeUpdate(SELECTED_MODULE_TYPE_KEY);
    }
  }
}
