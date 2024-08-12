/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package icons;

import com.intellij.openapi.util.IconLoader;
import javax.swing.Icon;

/** Class to manage icons used by the Blaze plugin. */
public class BlazeAndroidIcons {

  private static final String BASE = "/tools/vendor/google3/aswb/third_party/intellij/bazel/plugin/";

  public static final Icon MobileInstallRun =
      load("aswb/resources/icons/mobileInstallRun.png"); // 16x16
  public static final Icon MobileInstallDebug =
      load("aswb/resources/icons/mobileInstallDebug.png"); // 16x16
  public static final Icon Crow = load("aswb/resources/icons/crow.png"); // 16x16
  public static final Icon CrowToolWindow =
      load("aswb/resources/icons/crowToolWindow.png"); // 13x13

  private static Icon load(String path) {
    return IconLoader.findIcon(BASE + path, BlazeAndroidIcons.class);
  }
}
