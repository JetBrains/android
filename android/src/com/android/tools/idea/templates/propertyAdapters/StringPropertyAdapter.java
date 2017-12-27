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
package com.android.tools.idea.templates.propertyAdapters;

import com.android.tools.idea.observable.core.StringValueProperty;
import freemarker.template.*;
import org.jetbrains.annotations.NotNull;

/**
 * An adapter which wraps a {@link StringValueProperty} in order to expose it to Freemarker.
 */
public final class StringPropertyAdapter extends WrappingTemplateModel implements TemplateScalarModel, AdapterTemplateModel {

  @NotNull private final StringValueProperty myStringProperty;

  public StringPropertyAdapter(@NotNull StringValueProperty stringProperty, ObjectWrapper objectWrapper) {
    super(objectWrapper);
    myStringProperty = stringProperty;
  }

  @Override
  public Object getAdaptedObject(Class hint) {
    return myStringProperty;
  }

  @Override
  public String getAsString() throws TemplateModelException {
    return myStringProperty.get();
  }
}
