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
package com.android.tools.idea.whatsnew.assistant;

import com.android.repository.Revision;
import com.google.common.truth.Truth;
import org.junit.Test;

public class WhatsNewBundleTest {
  @Test
  public void getVersionNormal() {
    Truth.assertThat(WhatsNewBundle.getVersion("3.5.0")).isEqualTo(Revision.parseRevision("3.5.0"));
    Truth.assertThat(WhatsNewBundle.getVersion("0.0.0")).isEqualTo(Revision.parseRevision("0.0.0"));
    Truth.assertThat(WhatsNewBundle.getVersion("0.0.0000")).isEqualTo(Revision.parseRevision("0.0.0"));
    Truth.assertThat(WhatsNewBundle.getVersion("999.999.999")).isEqualTo(Revision.parseRevision("999.999.999"));
  }

  @Test
  public void getVersionNoPeriodsBackwardsCompatibility() {
    Truth.assertThat(WhatsNewBundle.getVersion("3500")).isEqualTo(Revision.parseRevision("3.5.0"));
    Truth.assertThat(WhatsNewBundle.getVersion("0000")).isEqualTo(Revision.parseRevision("0.0.0"));
    Truth.assertThat(WhatsNewBundle.getVersion("1234567890")).isEqualTo(Revision.parseRevision("1.2345678.90"));
    Truth.assertThat(WhatsNewBundle.getVersion("1")).isEqualTo(Revision.parseRevision("0.0.1"));
    Truth.assertThat(WhatsNewBundle.getVersion("01")).isEqualTo(Revision.parseRevision("0.0.1"));
    Truth.assertThat(WhatsNewBundle.getVersion("10")).isEqualTo(Revision.parseRevision("0.0.10"));
    Truth.assertThat(WhatsNewBundle.getVersion("210")).isEqualTo(Revision.parseRevision("0.2.10"));
  }

  @Test
  public void getVersionUnspecified() {
    Truth.assertThat(WhatsNewBundle.getVersion("2019.1.1.0.0")).isEqualTo(Revision.NOT_SPECIFIED);
  }
}
