/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.android.tools.dom.attrs;

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StyleableDefinitionImpl implements StyleableDefinition {
  @NonNull private final ResourceReference myStyleable;
  @NonNull private final List<AttributeDefinition> myAttributes = new ArrayList<>();

  public StyleableDefinitionImpl(@NonNull ResourceNamespace namespace, @NonNull String name) {
    myStyleable = ResourceReference.styleable(namespace, name);
  }

  @Override
  @NonNull
  public ResourceReference getResourceReference() {
    return myStyleable;
  }

  @Override
  @NonNull
  public String getName() {
    return myStyleable.getName();
  }

  public void addAttribute(@NonNull AttributeDefinition attrDef) {
    myAttributes.add(attrDef);
  }

  @Override
  @NonNull
  public List<AttributeDefinition> getAttributes() {
    return Collections.unmodifiableList(myAttributes);
  }
}
