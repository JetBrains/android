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

import com.android.tools.idea.ui.properties.AbstractProperty;
import com.android.tools.idea.ui.properties.core.BoolValueProperty;
import com.android.tools.idea.ui.properties.core.IntValueProperty;
import com.android.tools.idea.ui.properties.core.StringValueProperty;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.ObjectWrapper;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;

/**
 * A freemarker {@link ObjectWrapper} which, additionally, knows about our various
 * {@link AbstractProperty} classes (so we can add them directly into our freemarker data model).
 * <p/>
 * <a href="http://freemarker.org/docs/pgui_datamodel_objectWrapper.html">Click here</a> to read
 * more about Freemarker object wrappers.
 */
public final class PropertyObjectWrapper extends DefaultObjectWrapper {

  @Override
  protected TemplateModel handleUnknownType(final Object obj) throws TemplateModelException {
    if (obj instanceof StringValueProperty) {
      return new StringPropertyAdapter(((StringValueProperty)obj), this);
    }
    else if (obj instanceof IntValueProperty) {
      return new IntPropertyAdapter(((IntValueProperty)obj), this);
    }
    else if (obj instanceof BoolValueProperty) {
      return new BoolPropertyAdapter(((BoolValueProperty)obj), this);
    }

    return super.handleUnknownType(obj);
  }
}
