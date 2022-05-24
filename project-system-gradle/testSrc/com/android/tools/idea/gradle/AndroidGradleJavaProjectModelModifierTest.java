// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.gradle;

import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import org.junit.Assert;
import org.junit.Test;

public class AndroidGradleJavaProjectModelModifierTest {

  @Test
  public void selectVersionShouldReturnPreferredVersionForUnknownArtifact() {
    String preferred = "2.0";
    ExternalLibraryDescriptor desc = new ExternalLibraryDescriptor("a.b", "c", "1.0", "4.0", preferred);
    String selected = AndroidGradleJavaProjectModelModifier.selectVersion(desc);
    Assert.assertEquals("selectVersion() should return preferred version if it was specified",
                        preferred, selected);
  }

  @Test
  public void selectVersionShouldReturnPreferredVersionForWellKnownArtifact() {
    String preferred = "19.0.0";
    ExternalLibraryDescriptor desc = new ExternalLibraryDescriptor("org.jetbrains", "annotations", "1.0", "20.0", preferred);
    String selected = AndroidGradleJavaProjectModelModifier.selectVersion(desc);
    Assert.assertEquals("selectVersion() should return preferred version if it was specified",
                        preferred, selected);
  }

  @Test
  public void selectVersionShouldReturnPredefinedVersionForWellKnownArtifactIfNoPreferred() {
    ExternalLibraryDescriptor desc = new ExternalLibraryDescriptor("org.jetbrains", "annotations");
    String selected = AndroidGradleJavaProjectModelModifier.selectVersion(desc);
    Assert.assertNotNull("selectVersion() should return predefined fallback version if no preferred " +
                         "version explicitly specified", selected);
  }
}