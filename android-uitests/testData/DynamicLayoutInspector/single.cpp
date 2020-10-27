#include <fstream>
#include <SkPictureRecorder.h>
#include <SkRRect.h>
#include <SkCanvas.h>
#include <SkPaint.h>
#include <SkRect.h>

/**
 * Tool to generate single.skp used in testing the layout inspector.
 * Build and run through CLion--it's just for generating test data, and so needn't be built by bazel.
 */
 //TODO: Make this file build the single.skp during the run of the test.
int main() {
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
    std::ofstream f("single.skp", std::ofstream::out);
    f.write(static_cast<const char *>(data->data()), data->size());
    f.close();
}
