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
 * Provides classes that wrap values which can then be queried, set, and chained together via bindings.
 * <p/>
 * In Java, the concept of properties is traditionally implemented by convention, requiring strictly named {@code getXXX} and {@code setXXX}
 * methods. However, wrapping this concept in a class (see {@link com.android.tools.idea.ui.properties.AbstractProperty}) makes this
 * behavior more explicit and allows setting up relationships between properties via bindings
 * (see {@link com.android.tools.idea.ui.properties.BindingsManager}).
 * <p/>
 * Example code is provided in the testSrc folder under the {@code com.android.tools.idea.ui.properties.demo} package, which aims to show
 * off specific features in more depth, but here is a quick overview for using the classes in this package:
 * <p/>
 * <pre>
 *   BindingsManager bindings = new BindingsManager();
 *   StringProperty name = new StringValueProperty("Joe Random");
 *   IntProperty age = new IntValueProperty(22);
 *   BoolProperty isCitizen = new BoolValueProperty(true);
 *   // The following properties exist to be bound to the above properties
 *   BoolProperty canVote = new BoolValueProperty();
 *   BoolProperty validName = new BoolValueProperty();
 *   StringProperty message = new StringValueProperty();
 *
 *   // You can add a listener to any property
 *   message.addListener((sender) -> System.out.println("Message changed: " + ((StringProperty)sender).get()));
 *
 *   bindings.bind(canVote, age.isGreaterThanEqualTo(16).and(isCitizen));
 *   bindings.bind(validName, not(name.isEmpty()));
 *   bindings.bind(message, new FormatExpression("Hello, %1$s", name));
 *
 *   // After BindingsManager updates (which happens lazily by default, as this allows it to ignore redundant updates)...
 *   assert canVote.get() == true;
 *   assert validName.get() == true;
 *   assert message.get() == "Hello, Joe Random";
 * </pre>
 */
package com.android.tools.idea.ui.properties;