/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res.aar;

import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceVisibility;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resource item representing an attr resource that is defined in a namespace different from the namespace
 * of the owning AAR.
 */
public class AarForeignAttrResourceItem extends AarAttrResourceItem {
  @NotNull private final ResourceNamespace myNamespace;

  /**
   * Initializes the resource.
   *
   * @param name the name of the resource
   * @param sourceFile the source file containing definition of the resource
   * @param namespace the namespace of the attr resource
   * @param description the description of the attr resource
   * @param formats the allowed attribute formats
   * @param valueMap the enum or flag integer values keyed by the value names. Some of the values in the
   *     map may be null. The map must contain the names of all declared values, even the ones that don't
   *     have corresponding numeric values.
   * @param valueDescriptionMap the the enum or flag value descriptions keyed by the value names
   */
  public AarForeignAttrResourceItem(@NotNull String name,
                                    @NotNull AarSourceFile sourceFile,
                                    @NotNull ResourceNamespace namespace,
                                    @Nullable String description,
                                    @NotNull Set<AttributeFormat> formats,
                                    @NotNull Map<String, Integer> valueMap,
                                    @NotNull Map<String, String> valueDescriptionMap) {
    super(name, sourceFile, ResourceVisibility.PUBLIC, description, formats, valueMap, valueDescriptionMap);
    myNamespace = namespace;
  }

  @Override
  @NotNull
  public ResourceNamespace getNamespace() {
    return myNamespace;
  }
}
