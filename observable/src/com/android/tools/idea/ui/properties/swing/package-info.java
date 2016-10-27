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

/**
 * Classes which wrap fields in Swing classes, exposing them as {@link com.android.tools.idea.ui.properties.AbstractProperty}s.
 * <p/>
 * This extra structure layered on top of Swing lets the developer specify relationships between various GUI fields, enjoying the same
 * concise, powerful bindings syntax that properties use. This often results in many fewer lines of code than using the traditional Swing
 * listener approach, and as a bonus, all listeners are automatically released when the bindings manager goes out of scope.
 * <p/>
 * The following examples provide a quick introduction to Swing properties:
 * <pre>
 *   JTextField textField = new JTextField();
 *   JLabel label = new JLabel();
 *   JCheckBox checkBox = new JCheckBox();
 *
 *   // Direct interaction
 *   textField.setText("TextField input");
 *   label.setText("Label input");
 *   checkBox.setSelected(true);
 *
 *   // Using Swing properties instead
 *   StringProperty textFieldText = new TextProperty(textField);
 *   StringProperty labelText = new TextProperty(label);
 *   BoolProperty checkBoxSelected = new SelectedProperty(checkBox);
 *   textFieldText.set("TextField input");
 *   labelText.set("Label input");
 *   checkBoxSelected.set(true);
 *
 *   // With bindings
 *   BindingsManager bindings = new BindingsManager();
 *   StringProperty targetValue = new StringValueProperty();
 *   bindings.bind(textFieldText, targetValue);
 *   targetValue.set("The TextField is listening to this value through bindings");
 * </pre>
 */
package com.android.tools.idea.ui.properties.swing;