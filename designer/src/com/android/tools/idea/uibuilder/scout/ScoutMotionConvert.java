/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scout;

import static com.android.AndroidXConstants.CLASS_MOTION_LAYOUT;
import static com.android.SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION;
import static com.android.SdkConstants.SHERPA_URI;

import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import java.util.Locale;

/**
 * This performs direct conversion of ConstraintLayout to MotionLayout
 */
public final class ScoutMotionConvert {

  public static boolean convert(NlComponent layout) {

    Project project = layout.getModel().getProject();
    PsiFile layoutFile = layout.getTagPointer().getContainingFile();
    String fname = layoutFile.getName();
    fname = fname.substring(0, fname.lastIndexOf("."));
    String motion_scene_name = fname + "_scene";
    if (layout.getParent() != null) {
      String name = layout.getId();
      if (name == null) {
        name = layout.assignId(); // TODO this should be more sophisticated setting the id on the layout
      }
      motion_scene_name = layoutFile.getName() + "_" + name + "_scene";
    }
    for (NlComponent child : layout.getChildren()) {
      String name = child.getId();
      if (name == null) {
        child.assignId(child.getTagName());
      }
    }

    motion_scene_name = motion_scene_name.replace('.', '_').toLowerCase(Locale.US); // remove . and make lowercase
    motion_scene_name = motion_scene_name.replace(' ', '_'); // remove potential white space

    String text = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                  "<MotionScene \n" +
                  "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                  "    xmlns:motion=\"http://schemas.android.com/apk/res-auto\">\n" +
                  "\n" +
                  "    <Transition\n" +
                  "        motion:constraintSetEnd=\"@+id/end\"\n" +
                  "        motion:constraintSetStart=\"@id/start\"\n" +
                  "        motion:duration=\"1000\">\n" +
                  "       <KeyFrameSet>\n" +
                  "       </KeyFrameSet>\n" +
                  "    </Transition>\n" +
                  "\n" +
                  "    <ConstraintSet android:id=\"@+id/start\">\n" +
                  "    </ConstraintSet>\n" +
                  "\n" +
                  "    <ConstraintSet android:id=\"@+id/end\">\n" +
                  "    </ConstraintSet>\n" +
                  "</MotionScene>";

    EditorFactory editorFactory = EditorFactory.getInstance();

    PsiDirectory xmlDir = layoutFile.getParent().getParent().findSubdirectory("xml");
    if (xmlDir == null) {
      xmlDir = layoutFile.getParent().getParent().createSubdirectory("xml");
    }
    String countStr = "";
    int count = 1;
    while (xmlDir.findFile(motion_scene_name + countStr + ".xml") != null) {
      count++;
      countStr = Integer.toString(count);
    }
    motion_scene_name += countStr;
    PsiFile file =
      PsiFileFactory.getInstance(project).createFileFromText(motion_scene_name + ".xml", XmlFileType.INSTANCE, text);
    xmlDir.add(file);

    AttributesTransaction transaction = layout.startAttributeTransaction();

    layout.getTag().setName(DependencyManagementUtil.mapAndroidxName(layout.getModel().getModule(), CLASS_MOTION_LAYOUT));

    transaction.setAttribute(SHERPA_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION, "@xml/" + motion_scene_name);

    transaction.commit();

    return true;
  }
}
