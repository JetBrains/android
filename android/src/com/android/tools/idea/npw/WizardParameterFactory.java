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
package com.android.tools.idea.npw;


import com.android.tools.idea.templates.Parameter;
import com.android.tools.idea.wizard.dynamic.ScopedDataBinder;
import com.intellij.openapi.extensions.ExtensionPointName;

import javax.swing.*;

/**
 * This extension point can be implemented to add custom UI to a wizard.
 * This factory registers a friendly type name (which can be any unique string) and provides
 * the UI and binding used in the wizard.
 * Templates can use this UI by specifying the typename in their template.xml,
 */
public interface WizardParameterFactory {

  public static ExtensionPointName<WizardParameterFactory> EP_NAME =
    new ExtensionPointName<WizardParameterFactory>("org.jetbrains.android.wizardParameterFactory");


  /**
   * Returns the list of supported typenames created by this factory.
   * These are short friendly names that appear for Parameters in template.xml via type="[classname]"
   */
  String[] getSupportedTypes();

  /**
   * Creates a component for the given friendly typename.
   * The parameter information is also passed in which contains information from the template.xml file.
   */
  JComponent createComponent(String type, Parameter parameter);

  /**
   * Returns a Binding for the created component that gets/puts values into the UI.
   * This Binding is also called to attach event listeners for updating state.
   * When the event is fired, the argument for the event should be "[component]", otherwise the event may be ignored.
   */
  ScopedDataBinder.ComponentBinding<String, JComponent> createBinding(JComponent component, Parameter parameter);
}