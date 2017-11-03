.class Lcom/example/SanAngeles/DemoGLSurfaceView;
.super Landroid/opengl/GLSurfaceView;
.source "DemoActivity.java"


# instance fields
.field mRenderer:Lcom/example/SanAngeles/DemoRenderer;


# direct methods
.method public constructor <init>(Landroid/content/Context;)V
    .registers 3
    .param p1, "context"    # Landroid/content/Context;

    .prologue
    .line 82
    invoke-direct {p0, p1}, Landroid/opengl/GLSurfaceView;-><init>(Landroid/content/Context;)V

    .line 83
    new-instance v0, Lcom/example/SanAngeles/DemoRenderer;

    invoke-direct {v0}, Lcom/example/SanAngeles/DemoRenderer;-><init>()V

    iput-object v0, p0, Lcom/example/SanAngeles/DemoGLSurfaceView;->mRenderer:Lcom/example/SanAngeles/DemoRenderer;

    .line 84
    iget-object v0, p0, Lcom/example/SanAngeles/DemoGLSurfaceView;->mRenderer:Lcom/example/SanAngeles/DemoRenderer;

    invoke-virtual {p0, v0}, Lcom/example/SanAngeles/DemoGLSurfaceView;->setRenderer(Landroid/opengl/GLSurfaceView$Renderer;)V

    .line 85
    return-void
.end method

.method private static native nativePause()V
.end method

.method private static native nativeResume()V
.end method

.method private static native nativeTogglePauseResume()V
.end method


# virtual methods
.method public onPause()V
    .registers 1

    .prologue
    .line 96
    invoke-super {p0}, Landroid/opengl/GLSurfaceView;->onPause()V

    .line 97
    invoke-static {}, Lcom/example/SanAngeles/DemoGLSurfaceView;->nativePause()V

    .line 98
    return-void
.end method

.method public onResume()V
    .registers 1

    .prologue
    .line 102
    invoke-super {p0}, Landroid/opengl/GLSurfaceView;->onResume()V

    .line 103
    invoke-static {}, Lcom/example/SanAngeles/DemoGLSurfaceView;->nativeResume()V

    .line 104
    return-void
.end method

.method public onTouchEvent(Landroid/view/MotionEvent;)Z
    .registers 3
    .param p1, "event"    # Landroid/view/MotionEvent;

    .prologue
    .line 88
    invoke-virtual {p1}, Landroid/view/MotionEvent;->getAction()I

    move-result v0

    if-nez v0, :cond_9

    .line 89
    invoke-static {}, Lcom/example/SanAngeles/DemoGLSurfaceView;->nativeTogglePauseResume()V

    .line 91
    :cond_9
    const/4 v0, 0x1

    return v0
.end method
