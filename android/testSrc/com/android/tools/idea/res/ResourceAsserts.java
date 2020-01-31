/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.res;

import static com.google.common.truth.Fact.fact;
import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertWithMessage;

import com.android.ide.common.rendering.api.ArrayResourceValue;
import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.DensityBasedResourceValue;
import com.android.ide.common.rendering.api.PluralsResourceValue;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.rendering.api.StyleableResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.util.PathString;
import com.android.resources.Density;
import com.android.tools.idea.resources.base.BasicFileResourceItem;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import java.net.URI;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Custom {@link Truth} subjects for Android resources.
 */
public class ResourceAsserts {
  @NotNull
  public static ResourceItemSubject assertThat(@Nullable ResourceItem item) {
    return assertAbout(resourceItems()).that(item);
  }

  @NotNull
  private static Subject.Factory<ResourceItemSubject, ResourceItem> resourceItems() {
    return (metadata, actual) -> new ResourceItemSubject(metadata, actual);
  }

  @NotNull
  public static ResourceValueSubject assertThat(@Nullable ResourceValue item) {
    return assertAbout(resourceValues()).that(item);
  }

  @NotNull
  private static Subject.Factory<ResourceValueSubject, ResourceValue> resourceValues() {
    return (metadata, actual) -> new ResourceValueSubject(metadata, actual);
  }

  public static class ResourceItemSubject extends Subject<ResourceItemSubject, ResourceItem> {
    protected ResourceItemSubject(@NotNull FailureMetadata metadata, @Nullable ResourceItem actual) {
      super(metadata, actual);
    }

    /**
     * Checks that the actual and the expected {@link ResourceItem}s are functionally equivalent.
     * Equivalence of two resource items implies equivalence of the corresponding {@link ResourceValue}s.
     * Unlike {@link ResourceItem#equals(Object)}, this method doesn't require the two resource items to
     * be instances of the the same class, or even to belong to the same resource repository. Items
     * belonging to repositories with the same contents, e.g. one loaded from flat files and another
     * loaded from a jar containing the same files are considered equivalent.
     */
    public final void isEquivalentTo(@Nullable ResourceItem expected) {
      if (actual() == expected) {
        return;
      }
      if (expected == null) {
        assertThat(actual()).isNull();
      }
      else {
        assertThat(actual()).isNotNull();
      }
      if (!actual().getType().equals(expected.getType())) {
        failWithoutActual(fact("expected type", expected.getType()), fact("but was", actual().getType()));
      }
      if (!actual().getNamespace().equals(expected.getNamespace())) {
        failWithoutActual(fact("expected namespace", expected.getNamespace()), fact("but was", actual().getNamespace()));
      }
      if (!actual().getName().equals(expected.getName())) {
        failWithoutActual(fact("expected name", expected.getName()), fact("but was", actual().getName()));
      }
      if (!Objects.equals(actual().getLibraryName(), expected.getLibraryName())) {
        failWithoutActual(fact("expected library name", expected.getLibraryName()), fact("but was", actual().getLibraryName()));
      }
      if (expected.isFileBased()) {
        if (!actual().isFileBased()) {
          failWithoutActual(simpleFact("expected file based"), simpleFact("but was not"));
        }
      }
      else {
        if (actual().isFileBased()) {
          failWithoutActual(simpleFact("expected not file based"), simpleFact("but was file based"));
        }
      }
      if (actual().isFileBased() && !areEquivalentSources(actual().getSource(), expected.getSource())) {
        failWithoutActual(fact("expected source", expected.getSource()), fact("but was", actual().getSource()));
      }
      if (!areEquivalentSources(actual().getOriginalSource(), expected.getOriginalSource())) {
        failWithoutActual(fact("expected original source", expected.getOriginalSource()), fact("but was", actual().getOriginalSource()));
      }
      if (!actual().getConfiguration().equals(expected.getConfiguration())) {
        failWithoutActual(fact("expected configuration", expected.getConfiguration()), fact("but was", actual().getConfiguration()));
      }
      assertThat(actual().getResourceValue()).isEquivalentTo(expected.getResourceValue());
    }
  }

  public static class ResourceValueSubject extends Subject<ResourceValueSubject, ResourceValue> {
    protected ResourceValueSubject(@NotNull FailureMetadata metadata, ResourceValue actual) {
      super(metadata, actual);
    }

    /**
     * Checks that the actual and the expected {@link ResourceValue}s are functionally equivalent.
     * Unlike {@link ResourceValue#equals(Object)}, this method doesn't require the two resource values
     * to be instances of the the same class, or even to belong to the same resource repository.
     * Resource values belonging to repositories with the same contents, e.g. one loaded from flat files
     * and another loaded from a jar containing the same files are considered equivalent.
     */
    public final void isEquivalentTo(@Nullable ResourceValue expected) {
      if (actual() == expected) {
        return;
      }
      if (expected == null) {
        assertThat(actual()).isNull();
      }
      else {
        assertThat(actual()).isNotNull();
      }
      if (!actual().getResourceType().equals(expected.getResourceType())) {
        failWithoutActual(fact("expected resource type", expected.getResourceType()), fact("but was", actual().getResourceType()));
      }
      if (!actual().getNamespace().equals(expected.getNamespace())) {
        failWithoutActual(fact("expected namespace", expected.getNamespace()), fact("but was", actual().getNamespace()));
      }
      if (!actual().getName().equals(expected.getName())) {
        failWithoutActual(fact("expected name", expected.getName()), fact("but was", actual().getName()));
      }
      if (!Objects.equals(actual().getLibraryName(), expected.getLibraryName())) {
        failWithoutActual(fact("expected library name", expected.getLibraryName()), fact("but was", actual().getLibraryName()));
      }

      Density density1 = actual() instanceof DensityBasedResourceValue ? ((DensityBasedResourceValue)actual()).getResourceDensity() : null;
      Density density2 = expected instanceof DensityBasedResourceValue ? ((DensityBasedResourceValue)expected).getResourceDensity() : null;
      assertWithMessage("density mismatch").that(density1).isEqualTo(density2);

      if (actual() instanceof StyleableResourceValue && expected instanceof StyleableResourceValue) {
        List<AttrResourceValue> actualAttributes = ((StyleableResourceValue)actual()).getAllAttributes();
        List<AttrResourceValue> expectedAttributes = ((StyleableResourceValue)expected).getAllAttributes();
        assertWithMessage("attribute number mismatch").that(actualAttributes.size()).isEqualTo(expectedAttributes.size());
        for (int i = 0; i < actualAttributes.size(); i++) {
          assertThat(actualAttributes.get(i)).isEquivalentTo(expectedAttributes.get(i));
        }
      } else if ((actual() instanceof StyleableResourceValue) != (expected instanceof StyleableResourceValue)) {
        failWithoutActual(fact("expected type", expected.getClass().getSimpleName()), fact("but was", actual().getClass().getSimpleName()));
      }

      if (actual() instanceof AttrResourceValue && expected instanceof AttrResourceValue) {
        AttrResourceValue actualAttr = (AttrResourceValue)actual();
        AttrResourceValue expectedAttr = (AttrResourceValue)expected;
        if (!Objects.equals(actualAttr.getDescription(), expectedAttr.getDescription())) {
          failWithoutActual(fact("expected description", expectedAttr.getDescription()), fact("but was", actualAttr.getDescription()));
        }
        if (!Objects.equals(actualAttr.getGroupName(), expectedAttr.getGroupName())) {
          failWithoutActual(fact("expected group name", expectedAttr.getGroupName()), fact("but was", actualAttr.getGroupName()));
        }
        if (!Objects.equals(actualAttr.getFormats(), expectedAttr.getFormats())) {
          failWithoutActual(fact("expected formats", expectedAttr.getFormats()), fact("but was", actualAttr.getFormats()));
        }
        Map<String, Integer> attrValues1 = actualAttr.getAttributeValues();
        Map<String, Integer> attrValues2 = expectedAttr.getAttributeValues();
        assertWithMessage("attribute values mismatch").that(attrValues1).isEqualTo(attrValues2);
        for (String valueName: attrValues1.keySet()) {
          assertWithMessage("value description mismatch")
              .that(actualAttr.getValueDescription(valueName)).isEqualTo(expectedAttr.getValueDescription(valueName));
        }
      } else if ((actual() instanceof AttrResourceValue) != (expected instanceof AttrResourceValue)) {
        failWithoutActual(fact("expected type", expected.getClass().getSimpleName()), fact("but was", actual().getClass().getSimpleName()));
      }

      if (actual() instanceof StyleResourceValue && expected instanceof StyleResourceValue) {
        StyleResourceValue actualStyle = (StyleResourceValue)actual();
        StyleResourceValue expectedStyle = (StyleResourceValue)expected;
        assertWithMessage("parent style mismatch")
            .that(actualStyle.getParentStyle()).isEqualTo(expectedStyle.getParentStyle());
        Collection<StyleItemResourceValue> actualItems = actualStyle.getDefinedItems();
        Collection<StyleItemResourceValue> expectedItems = expectedStyle.getDefinedItems();
        assertWithMessage("style item number mismatch").that(actualItems.size()).isEqualTo(expectedItems.size());
        Iterator<StyleItemResourceValue> it1 = actualItems.iterator();
        Iterator<StyleItemResourceValue> it2 = expectedItems.iterator();
        while (it1.hasNext()) {
          StyleItemResourceValue item1 = it1.next();
          StyleItemResourceValue item2 = it2.next();
          assertThat(item1).isEquivalentTo(item2);
        }
      } else if ((actual() instanceof StyleResourceValue) != (expected instanceof StyleResourceValue)) {
        failWithoutActual(fact("expected type", expected.getClass().getSimpleName()), fact("but was", actual().getClass().getSimpleName()));
      }

      if (actual() instanceof ArrayResourceValue && expected instanceof ArrayResourceValue) {
        ArrayResourceValue actualArray = (ArrayResourceValue)actual();
        ArrayResourceValue expectedArray = (ArrayResourceValue)expected;
        assertWithMessage("element count mismatch")
            .that(actualArray.getElementCount()).isEqualTo(expectedArray.getElementCount());
        for (int i = 0; i < actualArray.getElementCount(); i++) {
          Truth.assertThat(actualArray.getElement(i)).isEqualTo(expectedArray.getElement(i));
        }
      } else if ((actual() instanceof ArrayResourceValue) != (expected instanceof ArrayResourceValue)) {
        failWithoutActual(fact("expected type", expected.getClass().getSimpleName()), fact("but was", actual().getClass().getSimpleName()));
      }

      if (actual() instanceof PluralsResourceValue && expected instanceof PluralsResourceValue) {
        PluralsResourceValue actualPlurals = (PluralsResourceValue)actual();
        PluralsResourceValue expectedPlurals = (PluralsResourceValue)expected;
        assertWithMessage("plurals count mismatch")
            .that(actualPlurals.getPluralsCount()).isEqualTo(expectedPlurals.getPluralsCount());
        for (int i = 0; i < actualPlurals.getPluralsCount(); i++) {
          assertWithMessage("quantity mismatch")
              .that(actualPlurals.getQuantity(i)).isEqualTo(expectedPlurals.getQuantity(i));
          assertWithMessage("plurals value mismatch")
              .that(actualPlurals.getValue(i)).isEqualTo(expectedPlurals.getValue(i));
        }
      } else if ((actual() instanceof PluralsResourceValue) != (expected instanceof PluralsResourceValue)) {
        failWithoutActual(fact("expected type", expected.getClass().getSimpleName()), fact("but was", actual().getClass().getSimpleName()));
      }

      String actualValue = actual().getValue();
      String expectedValue = expected.getValue();
      if (actual() instanceof BasicFileResourceItem && expected instanceof BasicFileResourceItem) {
        PathString actualPath = ResourceHelper.toFileResourcePathString(actualValue);
        PathString expectedPath = ResourceHelper.toFileResourcePathString(expectedValue);
        if (!areEquivalentSources(actualPath, expectedPath)) {
          failWithoutActual(fact("expected value", expectedValue), fact("but was", actualValue));
        }
      } else if ((actual() instanceof BasicFileResourceItem) != (expected instanceof BasicFileResourceItem)) {
        failWithoutActual(fact("expected type", expected.getClass().getSimpleName()), fact("but was", actual().getClass().getSimpleName()));
      }
      else {
        assertWithMessage("value mismatch").that(actualValue).isEqualTo(expectedValue);
      }
    }
  }

  private static boolean areEquivalentSources(@Nullable PathString path1, @Nullable PathString path2) {
    if (Objects.equals(path1, path2)) {
      return true;
    }
    if (path1 != null && path2 != null) {
      URI filesystemUri1 = path1.getFilesystemUri();
      URI filesystemUri2 = path2.getFilesystemUri();
      URI nonFileUri = filesystemUri2;
      if (filesystemUri2.getScheme().equals("file") && !filesystemUri1.getScheme().equals("file")) {
        PathString temp = path1;
        path1 = path2;
        path2 = temp;
        nonFileUri = filesystemUri1;
      }
      String portablePath1 = path1.getPortablePath();
      String portablePath2 = path2.getPortablePath();
      if (nonFileUri.getScheme().equals("jar")) {
        int offset1 = indexOfEnd(portablePath1, "/res/");
        int offset2 = indexOfEnd(portablePath2, "/res/");
        return portablePath1.length() - offset1 == portablePath2.length() - offset2 &&
               portablePath1.regionMatches(offset1, portablePath2, offset2, portablePath1.length() - offset1);
      }
    }
    return false;
  }

  private static int indexOfEnd(@NotNull String stringToSearch, @SuppressWarnings("SameParameterValue") @NotNull String toSearchFor) {
    int index = stringToSearch.indexOf(toSearchFor);
    return index < 0 ? index : index + toSearchFor.length();
  }
}
