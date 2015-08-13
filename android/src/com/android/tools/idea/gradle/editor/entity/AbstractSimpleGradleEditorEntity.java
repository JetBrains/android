/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.editor.entity;

import com.android.tools.idea.gradle.editor.metadata.GradleEditorEntityMetaData;
import com.android.tools.idea.gradle.editor.value.GradleEditorEntityValueManager;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Super class for {@link GradleEditorEntity} which hold a single value (e.g. gradle plugin version, compile sdk version etc).
 * <p/>
 * External dependency, for example, is another type of entity - it holds four different values (scope, group id, artifact id, version).
 */
public abstract class AbstractSimpleGradleEditorEntity extends AbstractGradleEditorEntity
  implements GradleEntityDeclarationValueLocationAware, GradleEntityDefinitionValueLocationAware {

  @NotNull private final String myName;
  @NotNull private final List<GradleEditorSourceBinding> myDefinitionValueSourceBindings;
  @NotNull private final GradleEditorEntityValueManager myValueManager;
  @NotNull private final GradleEditorSourceBinding myDeclarationValueLocation;
  @NotNull private String myCurrentValue;

  public AbstractSimpleGradleEditorEntity(@NotNull String name,
                                          @NotNull String currentValue,
                                          @NotNull Collection<GradleEditorSourceBinding> definitionValueSourceBindings,
                                          @NotNull GradleEditorSourceBinding entityLocation,
                                          @NotNull Set<GradleEditorEntityMetaData> metaData,
                                          @NotNull GradleEditorSourceBinding declarationValueLocation,
                                          @NotNull GradleEditorEntityValueManager valueManager,
                                          @Nullable String helpUrl) {
    super(entityLocation, metaData, helpUrl);
    myName = name;
    myCurrentValue = currentValue;
    myDefinitionValueSourceBindings = ImmutableList.copyOf(definitionValueSourceBindings);
    myDeclarationValueLocation = declarationValueLocation;
    myValueManager = valueManager;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  /**
   * @return  empty string as an indication that target value is not hard-coded but evaluated at runtime, e.g. 'a = 1' - simple value,
   *          it's expected that current method returns {@code '1'} and {@link #getDefinitionValueSourceBindings()} holds single element
   *          which points to the {@code '1'} range. However, expression like 'a = 1 + 2' is not obliged to be evaluated by a parser, so,
   *          an empty string is returned from this method and {@link #getDefinitionValueSourceBindings()} contains single element which
   *          points to the whole {@code '1 + 2'} range
   */
  @NotNull
  public String getCurrentValue() {
    return myCurrentValue;
  }

  @NotNull
  @Override
  public GradleEditorSourceBinding getDeclarationValueLocation() {
    return myDeclarationValueLocation;
  }

  @Nullable
  @Override
  public GradleEditorSourceBinding getDefinitionValueLocation() {
    return myDefinitionValueSourceBindings.size() == 1 ? myDefinitionValueSourceBindings.get(0) : null;
  }

  /**
   * Tries to apply given value to the current entity and {@link #getDefinitionValueSourceBindings() backing files}.
   * <p/>
   * Main success scenario here is to show UI for config properties manipulations and flush user-defined values via this method.
   *
   * @param newValue  new value to use
   * @return          null as an indication that given value has been successfully applied; an error message otherwise
   */
  @Nullable
  public String changeValue(@NotNull String newValue) {
    if (newValue.equals(getCurrentValue())) {
      return null;
    }
    List<GradleEditorSourceBinding> sourceBindings = getDefinitionValueSourceBindings();
    if (sourceBindings.size() != 1) {
      return String.format(
        "Can't apply version '%s' to the entity '%s'. Reason: expected the entity to hold only one source binding " + "but it has %d (%s)",
        newValue, this, sourceBindings.size(), sourceBindings);
    }
    GradleEditorSourceBinding binding = sourceBindings.get(0);
    RangeMarker rangeMarker = binding.getRangeMarker();
    if (!rangeMarker.isValid()) {
      return String.format("Can't apply version '%s' to the entity '%s'. Reason: source file binding is incorrect", newValue, this);
    }
    myCurrentValue = newValue;
    rangeMarker.getDocument().replaceString(rangeMarker.getStartOffset(), rangeMarker.getEndOffset(), newValue);
    return null;
  }

  @NotNull
  public List<GradleEditorSourceBinding> getDefinitionValueSourceBindings() {
    return myDefinitionValueSourceBindings;
  }

  @NotNull
  public GradleEditorEntityValueManager getValueManager() {
    return myValueManager;
  }

  @Override
  public void dispose() {
    super.dispose();
    Disposer.dispose(myDeclarationValueLocation);
    for (GradleEditorSourceBinding sourceBinding : myDefinitionValueSourceBindings) {
      Disposer.dispose(sourceBinding);
    }
  }

  @Override
  public String toString() {
    final String value;
    List<GradleEditorSourceBinding> bindings = getDefinitionValueSourceBindings();
    if (bindings.isEmpty()) {
      value = "<undefined>";
    }
    else if (bindings.size() > 1) {
      value = "<ref>";
    }
    else {
      Document document = FileDocumentManager.getInstance().getDocument(bindings.get(0).getFile());
      RangeMarker rangeMarker = bindings.get(0).getRangeMarker();
      if (document == null) {
        value = "<unexpected!>";
      }
      else {
        value = '[' + document.getCharsSequence().subSequence(rangeMarker.getStartOffset(), rangeMarker.getEndOffset()).toString() + ']';
      }
    }
    return getName() + " " + value;
  }
}
