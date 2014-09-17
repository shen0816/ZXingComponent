package tw.com.mobilogics.zxing.component.simple;

import com.google.zxing.Result;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import tw.com.mobilogics.zxing.component.ZXingComponent;

public class MainActivity extends Activity implements ZXingComponent.ResultHandler{

  private static final String TAG = MainActivity.class.getName();

  private ZXingComponent mZXingComponent;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    mZXingComponent = (ZXingComponent)findViewById(R.id.view);
    mZXingComponent.setResultHandler(this);
    mZXingComponent.setAutoFocus(true);
  }

  @Override
  protected void onResume() {
    super.onResume();
    mZXingComponent.start();
  }

  @Override
  protected void onPause() {
    mZXingComponent.stop();
    super.onPause();
  }

  @Override
  public void handleResult(Result rawResult) {
    Log.e(TAG, "Result : " + rawResult.getText());
    mZXingComponent.stop();
    mZXingComponent.start();
  }
}
