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
package com.android.tools.idea.gradle.variant.conflict;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.intellij.openapi.module.Module;
import com.intellij.util.containers.ContainerUtil;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Conflict {
  @NotNull private final Module mySource;
  @NotNull private final String mySelectedVariant;

  // Key: variant expected by module, Value: all modules expecting the variant used as key.
  @NotNull private final Multimap<String, AffectedModule> myAffectedModulesByExpectedVariant = ArrayListMultimap.create();

  @NotNull private final List<AffectedModule> myAffectedModules = new ArrayList<>();

  private boolean myResolved;

  public Conflict(@NotNull Module source, @NotNull String selectedVariant) {
    mySource = source;
    mySelectedVariant = selectedVariant;
  }

  public void addAffectedModule(@NotNull Module target, @NotNull String expectedVariant) {
    AffectedModule affected = new AffectedModule(this, target, expectedVariant);
    myAffectedModules.add(affected);
    myAffectedModulesByExpectedVariant.put(expectedVariant, affected);
  }

  @NotNull
  public Collection<String> getVariants() {
    return myAffectedModulesByExpectedVariant.keySet();
  }

  @NotNull
  public Collection<AffectedModule> getModulesExpectingVariant(@NotNull String variant) {
    return myAffectedModulesByExpectedVariant.get(variant);
  }

  @NotNull
  public Module getSource() {
    return mySource;
  }

  @NotNull
  public String getSelectedVariant() {
    return mySelectedVariant;
  }

  @NotNull
  public List<AffectedModule> getAffectedModules() {
    return myAffectedModules;
  }

  public void refreshStatus() {
    int selectedVariantCount = 0;
    for (String variant : myAffectedModulesByExpectedVariant.keySet()) {
      for (AffectedModule affected : getModulesExpectingVariant(variant)) {
        if (affected.isSelected()) {
          selectedVariantCount++;
          break;
        }
      }
    }
    setResolved(selectedVariantCount <= 1);
  }

  public boolean isResolved() {
    return myResolved;
  }

  public void setResolved(boolean resolved) {
    this.myResolved = resolved;
  }

  @Override
  public String toString() {
    String text = String.format("Module '%1$s' has variant '%2$s' selected, but", mySource.getName(), mySelectedVariant);

    List<String> expectedVariants = Lists.newArrayList(getVariants());
    if (expectedVariants.size() == 1) {
      String expectedVariant = expectedVariants.get(0);
      Collection<Conflict.AffectedModule> modules = getModulesExpectingVariant(expectedVariant);
      String moduleNames = getModuleNames(modules);
      String format = modules.size() == 1 ? " the module '%1$s' depends on variant '%2$s'" : " the modules %1$s depend on variant '%2$s'";
      text += String.format(format, moduleNames, expectedVariant);
    }
    else {
      Collections.sort(expectedVariants);
      for (String expectedVariant : expectedVariants) {
        Collection<Conflict.AffectedModule> modules = getModulesExpectingVariant(expectedVariant);
        String moduleNames = getModuleNames(modules);
        text += "\n- ";
        String format = modules.size() == 1 ? "Module '%1$s' depends on variant '%2$s'" : "Modules %1$s depend on variant '%2$s'";
        text += String.format(format, moduleNames, expectedVariant);
      }
    }
    return text;
  }

  @NotNull
  private static String getModuleNames(@NotNull Collection<Conflict.AffectedModule> modules) {
    if (modules.size() == 1) {
      Conflict.AffectedModule module = ContainerUtil.getFirstItem(modules);
      assert module != null;
      return "'" + module.getTarget().getName() + "'";
    }
    List<String> names = new ArrayList<>();
    for (Conflict.AffectedModule module : modules) {
      names.add("'" + module.getTarget().getName() + "'");
    }
    Collections.sort(names);
    return names.toString();
  }

  public static class AffectedModule {
    @NotNull private final Conflict myConflict;
    @NotNull private final Module myTarget;
    @NotNull private final String myExpectedVariant;
    private boolean mySelected = true;

    AffectedModule(@NotNull Conflict conflict, @NotNull Module target, @NotNull String expectedVariant) {
      myConflict = conflict;
      myTarget = target;
      myExpectedVariant = expectedVariant;
    }

    @NotNull
    public Conflict getConflict() {
      return myConflict;
    }

    @NotNull
    public Module getTarget() {
      return myTarget;
    }

    @NotNull
    public String getExpectedVariant() {
      return myExpectedVariant;
    }

    public boolean isSelected() {
      return mySelected;
    }

    public void setSelected(boolean selected) {
      mySelected = selected;
      myConflict.refreshStatus();
    }
  }
}
