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

import com.google.common.base.Ascii;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.IconLoader;
import javax.swing.Icon;

/** Class to manage icons used by the Blaze plugin. */
public class BlazeIcons {

  private static final String BASE = "resources/icons/";

  public static final Icon Logo = loadForBuildSystem("logo.png"); // 16x16
  public static final Icon BazelLogo = load("bazel/logo.png"); // 16x16
  public static final Icon BlazeSlow = load("blaze_slow.png"); // 16x16
  public static final Icon Failed = loadForBuildSystem("failed.png"); // 16x16

  public static final Icon BlazeRerun = load("blazeRerun.png"); // 16x16

  // This is just the Blaze icon scaled down to the size IJ wants for tool windows.
  public static final Icon ToolWindow = loadForBuildSystem("tool_window.png"); // 13x13

  // Build file support icons
  public static final Icon BuildFile = load("build_file.png"); // 16x16
  public static final Icon BuildRule = load("build_rule.png"); // 16x16

  public static final Icon LightningOverlay = load("lightningOverlay.png"); // 16x16

  private static Icon load(String path) {
    return IconLoader.getIcon(BASE + path, BlazeIcons.class);
  }

  private static Icon loadForBuildSystem(String basename) {
    // Guessing the build system name may result in an NPE if (e.g.) it was called from a
    // unit-testing scenario where there aren't Application/ProjectManager instances yet.
    if (ApplicationManager.getApplication() == null || ProjectManager.getInstance() == null) {
      // Default to the blaze icons.
      return load("blaze/" + basename);
    } else {
      return load(Ascii.toLowerCase(Blaze.guessBuildSystemName()) + "/" + basename);
    }
  }
}
