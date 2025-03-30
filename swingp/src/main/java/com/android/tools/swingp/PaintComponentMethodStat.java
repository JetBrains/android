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
package com.android.tools.swingp;

import com.android.tools.swingp.json.IncludeMethodsSerializer;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import java.awt.Graphics;
import java.awt.geom.AffineTransform;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import sun.awt.image.BufImgSurfaceData;
import sun.java2d.SunGraphics2D;
import sun.java2d.SurfaceData;

@JsonAdapter(IncludeMethodsSerializer.class) // Needed to properly serialize MethodStat fields
public class PaintComponentMethodStat extends MethodStat {
  @SerializedName("xform")
  private final AffineTransform myTransform;
  @SerializedName("clip")
  private final int[] myClip;
  @SerializedName("isImage")
  private final boolean myIsImage;

  public PaintComponentMethodStat(@NotNull JComponent owner,
                                  @NotNull Graphics co,
                                  @NotNull AffineTransform transform,
                                  int clipX,
                                  int clipY,
                                  int clipW,
                                  int clipH) {
    super(owner);
    myTransform = transform;
    myClip = new int[]{clipX, clipY, clipW, clipH};
    if (co instanceof SunGraphics2D) {
      SunGraphics2D sg2d = (SunGraphics2D)co;
      SurfaceData sd = sg2d.getSurfaceData();
      if (sd instanceof BufImgSurfaceData) {
        myIsImage = true;
      }
      else {
        myIsImage = false;
      }
    }
    else {
      myIsImage = false;
    }
  }
}
