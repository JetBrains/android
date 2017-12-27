.class Lcom/example/SanAngeles/DemoRenderer;
.super Ljava/lang/Object;
.source "DemoActivity.java"

# interfaces
.implements Landroid/opengl/GLSurfaceView$Renderer;


# direct methods
.method constructor <init>()V
    .registers 1

    .prologue
    .line 114
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method private static native nativeDone()V
.end method

.method private static native nativeInit()V
.end method

.method private static native nativeRender()V
.end method

.method private static native nativeResize(II)V
.end method


# virtual methods
.method public onDrawFrame(Ljavax/microedition/khronos/opengles/GL10;)V
    .registers 2
    .param p1, "gl"    # Ljavax/microedition/khronos/opengles/GL10;

    .prologue
    .line 125
    invoke-static {}, Lcom/example/SanAngeles/DemoRenderer;->nativeRender()V

    .line 126
    return-void
.end method

.method public onSurfaceChanged(Ljavax/microedition/khronos/opengles/GL10;II)V
    .registers 4
    .param p1, "gl"    # Ljavax/microedition/khronos/opengles/GL10;
    .param p2, "w"    # I
    .param p3, "h"    # I

    .prologue
    .line 121
    invoke-static {p2, p3}, Lcom/example/SanAngeles/DemoRenderer;->nativeResize(II)V

    .line 122
    return-void
.end method

.method public onSurfaceCreated(Ljavax/microedition/khronos/opengles/GL10;Ljavax/microedition/khronos/egl/EGLConfig;)V
    .registers 3
    .param p1, "gl"    # Ljavax/microedition/khronos/opengles/GL10;
    .param p2, "config"    # Ljavax/microedition/khronos/egl/EGLConfig;

    .prologue
    .line 116
    invoke-static {}, Lcom/example/SanAngeles/DemoRenderer;->nativeInit()V

    .line 117
    return-void
.end method
