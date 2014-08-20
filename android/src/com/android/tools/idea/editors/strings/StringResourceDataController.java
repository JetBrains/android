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
package com.android.tools.idea.editors.strings;

import com.android.SdkConstants;
import com.android.ide.common.res2.ResourceItem;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.Locale;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class StringResourceDataController {
  private final StringResourceEditor myEditor;
  private final AndroidFacet myAndroidFacet;
  private LocalResourceRepository myResourceRepository;

  private long myModificationCount;
  private StringResourceData myData;
  private String mySelectedKey;
  private Locale mySelectedLocale;

  private enum ParseTaskType {
    INIT_DATA("Loading string resource data"),
    UPDATE_DATA("Updating string resource data");

    public final String description;

    ParseTaskType(@NotNull String description) {
      this.description = description;
    }
  }

  private enum EditType {
    KEY, DEFAULT_VALUE, UNTRANSLATABLE, TRANSLATION
  }

  public StringResourceDataController(StringResourceEditor editor,
                                      AndroidFacet androidFacet) {
    myEditor = editor;
    myAndroidFacet = androidFacet;
    startParseTask(ParseTaskType.INIT_DATA);
  }

  @NotNull
  public StringResourceData getData() {
    return myData;
  }

  boolean dataIsCurrent() {
    return myResourceRepository != null && myModificationCount == myResourceRepository.getModificationCount();
  }

  void updateData() {
    startParseTask(ParseTaskType.UPDATE_DATA);
  }

  /**
   * Queues a task that gets string resource data from the local resource repository and reports back to the string resource editor
   * when it has successfully obtained data.
   * @param type Whether the data is being initialized for the first time or updated.  This determines which of
   *             StringResourceEditor.onDataInitialized() and StringResourceEditor.onDataUpdated() is called on task success.
   */
  private void startParseTask(@NotNull ParseTaskType type) {
    final Task.Modal parseTask = new ParseTask(type);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        parseTask.queue();
      }
    });
  }

  private class ParseTask extends Task.Modal {
    private ParseTaskType myType;

    public ParseTask(@NotNull ParseTaskType type) {
      super(myEditor.getProject(), type.description, false);
      myType = type;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      myResourceRepository = myAndroidFacet.getModuleResources(true);
      myModificationCount = myResourceRepository.getModificationCount();
      myData = StringResourceParser.parse(myResourceRepository);
    }

    @Override
    public void onSuccess() {
      switch (myType) {
        case INIT_DATA:
          myEditor.onDataInitialized();
          break;
        case UPDATE_DATA:
          myEditor.onDataUpdated();
      }
    }
  }

  public void selectData(@Nullable String key, @Nullable Locale locale) {
    mySelectedKey = key;
    mySelectedLocale = locale;
  }

  public void setKey(@NotNull String key) {
    doEdit(EditType.KEY, key);
  }

  public void setDefaultValue(@NotNull String defaultValue) {
    doEdit(EditType.DEFAULT_VALUE, defaultValue);
  }

  public void setUntranslatable(boolean untranslatable) {
    doEdit(EditType.UNTRANSLATABLE, untranslatable);
  }

  public void setTranslation(@NotNull String translation) {
    doEdit(EditType.TRANSLATION, translation);
  }

  private void doEdit(@NotNull EditType editType, @NotNull Object newData) {
    boolean dataChanged = false;
    if (mySelectedKey != null) {
      if (editType != EditType.UNTRANSLATABLE) {
        newData = String.valueOf(newData).trim();
      }
      switch (editType) {
        case KEY:
          String key = String.valueOf(newData);
          dataChanged = !mySelectedKey.equals(key) && setAttribute(SdkConstants.ATTR_NAME, key);
          break;
        case DEFAULT_VALUE:
          dataChanged = setText(true, String.valueOf(newData));
          break;
        case UNTRANSLATABLE:
          dataChanged =
            newData instanceof Boolean && setAttribute(SdkConstants.ATTR_TRANSLATABLE, (Boolean)newData ? SdkConstants.VALUE_FALSE : null);
          break;
        case TRANSLATION:
          dataChanged = mySelectedLocale != null && setText(false, String.valueOf(newData));
      }
    }
    if (dataChanged) {
      updateData();
    }
  }

  private boolean setAttribute(@NotNull String attributeName, @Nullable String attributeValue) {
    List<ResourceItem> items = Lists.newArrayList();
    if (myData.getDefaultValues().containsKey(mySelectedKey)) {
      items.add(myData.getDefaultValues().get(mySelectedKey));
    }
    items.addAll(myData.getTranslations().row(mySelectedKey).values());
    return StringsWriteUtils.setAttributeForItems(myAndroidFacet.getModule().getProject(), attributeName, attributeValue, items);
  }

  private boolean setText(boolean isDefaultValue, @NotNull String text) {
    boolean dataChanged = false;
    boolean itemExists = isDefaultValue
                         ? myData.getDefaultValues().containsKey(mySelectedKey)
                         : myData.getTranslations().contains(mySelectedKey, mySelectedLocale);
    final Project project = myAndroidFacet.getModule().getProject();
    if (itemExists) {
      ResourceItem item = isDefaultValue
                          ? myData.getDefaultValues().get(mySelectedKey) : myData.getTranslations().get(mySelectedKey, mySelectedLocale);
      String oldText = StringResourceData.resourceToString(item);
      if (!oldText.equals(text)) {
        dataChanged = StringsWriteUtils.setItemText(project, item, text);
      }
    }
    else if (!text.isEmpty()) {
      dataChanged = StringsWriteUtils
        .createItem(project, myAndroidFacet.getPrimaryResourceDir(), isDefaultValue ? null : mySelectedLocale, mySelectedKey, text,
                    !myData.getUntranslatableKeys().contains(mySelectedKey));
    }
    return dataChanged;
  }
}
