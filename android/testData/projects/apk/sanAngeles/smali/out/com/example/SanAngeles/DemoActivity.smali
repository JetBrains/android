.class public Lcom/example/SanAngeles/DemoActivity;
.super Landroid/app/Activity;
.source "DemoActivity.java"


# instance fields
.field private mGLView:Landroid/opengl/GLSurfaceView;


# direct methods
.method static constructor <clinit>()V
    .registers 1

    .prologue
    .line 76
    const-string v0, "sanangeles"

    invoke-static {v0}, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V

    .line 77
    return-void
.end method

.method public constructor <init>()V
    .registers 1

    .prologue
    .line 53
    invoke-direct {p0}, Landroid/app/Activity;-><init>()V

    return-void
.end method


# virtual methods
.method protected onCreate(Landroid/os/Bundle;)V
    .registers 3
    .param p1, "savedInstanceState"    # Landroid/os/Bundle;

    .prologue
    .line 56
    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V

    .line 57
    new-instance v0, Lcom/example/SanAngeles/DemoGLSurfaceView;

    invoke-direct {v0, p0}, Lcom/example/SanAngeles/DemoGLSurfaceView;-><init>(Landroid/content/Context;)V

    iput-object v0, p0, Lcom/example/SanAngeles/DemoActivity;->mGLView:Landroid/opengl/GLSurfaceView;

    .line 58
    iget-object v0, p0, Lcom/example/SanAngeles/DemoActivity;->mGLView:Landroid/opengl/GLSurfaceView;

    invoke-virtual {p0, v0}, Lcom/example/SanAngeles/DemoActivity;->setContentView(Landroid/view/View;)V

    .line 59
    return-void
.end method

.method protected onPause()V
    .registers 2

    .prologue
    .line 63
    invoke-super {p0}, Landroid/app/Activity;->onPause()V

    .line 64
    iget-object v0, p0, Lcom/example/SanAngeles/DemoActivity;->mGLView:Landroid/opengl/GLSurfaceView;

    invoke-virtual {v0}, Landroid/opengl/GLSurfaceView;->onPause()V

    .line 65
    return-void
.end method

.method protected onResume()V
    .registers 2

    .prologue
    .line 69
    invoke-super {p0}, Landroid/app/Activity;->onResume()V

    .line 70
    iget-object v0, p0, Lcom/example/SanAngeles/DemoActivity;->mGLView:Landroid/opengl/GLSurfaceView;

    invoke-virtual {v0}, Landroid/opengl/GLSurfaceView;->onResume()V

    .line 71
    return-void
.end method
