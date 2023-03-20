#include <fstream>
#include <jni.h>
#include <SkData.h>
#include <SkPictureRecorder.h>
#include <SkRRect.h>
#include <SkCanvas.h>
#include <SkPaint.h>
#include <SkRect.h>

/**
 * Tool to generate single.skp used in testing the layout inspector.
 */
extern "C" {

JNIEXPORT jbyteArray JNICALL
Java_com_android_tools_idea_tests_gui_layoutinspector_FakeBoxes_generateSingleBoxSkiaImage(
        JNIEnv *env, jobject instance) {
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

    canvas->drawAnnotation(skRect1, "/RenderNode(id=1, name='LinearLayout')", nullptr);

    sk_sp<SkPicture> picture = recorder.finishRecordingAsPicture();
    sk_sp<SkData> data = picture->serialize();
    jbyteArray array = env->NewByteArray(data->size());
    env->SetByteArrayRegion (array, 0, data->size(), (const jbyte *)data->data());
    return array;
}
}
