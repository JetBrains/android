/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.viewer;

import com.android.tools.idea.editors.gfxtrace.viewer.gl.Shader;
import com.intellij.openapi.diagnostic.Logger;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

public class Viewer implements GLEventListener {
  private static final Logger LOG = Logger.getInstance(Viewer.class);

  private final CameraModel myCamera;
  private Shaders myShaders;
  private Renderable myRenderable;
  private Renderable newRenderable;
  private Shading myShading = Shading.LIT;
  private Winding myWinding = Winding.CCW;
  private Culling myCulling = Culling.OFF;

  public Viewer(CameraModel camera) {
    this.myCamera = camera;
  }

  public void addMouseListeners(Component component) {
    MouseHandler handler = new MouseHandler(component);
    component.addMouseListener(handler);
    component.addMouseMotionListener(handler);
    component.addMouseWheelListener(handler);
  }

  public void setRenderable(Renderable renderable) {
    if (renderable != myRenderable) {
      newRenderable = renderable;
    }
    else {
      newRenderable = null;
    }
  }

  public Shading getShading() {
    return myShading;
  }

  public Culling getCulling() {
    return myCulling;
  }

  public Winding getWinding() {
    return myWinding;
  }

  public void setShading(Shading shading) {
    myShading = shading;
  }

  public void setCulling(Culling culling) {
    myCulling = culling;
  }

  public void setWinding(Winding winding) {
    myWinding = winding;
  }

  @Override
  public void init(GLAutoDrawable drawable) {
    GL2ES2 gl = drawable.getGL().getGL2ES2();
    LOG.debug("GL Version:   " + gl.glGetString(GL.GL_VERSION));
    LOG.debug("GLSL Version: " + gl.glGetString(GL2ES2.GL_SHADING_LANGUAGE_VERSION));

    myShaders = Shaders.init(gl);
    if (myRenderable != null) {
      myRenderable.init(gl);
    }

    gl.getGL2ES1().glPointSize(5);
    gl.glEnable(GL.GL_DEPTH_TEST);
    gl.glClearColor(.8f, .8f, .8f, 1);
    gl.getGL2GL3().glPointSize(4);
  }

  @Override
  public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
    GL2ES2 gl = drawable.getGL().getGL2ES2();
    gl.glViewport(x, y, width, height);
    myCamera.updateViewport(width, height);
  }

  @Override
  public void display(GLAutoDrawable drawable) {
    GL2ES2 gl = drawable.getGL().getGL2ES2();
    gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

    if (newRenderable != null) {
      if (myRenderable != null) {
        myRenderable.dispose(gl);
      }
      myRenderable = newRenderable;
      myRenderable.init(gl);
      newRenderable = null;
    }

    if (myRenderable != null) {
      Renderable.State state = myShading.getState(myShaders, myWinding.invertNormals);
      myCulling.apply(gl);
      myWinding.apply(gl, state.shader);

      state.transform.setProjection(myCamera.getProjection());
      state.transform.setModelView(myCamera.getViewTransform());
      myRenderable.render(gl, state);
    }
  }

  @Override
  public void dispose(GLAutoDrawable drawable) {
    GL2ES2 gl = drawable.getGL().getGL2ES2();
    myShaders.delete();
    if (myRenderable != null) {
      myRenderable.dispose(gl);
    }
  }

  public enum Culling {
    OFF() {
      @Override
      public void apply(GL2ES2 gl) {
        gl.glDisable(GL.GL_CULL_FACE);
      }
    },
    ON() {
      @Override
      public void apply(GL2ES2 gl) {
        gl.glEnable(GL.GL_CULL_FACE);
      }
    };

    public abstract void apply(GL2ES2 gl);
  }

  public enum Winding {
    CCW(false) {
      @Override
      public void apply(GL2ES2 gl, Shader shader) {
        gl.glFrontFace(GL.GL_CCW);
      }
    },
    CW(true) {
      @Override
      public void apply(GL2ES2 gl, Shader shader) {
        gl.glFrontFace(GL.GL_CW);
      }
    };

    public final boolean invertNormals;

    Winding(boolean invertNormals) {
      this.invertNormals = invertNormals;
    }

    public abstract void apply(GL2ES2 gl, Shader shader);
  }

  public enum Shading {
    LIT() {
      @Override
      public Renderable.State getState(Shaders shaders, boolean invertNormals) {
        Renderable.State state = new Renderable.State(shaders.myLitShader, invertNormals);
        state.shader.bind();
        state.shader.setUniform("uLightDir", new float[] {
          0,      -0.707f, -0.707f,
          0,       0.707f, -0.707f,
         -0.707f,  0,       0.707f,
          0.707f,  0,       0.707f
        });
        state.shader.setUniform("uLightColor", new float[] {
          0.2f, 0.2f, 0.2f,
          0.4f, 0.4f, 0.4f,
          0.5f, 0.5f, 0.5f,
          1.0f, 1.0f, 1.0f
        });
        state.shader.setUniform("uLightSpecColor", new float[] {
          0.0f, 0.0f, 0.0f,
          0.5f, 0.5f, 0.5f,
          0.5f, 0.5f, 0.5f,
          1.0f, 1.0f, 1.0f
        });
        state.shader.setUniform("uLightSize", new float[] {
          0f, 0.05f, 0.05f, 0f
        });
        state.shader.setUniform("uDiffuseColor", new float[] { 0.37868f, 0.56050f, 0.03703f }); // #A4C639 in linear.
        state.shader.setUniform("uSpecularColor", new float[] { 0.3f, 0.3f, 0.3f });
        state.shader.setUniform("uRoughness", 0.25f);
        return state;
      }
    },
    FLAT() {
      @Override
      public Renderable.State getState(Shaders shaders, boolean invertNormals) {
        Renderable.State state = new Renderable.State(shaders.myFlatShader, false);
        state.shader.bind();
        state.shader.setUniform("uDiffuseColor", new float[] { 0.640625f, 0.7734375f, 0.22265625f }); // #A4C639 in sRGB.
        return state;
      }
    },
    NORMALS() {
      @Override
      public Renderable.State getState(Shaders shaders, boolean invertNormals) {
        Renderable.State state = new Renderable.State(shaders.myNormalShader, invertNormals);
        state.shader.bind();
        return state;
      }
    };

    public abstract Renderable.State getState(Shaders shaders, boolean invertNormals);
  }

  private class MouseHandler extends MouseAdapter {
    private final Component component;
    private int lastX, lastY;

    public MouseHandler(Component component) {
      this.component = component;
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
      myCamera.onZoom(e.getWheelRotation() / 6.0f);
      component.repaint();
    }

    @Override
    public void mousePressed(MouseEvent e) {
      lastX = e.getX();
      lastY = e.getY();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      int x = e.getX();
      int y = e.getY();
      myCamera.onDrag(x - lastX, y - lastY);
      component.repaint();
      lastX = x;
      lastY = y;
    }
  }

  private static class Shaders {
    public final Shader myLitShader;
    public final Shader myFlatShader;
    public final Shader myNormalShader;

    private Shaders(Shader litShader, Shader flatShader, Shader normalShader) {
      myLitShader = litShader;
      myFlatShader = flatShader;
      myNormalShader = normalShader;
    }

    public static Shaders init(GL2ES2 gl) {
      return new Shaders(
        ShaderSource.loadShader(gl, "lit"),
        ShaderSource.loadShader(gl, "flat"),
        ShaderSource.loadShader(gl, "normals"));
    }

    public void delete() {
      myLitShader.delete();
      myFlatShader.delete();
      myNormalShader.delete();
    }
  }
}
