/*
 * Copyright (C) 2026 The Android Open Source Project
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
/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.configurations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.configurations.Configuration.ImageTransformationType;
import com.android.tools.configurations.SystemUiPreferences;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.Test;

public class SystemUiPreferencesTest {

  @Test
  public void testFontScale() {
    SystemUiPreferences prefs = new SystemUiPreferences();
    assertEquals(1f, prefs.getFontScale(), 0.0f);

    prefs.setFontScale(1.5f);
    assertEquals(1.5f, prefs.getFontScale(), 0.0f);
  }

  @Test
  public void testImageTransformationsExhaustive() {
    SystemUiPreferences prefs = new SystemUiPreferences();
    assertNull(prefs.getImageTransformation());

    AtomicBoolean colorBlindInvoked = new AtomicBoolean(false);
    Consumer<BufferedImage> colorBlindConsumer = (image) -> colorBlindInvoked.set(true);

    // 1. Add first transformation
    prefs.setImageTransformation(ImageTransformationType.COLOR_BLIND_MODE, colorBlindConsumer);
    assertNotNull(prefs.getImageTransformation());
    prefs.getImageTransformation().accept(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
    assertTrue(colorBlindInvoked.get());

    // 2. Add second transformation
    AtomicBoolean glassesInvoked = new AtomicBoolean(false);
    Consumer<BufferedImage> glassesConsumer = (image) -> glassesInvoked.set(true);
    prefs.setImageTransformation(ImageTransformationType.GLASSES_BACKGROUND_IMAGE, glassesConsumer);

    colorBlindInvoked.set(false);
    prefs.getImageTransformation().accept(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
    assertTrue(colorBlindInvoked.get());
    assertTrue(glassesInvoked.get());

    // 3. Remove first transformation
    prefs.setImageTransformation(ImageTransformationType.COLOR_BLIND_MODE, null);
    colorBlindInvoked.set(false);
    glassesInvoked.set(false);
    prefs.getImageTransformation().accept(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
    assertFalse(colorBlindInvoked.get());
    assertTrue(glassesInvoked.get());

    // 4. Remove second transformation
    prefs.setImageTransformation(ImageTransformationType.GLASSES_BACKGROUND_IMAGE, null);
    assertNull(prefs.getImageTransformation());
  }

  @Test
  public void testCopyFromClonesStateCorrectly() {
    SystemUiPreferences original = new SystemUiPreferences();
    original.setFontScale(2.0f);
    original.setEdgeToEdge(false);
    original.setGestureNav(false);
    original.setUseThemedIcon(true);

    SystemUiPreferences clone = new SystemUiPreferences();
    clone.copyFrom(original);

    assertEquals(2.0f, clone.getFontScale(), 0.0f);
    assertFalse(clone.isEdgeToEdge());
    assertFalse(clone.isGestureNav());
    assertTrue(clone.getUseThemedIcon());
  }
}

