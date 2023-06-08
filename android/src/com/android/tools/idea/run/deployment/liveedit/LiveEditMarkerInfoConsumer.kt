/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit

import com.android.tools.idea.run.deployment.liveedit.desugaring.MinApiLevel
import com.android.tools.r8.MarkerInfoConsumer
import com.android.tools.r8.MarkerInfoConsumerData

class LiveEditMarkerInfoConsumer() : MarkerInfoConsumer {

  val minApis : MutableSet<MinApiLevel> = mutableSetOf()

  override fun acceptMarkerInfo(info: MarkerInfoConsumerData?) {
    info?.markers?.forEach{
      // R8 returns -1 if it found marker but no min-api was in it.
      // This is a workaround for b/283737440. Once fixed, we can delete
      // this test.
      if (it.minApi != -1) {
        minApis.add(it.minApi)
      }
    }
  }

  override fun finished() {
  }
}