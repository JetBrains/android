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
package com.android.tools.idea.editors.theme.qualifiers;

import com.android.ide.common.resources.configuration.ResourceQualifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * This interface represents a compact form of a ResourceQualifier, that can be restricted by some values.
 */
public interface RestrictedQualifier{
  /**
   * Sets restrictions based on compatible and incompatibles.
   * So that if compatible is not null,
   * <a href="http://developer.android.com/guide/topics/resources/providing-resources.html#Compatibility">Providing Resources</a> algorithm,
   * will choose compatible than any from incompatibles.
   * If compatible is null, then the Providing Resource Algorithm won't choose any from incompatibles.
   * @param compatible ResourceQualifier that need to be matching
   * @param incompatibles Collection of ResourceQualifiers need to avoid matching
   */
  void setRestrictions(@Nullable ResourceQualifier compatible, @NotNull Collection<ResourceQualifier> incompatibles);
  boolean isMatchFor(@Nullable ResourceQualifier qualifier);
  boolean isEmpty();

  /**
   * @return any qualifier that matches to restrictions
   */
  @Nullable("if there is no restrictions for this qualifier")
  Object getAny();

  @Nullable("if empty intersection")
  RestrictedQualifier intersect(@NotNull RestrictedQualifier otherRestricted);
}
