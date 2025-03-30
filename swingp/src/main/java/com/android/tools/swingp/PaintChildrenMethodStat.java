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
package com.android.tools.swingp;

import com.android.tools.swingp.json.IncludeMethodsSerializer;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import java.awt.geom.AffineTransform;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

@JsonAdapter(IncludeMethodsSerializer.class)
public class PaintChildrenMethodStat extends MethodStat {
  @SerializedName("xform")
  private final AffineTransform myTransform;
  /**
   * Need to ensure owner does not get GC'ed.
   * Retaining a hard reference is fine, since owner will be retained for only a very brief moment.
   */
  private final JComponent myOwnerReference;
  private final List<Class<?>> myPathFragmentClasses;

  @SerializedName("pathToRoot")
  private List<String> getPathToRoot() {
    return myPathFragmentClasses.stream().map(clazz -> clazz == null ? null : clazz.getSimpleName()).collect(Collectors.toList());
  }

  public PaintChildrenMethodStat(@NotNull JComponent owner, @NotNull AffineTransform transform) {
    super(owner);
    myOwnerReference = owner;
    myTransform = transform;
    myPathFragmentClasses =
      JComponentTreeManager.pushJComponent(owner).stream().map(container -> container == null ? null : container.getClass())
                           .collect(Collectors.toList());
  }

  @Override
  public void endMethod() {
    JComponentTreeManager.popJComponent(myOwnerReference);
    super.endMethod();
  }
}
