package qrcodelib;

import android.app.Activity;
import android.graphics.Color;
import android.hardware.Camera;
import android.support.annotation.NonNull;
import android.view.ViewGroup;

import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import com.src.adux.votingapp.R;

public class MaterialBarcodeScannerBuilder {

    protected Activity mActivity;
    protected ViewGroup mRootView;

    protected CameraSource mCameraSource;

    protected BarcodeDetector mBarcodeDetector;

    protected boolean mUsed = false; //used to check if a builder is only used

    protected int mFacing = CameraSource.CAMERA_FACING_BACK;
    protected boolean mAutoFocusEnabled = false;

    protected MaterialBarcodeScanner.OnResultListener onResultListener;

    protected int mTrackerColor = Color.parseColor("#F44336"); //Material Red 500

    protected boolean mBleepEnabled = false;

    protected boolean mFlashEnabledByDefault = false;

    protected int mBarcodeFormats = Barcode.ALL_FORMATS;

    protected String mText = "";

    protected int mScannerMode = MaterialBarcodeScanner.SCANNER_MODE_FREE;

    protected int mTrackerResourceID = R.drawable.material_barcode_square_512;
    protected int mTrackerDetectedResourceID = R.drawable.material_barcode_square_512_green;

    /**
     * Default constructor
     */
    public MaterialBarcodeScannerBuilder() {

    }

    /**
     * Called immediately after a barcode was scanned
     * @param onResultListener
     */
    public MaterialBarcodeScannerBuilder withResultListener(@NonNull MaterialBarcodeScanner.OnResultListener onResultListener){
        this.onResultListener = onResultListener;
        return this;
    }

    /**
     * Construct a MaterialBarcodeScannerBuilder by passing the activity to use for the generation
     *
     * @param activity current activity which will contain the drawer
     */
    public MaterialBarcodeScannerBuilder(@NonNull Activity activity) {
        this.mRootView = (ViewGroup) activity.findViewById(android.R.id.content);
        this.mActivity = activity;
    }

    /**
     * Sets the activity which will be used as the parent of the MaterialBarcodeScanner activity
     * @param activity current activity which will contain the MaterialBarcodeScanner
     */
    public MaterialBarcodeScannerBuilder withActivity(@NonNull Activity activity) {
        this.mRootView = (ViewGroup) activity.findViewById(android.R.id.content);
        this.mActivity = activity;
        return this;
    }

    /**
     * Makes the barcode scanner use the camera facing back
     */
    public MaterialBarcodeScannerBuilder withBackfacingCamera(){
        mFacing = CameraSource.CAMERA_FACING_BACK;
        return this;
    }

    /**
     * Makes the barcode scanner use camera facing front
     */
    public MaterialBarcodeScannerBuilder withFrontfacingCamera(){
        mFacing = CameraSource.CAMERA_FACING_FRONT;
        return this;
    }

    /**
     * Either CameraSource.CAMERA_FACING_FRONT or CameraSource.CAMERA_FACING_BACK
     * @param cameraFacing
     */
    public MaterialBarcodeScannerBuilder withCameraFacing(int cameraFacing){
        mFacing = cameraFacing;
        return this;
    }

    /**
     * Enables or disables auto focusing on the camera
     */
    public MaterialBarcodeScannerBuilder withEnableAutoFocus(boolean enabled){
        mAutoFocusEnabled = enabled;
        return this;
    }

    /**
     * Sets the tracker color used by the barcode scanner, By default this is Material Red 500 (#F44336).
     * @param color
     */
    public MaterialBarcodeScannerBuilder withTrackerColor(int color){
        mTrackerColor = color;
        return this;
    }

    /**
     * Enables or disables a bleep sound whenever a barcode is scanned
     */
    public MaterialBarcodeScannerBuilder withBleepEnabled(boolean enabled){
        mBleepEnabled = enabled;
        return this;
    }

    /**
     * Shows a text message at the top of the barcode scanner
     */
    public MaterialBarcodeScannerBuilder withText(String text){
        mText = text;
        return this;
    }

    /**
     * Shows a text message at the top of the barcode scanner
     */
    public MaterialBarcodeScannerBuilder withFlashLightEnabledByDefault(){
        mFlashEnabledByDefault = true;
        return this;
    }

    /**
     * Bit mask (containing values like QR_CODE and so on) that selects which formats this barcode detector should recognize.
     * @param barcodeFormats
     * @return
     */
    public MaterialBarcodeScannerBuilder withBarcodeFormats(int barcodeFormats){
        mBarcodeFormats = barcodeFormats;
        return this;
    }

    /**
     * Enables exclusive scanning on EAN-13, EAN-8, UPC-A, UPC-E, Code-39, Code-93, Code-128, ITF and Codabar barcodes.
     * @return
     */
    public MaterialBarcodeScannerBuilder withOnly2DScanning() {
        mBarcodeFormats = Barcode.EAN_13 | Barcode.EAN_8 | Barcode.UPC_A | Barcode.UPC_A | Barcode.UPC_E | Barcode.CODE_39 | Barcode.CODE_93 | Barcode.CODE_128 | Barcode.ITF | Barcode.CODABAR;
        return this;
    }

    /**
     * Enables exclusive scanning on QR Code, Data Matrix, PDF-417 and Aztec barcodes.
     * @return
     */
    public MaterialBarcodeScannerBuilder withOnly3DScanning(){
        mBarcodeFormats = Barcode.QR_CODE | Barcode.DATA_MATRIX | Barcode.PDF417 | Barcode.AZTEC;
        return this;
    }

    /**
     * Enables exclusive scanning on QR Codes, no other barcodes will be detected
     * @return
     */
    public MaterialBarcodeScannerBuilder withOnlyQRCodeScanning(){
        mBarcodeFormats = Barcode.QR_CODE;
        return this;
    }

    /**
     * Enables the default center tracker. This tracker is always visible and turns green when a barcode is found.\n
     * Please note that you can still scan a barcode outside the center tracker! This is purely a visual change.
     * @return
     */
    public MaterialBarcodeScannerBuilder withCenterTracker(){
        mScannerMode = MaterialBarcodeScanner.SCANNER_MODE_CENTER;
        return this;
    }

    /**
     * Enables the center tracker with a custom drawable resource. This tracker is always visible.\n
     * Please note that you can still scan a barcode outside the center tracker! This is purely a visual change.
     * @param trackerResourceId a drawable resource id
     * @param detectedTrackerResourceId a drawable resource id for the detected tracker state
     * @return
     */
    public MaterialBarcodeScannerBuilder withCenterTracker(int trackerResourceId, int detectedTrackerResourceId){
        mScannerMode = MaterialBarcodeScanner.SCANNER_MODE_CENTER;
        mTrackerResourceID = trackerResourceId;
        mTrackerDetectedResourceID = detectedTrackerResourceId;
        return this;
    }

    /**
     * Build a ready to use MaterialBarcodeScanner
     *
     * @return A ready to use MaterialBarcodeScanner
     */
    public MaterialBarcodeScanner build() {
        if (mUsed) {
            throw new RuntimeException("You must not reuse a MaterialBarcodeScanner builder");
        }
        if (mActivity == null) {
            throw new RuntimeException("Please pass an activity to the MaterialBarcodeScannerBuilder");
        }
        mUsed = true;
        buildMobileVisionBarcodeDetector();
        MaterialBarcodeScanner materialBarcodeScanner = new MaterialBarcodeScanner(this);
        materialBarcodeScanner.setOnResultListener(onResultListener);
        return materialBarcodeScanner;
    }

    /**
     * Build a barcode scanner using the Mobile Vision Barcode API
     */
    private void buildMobileVisionBarcodeDetector() {
        String focusMode = Camera.Parameters.FOCUS_MODE_FIXED;
        if(mAutoFocusEnabled){
            focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;
        }
        mBarcodeDetector = new BarcodeDetector.Builder(mActivity)
                .setBarcodeFormats(mBarcodeFormats)
                .build();
        mCameraSource = new CameraSource.Builder(mActivity, mBarcodeDetector)
                .setFacing(mFacing)
                .setFlashMode(mFlashEnabledByDefault ? Camera.Parameters.FLASH_MODE_TORCH : null)
                .setFocusMode(focusMode)
                .build();
    }

    /**
     * Get the activity associated with this builder
     * @return
     */
    public Activity getActivity() {
        return mActivity;
    }

    /**
     * Get the barcode detector associated with this builder
     * @return
     */
    public BarcodeDetector getBarcodeDetector() {
        return mBarcodeDetector;
    }

    /**
     * Get the camera source associated with this builder
     * @return
     */
    public CameraSource getCameraSource() {
        return mCameraSource;
    }


    /**
     * Get the tracker color associated with this builder
     * @return
     */
    public int getTrackerColor() {
        return mTrackerColor;
    }

    /**
     * Get the text associated with this builder
     * @return
     */
    public String getText() {
        return mText;
    }

    /**
     * Get the bleep enabled value associated with this builder
     * @return
     */
    public boolean isBleepEnabled() {
        return mBleepEnabled;
    }

    /**
     * Get the flash enabled by default value associated with this builder
     * @return
     */
    public boolean isFlashEnabledByDefault() {
        return mFlashEnabledByDefault;
    }

    /**
     * Get the tracker detected resource id value associated with this builder
     * @return
     */
    public int getTrackerDetectedResourceID() {
        return mTrackerDetectedResourceID;
    }

    /**
     * Get the tracker resource id value associated with this builder
     * @return
     */
    public int getTrackerResourceID() {
        return mTrackerResourceID;
    }

    /**
     * Get the scanner mode value associated with this builder
     * @return
     */
    public int getScannerMode() {
        return mScannerMode;
    }

    public void clean() {
        mActivity = null;
    }
}
