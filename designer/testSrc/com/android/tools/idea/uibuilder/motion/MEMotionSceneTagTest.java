/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.motion;

import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import junit.framework.TestCase;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.runner.Description;

public class MEMotionSceneTagTest extends TestCase {
  MotionSceneTag myTag;

   static String  DEFAULT_SCENE_FILE = "scene.xml";

  public void testMotionSceneParse() {

    AndroidProjectRule rule = AndroidProjectRule.inMemory();
    rule.before(Description.EMPTY);
    rule.initAndroid(true);
    rule.getFixture().setTestDataPath(AndroidTestBase.getModulePath("designer") + "/testData/motion");
     VirtualFile scene =  rule.getFixture().copyFileToProject("scene.xml", "res/xml/scene.xml");
    rule.getFixture().copyFileToProject("attrs.xml", "res/values/attrs.xml");
     XmlFile sceneFile = (XmlFile) AndroidPsiUtils.getPsiFileSafely(rule.getProject(), scene);
    Application app = ApplicationManager.getApplication();
    Project project =  ProjectManager.getInstance().getDefaultProject();
    app.invokeAndWait(() -> myTag = MotionSceneTag.parse(null, project, scene, sceneFile));
    assertNotNull("MotionSceneTag.parse return null",myTag);
    assertEquals("root tag ", "MotionScene", myTag.getTagName());
    MTag[]tags = myTag.getChildTags();
    assertEquals("number of Children", 4, tags.length);
    MTag [] tag = myTag.getChildTags("ConstraintSet", "id", "@+id/start");
    assertEquals("constraintset with id = start", 1, tag.length);
    tag = myTag.getChildTags("Transition");
    assertEquals("transition", 1, tag.length);
    assertEquals("Transition duration is", "2000", tag[0].getAttributeValue("duration"));
  }
}
