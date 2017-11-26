package com.matsuo.scott.robotcameraapp2;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.ImageReader;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.Collections;

import static android.content.Context.CAMERA_SERVICE;

/**
 * Created by Scott on 11/24/2017.
 */

public class RobotCamera {
    private static final String TAG = "RobotCamera";

    // Camera image parameters (device-specific)
    private static final int IMAGE_WIDTH  = 320;
    private static final int IMAGE_HEIGHT = 240;
    private static final int MAX_IMAGES   = 1;

    // Image result processor
    private ImageReader mImageReader;
    // Active camera device connection
    private CameraDevice mCameraDevice;
    // Active camera capture session
    private CameraCaptureSession mCaptureSession;

    private RobotCamera() {
    }

    private static class InstanceHolder {
        private static RobotCamera mCamera = new RobotCamera();
    }

    public static RobotCamera getInstance() {
        return InstanceHolder.mCamera;
    }

    // Initialize a new camera device connection
    public void initializeCamera(Context context,
                                 Handler backgroundHandler,
                                 ImageReader.OnImageAvailableListener imageAvailableListener) {

        // Discover the camera instance
        CameraManager manager = (CameraManager) context.getSystemService(CAMERA_SERVICE);
        String[] camIds = {};
        try {
            camIds = manager.getCameraIdList();
        } catch (CameraAccessException e) {
            Log.d(TAG, "Cam access exception getting IDs", e);
        }
        if (camIds.length < 1) {
            Log.d(TAG, "No cameras found");
            return;
        }
        String id = camIds[0];
        Log.d(TAG, "Using camera id " + id);

        // Initialize image processor
        mImageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT,
                ImageFormat.JPEG, MAX_IMAGES);
        mImageReader.setOnImageAvailableListener(imageAvailableListener, backgroundHandler);
        Log.d(TAG, "Initialized image processor, now attempting to open camera resource");

        // Open the camera resource
        try {
            manager.openCamera(id, mStateCallback, backgroundHandler);
        } catch (CameraAccessException cae) {
            Log.d(TAG, "Camera access exception", cae);
        }
    }

    // Callback handling devices state changes
    private final CameraDevice.StateCallback mStateCallback =
            new CameraDevice.StateCallback() {

                @Override
                public void onOpened(CameraDevice cameraDevice) {
                    Log.d(TAG, "Camera opened");
                    mCameraDevice = cameraDevice;
                }

                @Override
                public void onDisconnected(CameraDevice cameraDevice) {
                    Log.d(TAG, "Camera disconnected");
                    cameraDevice.close();
                }

                @Override
                public void onError( CameraDevice cameraDevice, int error) {
                    Log.d(TAG,"Camera Error");
                    cameraDevice.close();
                }

                @Override
                public void onClosed(CameraDevice cameraDevice) {
                    Log.d(TAG, "Closed camera, releasing");
                    mCameraDevice = null;
                }
            };

    // Close the camera resources
    public void shutDown() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
        }
    }

    public void takePicture() {
        if (mCameraDevice == null) {
            Log.w(TAG, "Cannot capture image. Camera not initialized.");
            return;
        }

        // Here, we create a CameraCaptureSession for capturing still images.
        try {
            Log.d(TAG, "Creating capture session");
            mCameraDevice.createCaptureSession(
                    Collections.singletonList(mImageReader.getSurface()),
                    mSessionCallback,
                    null);
        } catch (CameraAccessException cae) {
            Log.d(TAG, "access exception while preparing pic", cae);
        }
    }

    // Callback handling session state changes
    private CameraCaptureSession.StateCallback mSessionCallback =
            new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    // The camera is already closed
                    if (mCameraDevice == null) {
                        return;
                    }

                    // When the session is ready, we start capture.
                    mCaptureSession = cameraCaptureSession;
                    Log.d(TAG, "Triggering image capture");
                    triggerImageCapture();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Log.w(TAG, "Failed to configure camera");
                }
            };

    private void triggerImageCapture() {
        try {
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            Log.d(TAG, "Session initialized.");
            mCaptureSession.capture(captureBuilder.build(), mCaptureCallback, null);
        } catch (CameraAccessException cae) {
            Log.d(TAG, "camera capture exception");
        }
    }

    // Callback handling capture progress events
    private final CameraCaptureSession.CaptureCallback mCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(CameraCaptureSession session,
                                               CaptureRequest request,
                                               TotalCaptureResult result) {
                    if (session != null) {
                        session.close();
                        mCaptureSession = null;
                        Log.d(TAG, "CaptureSession closed");
                    }
                }

                @Override
                public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                    Log.d(TAG, "Capture Started");
                }

                @Override
                public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
                    Log.d(TAG, "Partial progress made");
                }
            };
}
