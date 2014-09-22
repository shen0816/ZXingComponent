package tw.com.mobilogics.zxing.component;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import com.commonsware.cwac.camera.CameraView;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import me.dm7.barcodescanner.core.DisplayUtils;
import me.dm7.barcodescanner.core.ViewFinderView;

/**
 * Created by chuck on 2014/9/15.
 */
public class ZXingComponent extends FrameLayout implements Camera.PreviewCallback {

  public static final List<BarcodeFormat> ALL_FORMATS = new ArrayList<BarcodeFormat>();

  private static final String TAG = ZXingComponent.class.getName();

  private ResultHandler mResultHandler = null;

  private CameraView mCameraView;

  private MultiFormatReader mMultiFormatReader;

  private ViewFinderView mViewFinderView;

  private Rect mFramingRectInPreview;

  private boolean isOpen = false;

  static {
    ALL_FORMATS.add(BarcodeFormat.UPC_A);
    ALL_FORMATS.add(BarcodeFormat.UPC_E);
    ALL_FORMATS.add(BarcodeFormat.EAN_13);
    ALL_FORMATS.add(BarcodeFormat.EAN_8);
    ALL_FORMATS.add(BarcodeFormat.RSS_14);
    ALL_FORMATS.add(BarcodeFormat.CODE_39);
    ALL_FORMATS.add(BarcodeFormat.CODE_93);
    ALL_FORMATS.add(BarcodeFormat.CODE_128);
    ALL_FORMATS.add(BarcodeFormat.ITF);
    ALL_FORMATS.add(BarcodeFormat.CODABAR);
    ALL_FORMATS.add(BarcodeFormat.QR_CODE);
    ALL_FORMATS.add(BarcodeFormat.DATA_MATRIX);
    ALL_FORMATS.add(BarcodeFormat.PDF_417);
  }

  public ZXingComponent(Context context) {
    super(context);
    initMultiFormatReader();
    setupLayout();
  }

  public ZXingComponent(Context context, AttributeSet attrs) {
    super(context, attrs);
    initMultiFormatReader();
    setupLayout();
  }

  public ZXingComponent(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initMultiFormatReader();
    setupLayout();
  }

  public void setAutoFocus(boolean isAutoFocus) {
    mCameraView.setAutoFocus(isAutoFocus);
  }

  public void setResultHandler(ResultHandler resultHandler) {
    mResultHandler = resultHandler;
  }

  private void setupLayout() {
    mViewFinderView = new ViewFinderView(getContext());
    mViewFinderView.setVisibility(View.INVISIBLE);
    mCameraView = new CameraView(getContext());
    mCameraView.setPreviewCallback(this);
    addView(mCameraView);
//    addView(mViewFinderView);
  }

  private void initMultiFormatReader() {
    Map<DecodeHintType, Object> hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
    hints.put(DecodeHintType.POSSIBLE_FORMATS, ALL_FORMATS);
    mMultiFormatReader = new MultiFormatReader();
    mMultiFormatReader.setHints(hints);
  }

  public void start() {
    if (isOpen) {
      return;
    }
    isOpen = true;
    mCameraView.onResume();
    mViewFinderView.setVisibility(View.VISIBLE);
  }

  public void stop() {
    if (!isOpen) {
      return;
    }
    isOpen = false;
    mCameraView.onPause();
    mViewFinderView.setVisibility(View.INVISIBLE);
  }

  public void autoFocus() {
    mCameraView.autoFocus();
  }

  @Override
  public void onPreviewFrame(byte[] bytes, Camera camera) {
    Camera.Parameters parameters = camera.getParameters();
    Camera.Size size = parameters.getPreviewSize();
    int width = size.width;
    int height = size.height;


    if (DisplayUtils.getScreenOrientation(getContext()) == Configuration.ORIENTATION_PORTRAIT) {
      byte[] rotatedData = new byte[bytes.length];
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          rotatedData[x * height + height - y - 1] = bytes[x + y * width];
        }
      }
//      int tmp = width;
//      width = height;
//      height = tmp;
      bytes = rotatedData;
    }

    Log.d(TAG,String.format("onPreviewFrame width : %d, height : %d",width, height));

    int centerX = width / 2;
    int centerY = height / 2;
    int viewCenterX = getWidth() / 2;
    int viewCenterY = getHeight() / 2;

    Result rawResult = null;
    PlanarYUVLuminanceSource source = null;
    source = new PlanarYUVLuminanceSource(bytes,
        width,
        height,
        centerX - viewCenterX,
        centerY - viewCenterY,
        width,
        mCameraView.getHeight(),
        false);

//    source = buildLuminanceSource(source.getMatrix(),
//        source.getWidth(),
//        source.getHeight());

    if (source != null) {
      BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
      try {
        rawResult = mMultiFormatReader.decodeWithState(bitmap);
      } catch (ReaderException re) {
        // continue
      } catch (NullPointerException npe) {
        // This is terrible
      } catch (ArrayIndexOutOfBoundsException aoe) {

      } finally {
        mMultiFormatReader.reset();
      }
    }

    if (rawResult != null && mResultHandler != null) {
      mResultHandler.handleResult(rawResult);
    }

    if (mCameraView.isAutoFocusAvailable()){
      camera.setOneShotPreviewCallback(this);
    }
  }

  public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height) {
    Rect rect = getFramingRectInPreview(width, height);
    if (rect == null) {
      return null;
    }
    // Go ahead and assume it's YUV rather than die.
    PlanarYUVLuminanceSource source = null;

    try {
      source = new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top,
          rect.width(), rect.height(), false);
    } catch (Exception e) {
    }

    return source;
  }

  public synchronized Rect getFramingRectInPreview(int width, int height) {
    if (mFramingRectInPreview == null) {
      Rect framingRect = mViewFinderView.getFramingRect();
      if (framingRect == null) {
        return null;
      }
      Rect rect = new Rect(framingRect);
      Point screenResolution = DisplayUtils.getScreenResolution(getContext());
      Point cameraResolution = new Point(width, height);

      if (cameraResolution == null || screenResolution == null) {
        // Called early, before init even finished
        return null;
      }

      rect.left = rect.left * cameraResolution.x / screenResolution.x;
      rect.right = rect.right * cameraResolution.x / screenResolution.x;
      rect.top = rect.top * cameraResolution.y / screenResolution.y;
      rect.bottom = rect.bottom * cameraResolution.y / screenResolution.y;

      mFramingRectInPreview = rect;
    }
    return mFramingRectInPreview;
  }

  public interface ResultHandler {

    public void handleResult(Result rawResult);
  }
}
