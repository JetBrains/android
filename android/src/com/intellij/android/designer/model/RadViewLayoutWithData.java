/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.model;

import com.intellij.android.designer.designSurface.layout.BorderStaticDecorator;
import com.intellij.designer.designSurface.StaticDecorator;
import com.intellij.designer.model.RadComponent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.android.designer.designSurface.graphics.DrawingStyle.SHOW_STATIC_BORDERS;

/**
 * @author Alexander Lobas
 */
public abstract class RadViewLayoutWithData extends RadViewLayout {
  private BorderStaticDecorator myBorderDecorator;

  @NotNull
  public abstract String[] getLayoutParams();

  private StaticDecorator getBorderDecorator() {
    if (myBorderDecorator == null) {
      myBorderDecorator = new BorderStaticDecorator(myContainer);
    }
    return myBorderDecorator;
  }

  @Override
  public void addStaticDecorators(List<StaticDecorator> decorators, List<RadComponent> selection) {
    //noinspection ConstantConditions
    if (!SHOW_STATIC_BORDERS) {
      return;
    }
    if (!selection.contains(myContainer) && myContainer.getParent() != myContainer.getRoot()) {
      decorators.add(getBorderDecorator());
    }
  }
}