package qrcodelib;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.FrameLayout;

import com.google.android.gms.vision.barcode.Barcode;
import com.src.adux.votingapp.R;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import com.src.adux.votingapp.R;



public class MaterialBarcodeScanner {

    /**
     * Request codes
     */
    public static final int RC_HANDLE_CAMERA_PERM = 2;

    /**
     * Scanner modes
     */
    public static final int SCANNER_MODE_FREE = 1;
    public static final int SCANNER_MODE_CENTER = 2;

    protected final MaterialBarcodeScannerBuilder mMaterialBarcodeScannerBuilder;

    private FrameLayout mContentView; //Content frame for fragments

    private OnResultListener onResultListener;

    public MaterialBarcodeScanner(@NonNull MaterialBarcodeScannerBuilder materialBarcodeScannerBuilder) {
        this.mMaterialBarcodeScannerBuilder = materialBarcodeScannerBuilder;
    }

    public void setOnResultListener(OnResultListener onResultListener) {
        this.onResultListener = onResultListener;
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onBarcodeScannerResult(Barcode barcode) throws IOException {
        onResultListener.onResult(barcode);
        EventBus.getDefault().removeStickyEvent(barcode);
        EventBus.getDefault().unregister(this);
        mMaterialBarcodeScannerBuilder.clean();
    }

    /**
     * Interface definition for a callback to be invoked when a view is clicked.
     */
    public interface OnResultListener {
        void onResult(Barcode barcode) throws IOException;
    }

    /**
     * Start a scan for a barcode
     *
     * This opens a new activity with the parameters provided by the MaterialBarcodeScannerBuilder
     */
    public void startScan(){
        EventBus.getDefault().register(this);
        if(mMaterialBarcodeScannerBuilder.getActivity() == null){
            throw new RuntimeException("Could not start scan: Activity reference lost (please rebuild the MaterialBarcodeScanner before calling startScan)");
        }
        int mCameraPermission = ActivityCompat.checkSelfPermission(mMaterialBarcodeScannerBuilder.getActivity(), Manifest.permission.CAMERA);
        if (mCameraPermission != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
        }else{
            //Open activity
            EventBus.getDefault().postSticky(this);
            Intent intent = new Intent(mMaterialBarcodeScannerBuilder.getActivity(), MaterialBarcodeScannerActivity.class);
            mMaterialBarcodeScannerBuilder.getActivity().startActivity(intent);
        }
    }

    private void requestCameraPermission() {
        final String[] mPermissions = new String[]{Manifest.permission.CAMERA};
        if (!ActivityCompat.shouldShowRequestPermissionRationale(mMaterialBarcodeScannerBuilder.getActivity(), Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(mMaterialBarcodeScannerBuilder.getActivity(), mPermissions, RC_HANDLE_CAMERA_PERM);
            return;
        }
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(mMaterialBarcodeScannerBuilder.getActivity(), mPermissions, RC_HANDLE_CAMERA_PERM);
            }
        };
        Snackbar.make(mMaterialBarcodeScannerBuilder.mRootView, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(android.R.string.ok, listener)
                .show();
    }

    public MaterialBarcodeScannerBuilder getMaterialBarcodeScannerBuilder() {
        return mMaterialBarcodeScannerBuilder;
    }

}
