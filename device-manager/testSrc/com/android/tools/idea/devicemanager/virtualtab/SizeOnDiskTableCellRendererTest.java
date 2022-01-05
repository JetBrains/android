/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SizeOnDiskTableCellRendererTest {
  @Test
  public void sizeOnDiskTableCellRendererToString() {
    assertEquals("5.3 MB", SizeOnDiskTableCellRenderer.toString((long)(5.32 * 1024 * 1024)));
    assertEquals("5.4 MB", SizeOnDiskTableCellRenderer.toString((long)(5.37 * 1024 * 1024)));
    assertEquals("9.3 MB", SizeOnDiskTableCellRenderer.toString((long)(9.3 * 1024 * 1024)));
    assertEquals("10 MB", SizeOnDiskTableCellRenderer.toString((long)(9.98 * 1024 * 1024)));
    assertEquals("123 MB", SizeOnDiskTableCellRenderer.toString((long)(123.4 * 1024 * 1024)));
    assertEquals("124 MB", SizeOnDiskTableCellRenderer.toString((long)(123.6 * 1024 * 1024)));
    assertEquals("1,023 MB", SizeOnDiskTableCellRenderer.toString((long)(1023.0 * 1024 * 1024)));
    assertEquals("18 GB", SizeOnDiskTableCellRenderer.toString((long)(18.0 * 1024 * 1024 * 1024)));
  }
}
