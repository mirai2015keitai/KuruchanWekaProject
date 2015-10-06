package com.example.b1013_000.wekapj;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.trees.J48;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

public class WekaActivity extends AppCompatActivity implements SensorEventListener, View.OnClickListener, LocationListener{

    //センサーのインスタンスの生成
    private SensorManager mSensorManager1;
    private SensorManager mSensorManager2;

    //各センサーのための変数
    private double acceX= 0;
    private double acceY= 0;
    private double acceZ = 0;
    private double gyroX = 0;
    private double gyroY = 0;
    private double gyroZ = 0;

    //Timerのための変数
    private Timer mainTimer = new Timer();
    private Handler mHandler1 = new Handler();
    private SetAccelerate setAccelerate;
    private PostStepDate postStepDate;
    private Timer subTimer = new Timer();


    //機械学習のための変数
    private Attribute ax;
    private Attribute ay;
    private Attribute az;
    private Attribute gx;
    private Attribute gy;
    private Attribute gz;
    private Attribute ave;
    private Attribute re;
    private Instances data;
    private Classifier classifier;

    //段差判定のための変数
    private double[] decision = new double[20];
    private int count = 0;

    //位置情報のための変数
    private LocationManager mLocationManager;
    private double str_lat, str_lng, end_lat, end_lng;
    private Handler mHandler2 = new Handler();


    //その他変数
    private Button bt, sou, res;
    private TextView tv, tv2, tv3;
    private int stepCounter = 0;
    private int moveCounter = 0;

    @Override
    protected void onCreate(Bundle savedInstance){
        super.onCreate(savedInstance);
        setContentView(R.layout.activity_weka);

        //センサとボタンのインスタンス生成
        mSensorManager1 = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensorManager2 = (SensorManager) getSystemService(SENSOR_SERVICE);
        mLocationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
        tv = (TextView) findViewById(R.id.resutlText);
        tv2 = (TextView) findViewById(R.id.stepcounter);
        tv3 = (TextView) findViewById(R.id.stopcounter);
        bt = (Button) findViewById(R.id.startbt);
        sou = (Button) findViewById(R.id.soushin);
        res = (Button) findViewById(R.id.reset);
        bt.setOnClickListener(this);
        sou.setOnClickListener(this);
        res.setOnClickListener(this);

        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_COARSE);
        criteria.setPowerRequirement(Criteria.POWER_LOW);
        String provider = mLocationManager.getBestProvider(criteria, true);
        mLocationManager.requestLocationUpdates(provider, 0, 0, this);

        //段差判定のための配列の初期化
        for(int i = 0; i < decision.length; i++){
            decision[i] = 0;
        }

        try {
            //データセットtestData2を用いる
            DataSource source = new DataSource(this.getAssets().open("data.arff"));
            data = source.getDataSet();
            if(data.classIndex() == -1){
                data.setClassIndex(data.numAttributes() - 1);
            }

            //分類器の生成
            classifier = new J48();
            classifier.buildClassifier(data);

            //分類器の評価
            Evaluation eval = new Evaluation(data);
            eval.evaluateModel(classifier, data);
            System.out.println("テストだよ" + eval.toSummaryString());

            //事例の属性を設定
            ax = new Attribute("ax", 0);
            ay = new Attribute("ay", 1);
            az = new Attribute("az", 2);
            gx = new Attribute("gx", 3);
            gy = new Attribute("gy", 4);
            gz = new Attribute("gz", 5);
            ave = new Attribute("ave", 6);
            re = new Attribute("res", 7);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume(){
        super.onResume();
        //加速度センサのリスターを登録
        mSensorManager1.registerListener(this,
                mSensorManager1.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_UI);
        // ジャイロセンサーのリスナーを登録
        mSensorManager2.registerListener(this,
                mSensorManager2.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            acceX = event.values[0];
            acceY = event.values[1];
            acceZ = event.values[2];
        }
        if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE){
            gyroX = event.values[0];
            gyroY = event.values[1];
            gyroZ = event.values[2];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.startbt:
                this.setAccelerate = new SetAccelerate();
                this.postStepDate = new PostStepDate();
                mainTimer.schedule(setAccelerate, 1000, 50);
                subTimer.schedule(postStepDate, 1000, 10000);
                Toast.makeText(WekaActivity.this, "スタート", Toast.LENGTH_SHORT).show();
                break;
            case R.id.soushin:
                showDB();
                break;
            case R.id.reset:
                stepCounter = 0;
                moveCounter = 0;
                break;
            default:
        }
    }

    //位置情報が変更されたときに動く
    @Override
    public void onLocationChanged(Location location) {
        end_lat = location.getLatitude();
        end_lng = location.getLongitude();
//        System.out.println(location.getLatitude());
//        System.out.println(location.getLongitude());
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    //路面情報を定期的に取るためのタイマータスク
    private class SetAccelerate extends TimerTask {
        @Override
        public void run() {
            mHandler1.post(new Runnable() {
                @Override
                public void run() {
                    //事例の値を設定
                    Instance instance = new Instance(7);
                    instance.setValue(ax, acceX);
                    instance.setValue(ay, acceY);
                    instance.setValue(az, acceZ);
                    instance.setValue(gx, gyroX);
                    instance.setValue(gy, gyroY);
                    instance.setValue(gz, gyroZ);
                    instance.setValue(ave, (acceX + acceY + acceZ + gyroX) / 4);
                    instance.setDataset(data);


                    //結果を得る
                    FastVector res = new FastVector(2);
                    double result = 0;
                    try {
                        result = classifier.classifyInstance(instance);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    //System.out.println("分類結果 : " + result);
                    if (result == 0.0) {
                        // Log.d("検知", "検知しました．");
                        tv.setText("止まっています");
                    } else if (result == 1.0) {
                        tv.setText("動いています");
                        moveCounter++;
                    } else if (result == 2.0) {
                        tv.setText("段差があります");
                        stepCounter++;
                    }
                    tv2.setText("" + stepCounter);
                    tv3.setText("" + moveCounter);
                }
            });
        }
    }

    //路面情報を定期的にサーバへ送るためのタイマータスク
    private class PostStepDate extends TimerTask{

        @Override
        public void run() {
            mHandler2.post(new Runnable() {
                @Override
                public void run() {
                    volleyPost();
                }
            });

        }
    }

    //volleyによるポスト
    private void volleyPost(){
        RequestQueue mQueue = Volley.newRequestQueue(getApplicationContext());
        String url = "http://mirai2015kuru.ddns.net/json_in.php";

        StringRequest postRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d("レスポンス", response);

                        stepCounter = 0;
                        moveCounter = 0;
                        str_lat = end_lat;
                        str_lng = end_lng;
                        Log.d("レスポンス", "1");
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d("エラーレスポンス", "error");
                    }
                }) {
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<String, String>();
                params.put("st_lat", String.valueOf(str_lat));
                params.put("st_lng", String.valueOf(str_lng));
                params.put("en_lat", String.valueOf(end_lat));
                params.put("en_lng", String.valueOf(end_lng));
                params.put("no_dump", String.valueOf(moveCounter));
                params.put("high_dump", String.valueOf(stepCounter));
                Log.d("レスポンス", "2");
                return params;
            }
        };
        mQueue.add(postRequest);
    }

    private void showDB(){
        RequestQueue mQueue = Volley.newRequestQueue(getApplicationContext());
        String url = "http://mirai2015kuru.ddns.net/tlow_select.php";

        StringRequest postRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d("レスポンス", response);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d("エラーレスポンス", "error");
                    }
                });
        mQueue.add(postRequest);
    }

}
