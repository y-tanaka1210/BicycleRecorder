package jp.ac.titech.itpro.sdl.bicyclerecorder;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Date;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.provider.MediaStore;
import android.util.Log;
import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.hardware.SensorEventListener;
import android.widget.TextView;
import android.widget.Button;

public class MainActivity extends Activity implements SensorEventListener, OnClickListener{

    //センサの値の取得と表示
    private SensorManager manager;
    private TextView values;
    //プレビュー画面
    private SurfaceView mySurfaceView;
    private Camera myCamera;
    //for debug 撮影の連続判定防止フラグ
    private boolean picflg = false;
    private int picnum = 0;
    private boolean led = false;

    //カメラのイベント処理
    private PictureCallback mPictureListener =
            new PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera){

                    //データの生成
                    Bitmap tmp_bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                    int width = tmp_bitmap.getWidth();
                    int height = tmp_bitmap.getHeight();

                    //画像の回転(portraitに対応する場合に追加)
                    Matrix matrix = new Matrix();
                    matrix.setRotate(0);

                    Bitmap bitmap = Bitmap.createBitmap(tmp_bitmap, 0, 0, width, height, matrix, true);

                    //ギャラリーに画像を保存
                    String filename = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.JAPAN).format(new Date()) +
                            "-" + picnum + ".jpg";
                    MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, filename, null);
                    //カメラの再開
                    picflg = false;
                    picnum++;
                    myCamera.startPreview();
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        values = (TextView)findViewById(R.id.myTextView);
        manager = (SensorManager)getSystemService(SENSOR_SERVICE);

        mySurfaceView = (SurfaceView)findViewById(R.id.mySurfaceView);
        SurfaceHolder holder = mySurfaceView.getHolder();
        holder.addCallback(callback);

        //Buttonの配置
        Button led_button = (Button)findViewById(R.id.Button01);
        led_button.setOnClickListener(this);
    }

    @Override
    public void onClick(View v){
        Log.d("onClick", "LEDswitch");
        ledSwitch(!led);
        led = !led;
    }

    @Override
    protected void onStop(){
        super.onStop();
        manager.unregisterListener(this);
    }

    @Override
    protected void onResume(){
        super.onResume();

        manager.registerListener(this, manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_UI);
        manager.registerListener(this, manager.getDefaultSensor(Sensor.TYPE_LIGHT),
                SensorManager.SENSOR_DELAY_UI);

    }

    @Override
    public void onPause(){
        super.onPause();
        manager.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy){

    }

    @Override
    public void onSensorChanged(SensorEvent event){

        if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE){
        //複数行に渡ってTextViewに表示すると実機動作時にエラー吐いて落ちる
//            String str = "ジャイロセンサ"
//                   + "X軸：" + event.values[SensorManager.DATA_X];
//                    + "Y軸：" + event.values[SensorManager.DATA_Y]
//                    + "Z軸：" + event.values[SensorManager.DATA_Z];
//            values.setText(str);

            if(event.values[SensorManager.DATA_X] >= 3){
                Log.d("OnSensorChanged", "threshold value!");
                if(picflg == false) {
                    picflg = true;
                    Log.d("onSensorChanged", "taking picture");
                    myCamera.takePicture(null, null, mPictureListener);
                }
            }
        }

        else if(event.sensor.getType() == Sensor.TYPE_LIGHT){
//            Camera.Parameters param = myCamera.getParameters();

            if(event.values[0] < 120 && (led == false)){
                Log.d("OnSensorChanged", "LED to Torch mode");
                led = true;
                ledSwitch(true);
            }
//            else if(event.values[0] >= 120 && (led == true)){
//                Log.d("OnSensorChanged", "LED to Torch mode off");
//                led = false;
//                ledSwitch(false);
//            }
        }

    }

    //LEDのON・OFF切替
    public void ledSwitch(boolean torch){
        Camera.Parameters param = myCamera.getParameters();
        if(torch) {
            Log.d("ledSwitch", "flash_mode_torch");
            param.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        }
        else if(!torch){
            Log.d("ledSwitch", "flash_mode_off");
            param.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        }
        myCamera.setParameters(param);
    }

    private SurfaceHolder.Callback callback = new SurfaceHolder.Callback(){
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder){
            myCamera = Camera.open();

            try {
                myCamera.setPreviewDisplay(surfaceHolder);
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height){
            //最適サイズの取得
            Camera.Parameters params = myCamera.getParameters();
            List<Camera.Size> sizes = params.getSupportedPreviewSizes();
            Camera.Size optimalSize = getOptimalPreviewSize(sizes, width, height);
            params.setPreviewSize(optimalSize.width,optimalSize.height);
            myCamera.setParameters(params);

            //手ブレ補正機能の使用
            boolean isSuppoted = params.isVideoStabilizationSupported();
            if(isSuppoted) {
                Log.d("surfaceChanged", "VideoStabilization is Supported.");
                params.setVideoStabilization(true);
            }
            else {
                Log.d("surfaceChanged", "VideoStabilization not Supported.");
            }

            //プレビュー開始
            myCamera.startPreview();
        }
        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder){
            myCamera.release();
            myCamera = null;
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h){
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio=(double)h / w;

        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }
}
