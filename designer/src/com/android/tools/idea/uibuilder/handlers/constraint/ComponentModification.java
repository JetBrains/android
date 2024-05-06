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
package com.android.tools.idea.uibuilder.handlers.constraint;

import com.android.SdkConstants;
import com.android.tools.idea.common.command.NlWriteCommandActionUtil;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlAttributesHolder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlComponentModificationDelegate;
import com.android.utils.Pair;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

/**
 * Encapsulate write operations on components
 * Give a chance to an eventual delegate to do something.
 */
public class ComponentModification implements NlAttributesHolder {

  private final NlComponent myComponent;
  private final NlComponentModificationDelegate myDelegate;
  private final String myLabel;

  public ComponentModification(@NotNull NlComponent component, @NotNull String label) {
    myComponent = component;
    myLabel = label;
    myDelegate = myComponent.getComponentModificationDelegate();

    if (myDelegate != null) {
      myDelegate.initializeModification(this);
    } else {
      component.startAttributeTransaction();
    }
  }

  @NotNull
  public String getLabel() {
    return myLabel;
  }

  @NotNull
  public NlComponent getComponent() {
    return myComponent;
  }

  HashMap<Pair<String, String>, String> myAttributes = new HashMap<>();

  @Override
  public void setAttribute(@Nullable String namespace, @NotNull String name, @Nullable String value) {
    if (myDelegate != null) {
      myDelegate.setAttribute(myAttributes, namespace, name, value);
    } else {
      AttributesTransaction transaction = myComponent.startAttributeTransaction();
      transaction.setAttribute(namespace, name, value);
    }
  }

  @Override
  public String getAttribute(@Nullable String namespace, @NotNull String attribute) {
    if (myDelegate != null) {
      return myDelegate.getAttribute(myAttributes, namespace, attribute);
    }
    AttributesTransaction transaction = myComponent.startAttributeTransaction();
    return transaction.getAttribute(namespace, attribute);
  }

  public HashMap<Pair<String, String>, String> getAttributes() { return myAttributes; }

  @Override
  public void removeAttribute(@NotNull String namespace, @NotNull String name) {
    myAttributes.remove(Pair.of(namespace, name));
  }

  public void apply() {
    if (myDelegate != null) {
      myDelegate.applyModification(this);
      return;
    }
    directApply();
  }

  public void directApply() {
    AttributesTransaction transaction = myComponent.startAttributeTransaction();
    transaction.apply();
  }

  public void commit() {
    if (myDelegate != null) {
      myDelegate.commitModification(this);
      return;
    }
    directCommit();
  }

  public void directCommit() {
    AttributesTransaction transaction = myComponent.startAttributeTransaction();
    transaction.apply();
    NlWriteCommandActionUtil.run(myComponent, myLabel, transaction::commit);
  }

  public void commitTo(XmlTag view) {
    for (Pair<String, String> key : myAttributes.keySet()) {
      String value = myAttributes.get(key);
      String namespace = key.getFirst();
      if (namespace.equalsIgnoreCase(SdkConstants.TOOLS_URI)) {
        namespace = SdkConstants.AUTO_URI;
      }
      view.setAttribute(key.getSecond(), namespace, value);
    }
  }
}
