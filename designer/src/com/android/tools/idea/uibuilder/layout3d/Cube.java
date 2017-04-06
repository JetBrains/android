/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.layout3d;

import java.text.DecimalFormat;

/**
 * is a simple test cube data set useful to driving the rendering
 */
public class Cube extends TriData {

    @Override
    public String toString() {
        return "CUBE[" + fs(myVert, 0, 3) + "][" + fs(myVert, 18, 3) + "]";
    }

    private static String fs(float []f,int off,int n){
        DecimalFormat df =new DecimalFormat("##0.000");
        String ret = "";
        for (int i = off; i < off+n; i++) {
            String s = "       "+df.format(f[i]);

            if (i!=off) {
                ret+=",";
            }
            ret+= s.substring(s.length()-8);

        }
        return ret;
    }

    public Cube() {
        myVert = new float[]{
                -1.f, -1.f, -1.f,
                1.f, -1.f, -1.f,
                1.f, 1.f, -1.f,
                -1.f, 1.f, -1.f,
                -1.f, -1.f, 1.f,
                1.f ,-1.f, 1.f,
                1.f, 1.f, 1.f,
                -1.f, 1.f, 1.f,
        };
        for (int i = 0; i < myVert.length; i++) {
            myVert[i]+=1;
          if (i%3==2) {
              myVert[i]/=2;
          }
        }
        myIndex = new int[]{
                2, 1, 0,
                0, 3, 2,
                7, 4, 5,
                5, 6, 7,
                1, 2, 6,
                6, 5, 1,
                4, 7, 3,
                3, 0, 4,
                2, 3, 7,
                7, 6, 2,
                0, 1, 5,
                5, 4, 0
        };
        for (int i = 0; i < myIndex.length; i++) {
            myIndex[i]*=3;
        }
    }
}
