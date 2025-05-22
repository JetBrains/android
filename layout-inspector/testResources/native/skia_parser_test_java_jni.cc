#include "include/core/SkData.h"
#include "include/core/SkPictureRecorder.h"
#include "include/core/SkRRect.h"
#include "include/core/SkCanvas.h"
#include "include/core/SkPaint.h"
#include "include/core/SkRect.h"
#include <grpc++/grpc++.h>
#include <jni.h>
#include "server.h"

extern "C" {

/**
 * Start the skia grpc server in this process, listening on the given port.
 * Be sure to send the Shutdown message to it when you're done.
 */
JNIEXPORT void JNICALL
Java_com_android_tools_idea_layoutinspector_skia_SkiaParserIntegrationTest_runServer(
    JNIEnv *env, jclass c, jint port) {
	SkiaParserServiceImpl::RunServer(std::to_string(port).c_str());
}

/**
 * Tool to generate boxes.skp used in testing the layout inspector.
 */
JNIEXPORT jbyteArray JNICALL
Java_com_android_tools_idea_layoutinspector_skia_SkiaParserIntegrationTest_generateBoxes(
        JNIEnv *env, jclass c) {
    SkPictureRecorder recorder;
    SkPaint paint;
    paint.setStyle(SkPaint::kFill_Style);
    paint.setAntiAlias(true);
    paint.setStrokeWidth(0);

    SkCanvas* canvas = recorder.beginRecording({0, 0, 1000, 2000});
    const SkRect &skRect1 = SkRect::MakeXYWH(0, 0, 1000, 2000);
    canvas->drawAnnotation(skRect1, "RenderNode(id=1, name='LinearLayout')", nullptr);
    paint.setColor(SK_ColorYELLOW);
    canvas->drawRect(skRect1, paint);

    const SkRect &skRect2 = SkRect::MakeXYWH(0, 0, 500, 1000);
    canvas->drawAnnotation(skRect2, "RenderNode(id=2, name='FrameLayout')", nullptr);
    canvas->save();
    canvas->translate(100, 100);
    paint.setColor(SK_ColorBLUE);
    canvas->drawRect(skRect2, paint);

    const SkRect &skRect3 = SkRect::MakeXYWH(0, 0, 200, 500);
    canvas->drawAnnotation(skRect3, "RenderNode(id=3, name='AppCompatButton')", nullptr);
    canvas->save();
    canvas->translate(200, 200);
    paint.setColor(SK_ColorBLACK);
    canvas->drawRect(skRect3, paint);
    canvas->restore();
    canvas->drawAnnotation(skRect3, "/RenderNode(id=3, name='AppCompatButton')", nullptr);

    canvas->restore();
    canvas->drawAnnotation(skRect2, "/RenderNode(id=2, name='FrameLayout')", nullptr);

    const SkRect &skRect4 = SkRect::MakeXYWH(0, 0, 400, 500);
    canvas->drawAnnotation(skRect4, "RenderNode(id=4, name='Button')", nullptr);
    canvas->save();
    canvas->translate(300, 1200);
    paint.setColor(SK_ColorRED);
    canvas->drawRect(skRect4, paint);
    canvas->restore();
    canvas->drawAnnotation(skRect4, "/RenderNode(id=4, name='Button')", nullptr);

    canvas->drawAnnotation(skRect1, "/RenderNode(id=1, name='LinearLayout')", nullptr);

    sk_sp<SkPicture> picture = recorder.finishRecordingAsPicture();
    sk_sp<SkData> data = picture->serialize();
    jbyteArray array = env->NewByteArray(data->size());
    env->SetByteArrayRegion (array, 0, data->size(), (const jbyte *)data->data());
    return array;
}
}
