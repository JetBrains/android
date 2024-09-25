/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.common.ui.templates;

import com.intellij.ui.AncestorListenerAdapter;
import javax.swing.JComponent;
import javax.swing.event.AncestorEvent;

/**
 * The view of the ViewModel architectural pattern that allows binding of the model's properties to
 * the Swing components of the view.
 */
public abstract class AbstractView<C extends JComponent> implements View<C> {
  private C component;

  @Override
  public C getComponent() {
    if (component == null) {
      component = createComponent();
      component.addAncestorListener(
          new AncestorListenerAdapter() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
              bind();
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
              unbind();
            }
          });
    }
    return component;
  }

  /** Create a Swing component corresponding to the view. */
  protected abstract C createComponent();

  /** Bind properties of the model to Swing UI elements */
  protected abstract void bind();

  /** Unbind properties of the model from Swing UI elements */
  protected abstract void unbind();
}
