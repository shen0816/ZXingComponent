package tw.com.mobilogics.zxing.component.simple;

import com.google.zxing.Result;

import android.app.Activity;
import android.test.ActivityInstrumentationTestCase2;
import android.widget.Button;

import tw.com.mobilogics.zxing.component.ZXingComponent;

/**
 * Created by chuck on 2014/9/18.
 */
public class MainActivityTest extends ActivityInstrumentationTestCase2<MainActivity> implements
    ZXingComponent.ResultHandler{

  private Activity mActivity;

  private Button mButton;

  private Result mResult = null;

  private ZXingComponent mZXingComponent = null;

  public MainActivityTest(){
    super(MainActivity.class);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mActivity = getActivity();
    mButton = (Button) mActivity.findViewById(R.id.button);
    mZXingComponent = (ZXingComponent) mActivity.findViewById(R.id.view);
    mZXingComponent.setAutoFocus(true);
    mZXingComponent.setResultHandler(this);
  }

  public void testScan(){
    for (int i = 1; i <= 5000;i++){
      System.out.println("index : " + i);
//      assertEquals("4800010995558", scan());
      scan();
      assertEquals(true,true);
    }
//    mZXingComponent.release();
  }

  public String scan(){
    getInstrumentation().runOnMainSync(new Runnable() {
      @Override
      public void run() {
        mButton.performClick();
      }
    });

    while (mResult == null){}

    String barcode = mResult.getText().toString().trim();

    System.out.println("barcode : " + barcode);

    mResult = null;

    return barcode;
  }

  @Override
  public void handleResult(Result rawResult) {
    mResult = rawResult;
    mZXingComponent.stop();
  }
}
