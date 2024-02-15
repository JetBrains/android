#include <fstream>
#include <jni.h>
#include "include/core/SkData.h"
#include "include/core/SkPicture.h"
#include "include/core/SkPictureRecorder.h"
#include "include/core/SkRRect.h"
#include "include/core/SkSerialProcs.h"
#include "include/core/SkCanvas.h"
#include "include/core/SkPaint.h"
#include "include/core/SkRect.h"
#include "include/encode/SkPngEncoder.h"

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
    SkSerialProcs procs;
    procs.fImageProc = [](SkImage *img, void *) -> sk_sp<SkData> {
      return SkPngEncoder::Encode(nullptr, img, SkPngEncoder::Options{});
    };
    sk_sp<SkData> data = picture->serialize(&procs);
    jbyteArray array = env->NewByteArray(data->size());
    env->SetByteArrayRegion (array, 0, data->size(), (const jbyte *)data->data());
    return array;
}
}
