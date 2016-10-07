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
package com.android.tools.idea.npw;

import com.android.tools.idea.actions.NewAndroidComponentAction;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.ui.ASGallery;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithDescription;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;

import static com.android.tools.idea.wizard.WizardConstants.DEFAULT_GALLERY_THUMBNAIL_SIZE;
import static com.android.tools.idea.wizard.WizardConstants.IS_LIBRARY_KEY;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;

/**
 * Gallery of Android activity templates.
 */
public class ActivityGalleryStep extends DynamicWizardStepWithDescription {
  private static final Key<TemplateEntry[]> KEY_TEMPLATES =
    ScopedStateStore.createKey("template.list", ScopedStateStore.Scope.STEP, TemplateEntry[].class);

  @NotNull
  private final FormFactor myFormFactor;
  private final Key<TemplateEntry> myCurrentSelectionKey;
  private final boolean myShowSkipEntry;
  private final Module myModule;
  private boolean myAppThemeExists;
  private ASGallery<Optional<TemplateEntry>> myGallery;

  public ActivityGalleryStep(@NotNull FormFactor formFactor, boolean showSkipEntry,
                             @NotNull Key<TemplateEntry> currentSelectionKey, @Nullable Module module, @NotNull Disposable disposable) {
    super(disposable);
    myFormFactor = formFactor;
    myCurrentSelectionKey = currentSelectionKey;
    myShowSkipEntry = showSkipEntry;
    myModule = module;
    setBodyComponent(createGallery());
  }

  private JComponent createGallery() {
    myGallery = new ASGallery<>();
    Dimension thumbnailSize = DEFAULT_GALLERY_THUMBNAIL_SIZE;
    myGallery.setThumbnailSize(thumbnailSize);
    myGallery.setMinimumSize(new Dimension(thumbnailSize.width * 2 + 1, thumbnailSize.height));
    myGallery.setLabelProvider(new Function<Optional<TemplateEntry>, String>() {
      @Override
      public String apply(Optional<TemplateEntry> template) {
        if (template.isPresent()) {
          return template.get().getTitle();
        }
        else {
          return "Add No Activity";
        }
      }
    });
    myGallery.setDefaultAction(new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        DynamicWizard wizard = getWizard();
        assert wizard != null;
        wizard.doNextAction();
      }
    });
    myGallery.setImageProvider(new Function<Optional<TemplateEntry>, Image>() {
      @Override
      public Image apply(Optional<TemplateEntry> input) {
        return input.isPresent() ? input.get().getImage() : null;
      }
    });
    myGallery.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        saveState(myGallery);
      }
    });
    myGallery.setName("Templates Gallery");
    AccessibleContext accessibleContext = myGallery.getAccessibleContext();
    if (accessibleContext != null) {
      accessibleContext.setAccessibleDescription(getStepTitle());
    }
    JPanel panel = new JPanel(new JBCardLayout());
    panel.add("only card", new JBScrollPane(myGallery));
    return panel;
  }

  @Override
  public void onEnterStep() {
    super.onEnterStep();
    //// First time page is shown, controls are narrow hence gallery assumes single column mode.
    //// We need to scroll up.
    UiNotifyConnector.doWhenFirstShown(myGallery, new Runnable() {
      @Override
      public void run() {
        myGallery.scrollRectToVisible(new Rectangle(0, 0, 1, 1));
      }
    });

    myAppThemeExists = myState.getNotNull(WizardConstants.IS_NEW_PROJECT_KEY, false);
    if (myModule != null) {
      ThemeHelper themeHelper = new ThemeHelper(myModule);
      myAppThemeExists = themeHelper.getAppThemeName() != null;
    }
  }

  @Override
  public boolean isStepVisible() {
    return !myState.getNotNull(IS_LIBRARY_KEY, false) && super.isStepVisible();
  }

  @Override
  public boolean validate() {
    TemplateEntry template = myState.get(myCurrentSelectionKey);
    final PageStatus status;
    if (template == null) {
      status = myShowSkipEntry ? PageStatus.OK : PageStatus.NOTHING_SELECTED;
    }
    else if (isIncompatibleMinSdk(template)) {
      status = PageStatus.INCOMPATIBLE_MAIN_SDK;
    }
    else if (isIncompatibleBuildApi(template)) {
      status = PageStatus.INCOMPATIBLE_BUILD_API;
    }
    else if (isMissingAppTheme(template)) {
      status = PageStatus.MISSING_THEME;
    }
    else {
      status = PageStatus.OK;
    }
    setErrorHtml(status.formatMessage(template));
    return status.isPageValid();
  }

  private boolean isIncompatibleBuildApi(@NotNull TemplateEntry template) {
    Integer buildSdkLevel = myState.get(FormFactorUtils.getBuildApiLevelKey(myFormFactor));
    return buildSdkLevel != null && buildSdkLevel < template.getMinBuildApi();
  }

  private boolean isIncompatibleMinSdk(@NotNull TemplateEntry template) {
    Integer minSdkLevel = myState.get(FormFactorUtils.getMinApiLevelKey(myFormFactor));
    return minSdkLevel != null && minSdkLevel < template.getMinSdk();
  }

  private boolean isMissingAppTheme(@NotNull TemplateEntry template) {
    return !myAppThemeExists && template.getMetadata().isAppThemeRequired();
  }

  @Override
  public void init() {
    super.init();
    TemplateListProvider templateListProvider = new TemplateListProvider(myFormFactor, NewAndroidComponentAction.NEW_WIZARD_CATEGORIES,
                                                                         TemplateManager.EXCLUDED_TEMPLATES);
    TemplateEntry[] list = templateListProvider.deriveValue(myState, AddAndroidActivityPath.KEY_IS_LAUNCHER, null);
    myGallery.setModel(JBList.createDefaultListModel((Object[])wrapInOptionals(list)));
    myState.put(KEY_TEMPLATES, list);

    if (list.length != 0) {
      int i = indexOfTemplateWithTitle(list, "Empty Activity");
      myState.put(myCurrentSelectionKey, list[i == -1 ? 0 : i]);
    }

    register(myCurrentSelectionKey, myGallery, new ComponentBinding<TemplateEntry, ASGallery<Optional<TemplateEntry>>>() {
      @Override
      public void setValue(TemplateEntry newValue, @NotNull ASGallery<Optional<TemplateEntry>> component) {
        component.setSelectedElement(Optional.fromNullable(newValue));
      }

      @Override
      @Nullable
      public TemplateEntry getValue(@NotNull ASGallery<Optional<TemplateEntry>> component) {
        Optional<TemplateEntry> selection = component.getSelectedElement();
        if (selection != null && selection.isPresent()) {
          return selection.get();
        }
        else {
          return null;
        }
      }
    });
    register(KEY_TEMPLATES, myGallery, new ComponentBinding<TemplateEntry[], ASGallery<Optional<TemplateEntry>>>() {
      @Override
      public void setValue(@Nullable TemplateEntry[] newValue, @NotNull ASGallery<Optional<TemplateEntry>> component) {
        component.setModel(JBList.createDefaultListModel((Object[])wrapInOptionals(newValue)));
      }
    });
    registerValueDeriver(KEY_TEMPLATES, templateListProvider);
  }

  private Optional[] wrapInOptionals(@Nullable TemplateEntry[] newValue) {
    if (newValue == null) {
      return new Optional[0];
    }
    final Optional[] model;
    int i;
    if (myShowSkipEntry) {
      model = new Optional[newValue.length + 1];
      model[0] = Optional.absent();
      i = 1;
    }
    else {
      model = new Optional[newValue.length];
      i = 0;
    }
    for (TemplateEntry entry : newValue) {
      model[i++] = Optional.of(entry);
    }
    return model;
  }

  private static int indexOfTemplateWithTitle(@NotNull TemplateEntry[] entries, @NotNull String title) {
    for (int i = 0; i < entries.length; i++) {
      if (title.equals(entries[i].getMetadata().getTitle())) {
        return i;
      }
    }

    return -1;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myGallery;
  }

  @NotNull
  @Override
  public String getStepName() {
    return "Activity Gallery";
  }

  @NotNull
  @Override
  protected String getStepTitle() {
    return "Add an Activity to " + myFormFactor.id;
  }

  @Nullable
  @Override
  protected String getStepDescription() {
    return null;
  }

  @Nullable
  @Override
  protected Icon getStepIcon() {
    return myFormFactor.getIcon();
  }

  private enum PageStatus {
    OK, INCOMPATIBLE_BUILD_API, INCOMPATIBLE_MAIN_SDK, MISSING_THEME, NOTHING_SELECTED;

    public boolean isPageValid() {
      return this == OK;
    }

    @Nullable
    public String formatMessage(TemplateEntry template) {
      switch (this) {
        case OK:
          return null;
        case INCOMPATIBLE_BUILD_API:
          return String.format("Selected activity template has a minimum build API level of %d.", template.getMinBuildApi());
        case INCOMPATIBLE_MAIN_SDK:
          return String.format("Selected activity template has a minimum SDK level of %d.", template.getMinSdk());
        case MISSING_THEME:
          return "Selected activity template requires an existing Application Theme";
        case NOTHING_SELECTED:
          return "No activity template was selected.";
        default:
          throw new IllegalArgumentException(name());
      }
    }
  }
}
