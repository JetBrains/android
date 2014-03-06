/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.parser;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;

import java.util.List;

/**
 * Represents a single Groovy statement in a specific closure that we understand and can deal with. This is intended as a parent for types
 * that consist of a list of unnamed statements inside a closure function to an argument, for example, dependencies or repositories:
 * <pre>
 *   dependencies {
 *     compile 'com.foo:1.0.0'
 *     compile 'com.bar:1.0.0'
 *   }
 * </pre>
 * In this example, each of the compile statements would be represented by an instance of an subtype of this class.
 * <p>
 * The purpose of this class is to provide a mechanism to deal with Groovy statements that we can't properly parse; we want to preserve
 * them and be able to edit other, parseable objects in the same block while leaving the unparseable ones alone. These unparseable
 * elements can be represented by the subtype class {@link UnparseableStatement}, which is a
 * container for their raw Groovy content that can be used to reconstruct the elements as they originally appear.
 * <p>
 * This mechanism is necessary because these objects are not individually addressable. The API doesn't provide a means to change the
 * value of one of these statements within its parent block. Instead, the API only allows you to do a bulk replace of the entire contents
 * of the parent closure, which means that we need to preserve unknown content so we can write it back out when the closure is rebuilt.
 * <p>
 * This class is not intended to be used for named objects, such as build types or flavors, which consist of a parent closure, under
 * which are a number of method calls where the call name is the object name and the object's values are inside a subclosure. With objects
 * of that type, individual key/value statements inside the object are individually addressable, and there is no need for a mechanism to
 * preserve contents of unparseable statements.
 */
public abstract class BuildFileStatement {
  @NotNull
  public abstract List<PsiElement> getGroovyElements(@NotNull GroovyPsiElementFactory factory);
}
