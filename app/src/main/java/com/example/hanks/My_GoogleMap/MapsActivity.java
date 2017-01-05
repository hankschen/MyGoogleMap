package com.example.hanks.My_GoogleMap;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends FragmentActivity {
    Spinner spMyType;
    CheckBox cbTraffic, cbMyLocationButton, cbMyLocationLayer, cbZoomControl,
            cbCompasss, cbScrollGesture, cbZoomGesture, cbRotateGesture, cbTileGesture;
    EditText etAddress;
    UiSettings uiSettings;
    LatLng point1, startPoint, endPoint;

    // 主執行緒的Handler(android.os)
    Handler handler;
    ArrayList<LatLng> points;

    LocationManager locationManager;

    String provider;

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        setUpMapIfNeeded();
        findviews();

        //主執行緒(UI Thread)的Handler(經紀人)
        //handleMessage(Message msg)
        //Subclasses must implement this to receive messages.子類別需實作handleMessage()
        //所以我們直接用暱名內部類別去實作handleMessage()
        //這裡是在主執行緒範圍,所以實作hanbleMessage當收到次執行緒的msg訊息時,就會開始更新UI
        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (mMap != null) {
                    PolylineOptions polylineOptions = new PolylineOptions()
                            .color(0x660000FF)
                            .width(15);
                    //將points的緯經點用for迴圈放到polylineOptions參考物件內
                    for (LatLng p : points) {
                        polylineOptions.add(p);
                    }
                    //劃線
                    mMap.addPolyline(polylineOptions);
                }
            }
        };

        //*****************************************************************************************
        //取的最後定位位置
        //1.取的LocationManager
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        //2.自創取最後位置的方法(code在程式最下面,步驟2-1)
        getLocation();
        //註冊監聽器,但此行要在getLocation()方法後面,因為provider是在getLocation方法內取得的,這樣才不會null)
        //回家試沒加try catch會不會有問題
        try {
            locationManager.requestLocationUpdates(provider, 10 * 1000, 10, locationListener);
            locationManager.requestLocationUpdates(provider, 10 * 1000, 10, locationListener);
        }catch (SecurityException e){

        }

    }

    void findviews() {
        spMyType = (Spinner) findViewById(R.id.spinner);
        spMyType.setOnItemSelectedListener(spMapTypeListener);

        cbTraffic = (CheckBox) findViewById(R.id.checkBox);
        cbTraffic.setOnClickListener(cbClickListener);
        cbMyLocationButton = (CheckBox) findViewById(R.id.checkBox2);
        cbMyLocationButton.setOnClickListener(cbClickListener);
        cbMyLocationLayer = (CheckBox) findViewById(R.id.checkBox3);
        cbMyLocationLayer.setOnClickListener(cbClickListener);
        cbZoomControl = (CheckBox) findViewById(R.id.checkBox4);
        cbZoomControl.setOnClickListener(cbClickListener);
        cbCompasss = (CheckBox) findViewById(R.id.checkBox5);
        cbCompasss.setOnClickListener(cbClickListener);
        cbScrollGesture = (CheckBox) findViewById(R.id.checkBox6);
        cbScrollGesture.setOnClickListener(cbClickListener);
        cbZoomGesture = (CheckBox) findViewById(R.id.checkBox7);
        cbZoomGesture.setOnClickListener(cbClickListener);
        cbRotateGesture = (CheckBox) findViewById(R.id.checkBox8);
        cbRotateGesture.setOnClickListener(cbClickListener);
        cbTileGesture = (CheckBox) findViewById(R.id.checkBox9);
        cbTileGesture.setOnClickListener(cbClickListener);
        etAddress = (EditText) findViewById(R.id.editText);
        etAddress.setOnLongClickListener(etAddressLongClick);
    }

    //longclick edittext 地址轉經緯度(在地圖最上方的edittext輸入中文地標或地址,並長按edittext,地圖就會移到你輸入的位置)
    View.OnLongClickListener etAddressLongClick = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            transferAddress();
            return true;
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                uiSettings = mMap.getUiSettings();
                mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
                    @Override
                    public void onMapLongClick(LatLng latLng) {
                        point1 = latLng;
                        displayDialog();
                    }
                });

                //自定mark的infowindows樣式
                mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
                    @Override
                    // 設定外框樣式,是自己畫圖片放上去
                    public View getInfoWindow(Marker marker) {
                        return null;
                    }

                    // 設定文字部分,文字是放在圖片上
                    @Override
                    public View getInfoContents(Marker marker) {
                        View myView = getLayoutInflater().inflate(R.layout.infotype, null);
                        TextView tvTitle = (TextView) myView.findViewById(R.id.textView);
                        TextView tvContent = (TextView) myView.findViewById(R.id.textView2);
                        tvTitle.setText(marker.getTitle());
                        tvContent.setText(marker.getSnippet());
                        return myView; //如果return null,就會用預設的樣式
                    }
                });
                setUpMap();
            }
        }
    }

    //在地圖任一位置長按顯示出Dialog,此Dialog功能有加標記,設起點,終點及清除所有標記
    void displayDialog() {
        String[] items = {"Add Mark", "Start point", "End point", "Clear all Mark"};
        new AlertDialog.Builder(this)
                .setTitle("標記或取消")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                mMap.addMarker(new MarkerOptions()
                                                .position(point1)
                                                .title("Title")
                                                .snippet("snippet")
                                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.flag_finish))
                                                .anchor(0f, 1f)
                                );
                                //mMap.setInfoWindowAdapter();
                                break;
                            case 1:
                                startPoint = point1;
                                break;
                            case 2:
                                endPoint = point1;
                                //由此位置加入次執行緒 new Thread,去執行取點的工作
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
//                                        String strJson=getJsonString();
//                                        String encodeString=getEncodeString(strJson);
//                                        ArrayList<LatLng> points=decodePolylines(encodeString);

                                        points = decodePolylines(getEncodeString(getJsonString()));
                                        //points=decodePolylines(getEncodeString(getJsonString()));
                                        //叫handler傳回空訊息,主要是跟主執行緒說任務完成的訊息
                                        handler.sendEmptyMessage(0);
                                    }
                                }).start();

                                break;
                            case 3:
                                mMap.clear();
                                break;
                        }
                    }
                })
                .show();
    }

    //map type設定spinner listener
    AdapterView.OnItemSelectedListener spMapTypeListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            switch (position) {
                case 0:
                    mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                    break;
                case 1:
                    mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                    break;
                case 2:
                    mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                    break;
                case 3:
                    mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
                    break;
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {

        }
    };

    //map左下角的設定功能CheckBox listener
    View.OnClickListener cbClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.checkBox:
                    mMap.setTrafficEnabled(cbTraffic.isChecked());
                    break;
                case R.id.checkBox2:
                    uiSettings.setMyLocationButtonEnabled(cbMyLocationButton.isChecked());
                    break;
                case R.id.checkBox3:
                    mMap.setMyLocationEnabled(cbMyLocationLayer.isChecked());
                    break;
                case R.id.checkBox4:
                    uiSettings.setZoomControlsEnabled(cbZoomControl.isChecked());
                    break;
                case R.id.checkBox5:
                    uiSettings.setCompassEnabled(cbCompasss.isChecked());
                    break;
                case R.id.checkBox6:
                    uiSettings.setScrollGesturesEnabled(cbScrollGesture.isChecked());
                    break;
                case R.id.checkBox7:
                    uiSettings.setZoomGesturesEnabled(cbZoomGesture.isChecked());
                    break;
                case R.id.checkBox8:
                    uiSettings.setRotateGesturesEnabled(cbRotateGesture.isChecked());
                    break;
                case R.id.checkBox9:
                    uiSettings.setTiltGesturesEnabled(cbTileGesture.isChecked());
                    break;
            }
        }
    };

    private void setUpMap() {

        //在map上畫circle
        LatLng latLng = new LatLng(24.142975, 120.689295);
        CircleOptions circleOptions = new CircleOptions()
                .center(latLng)
                .radius(100.0)
//                .fillColor(Color.argb(0x55,0,0x88,0))
                .fillColor(0x55008800)
                .strokeColor(Color.BLACK)
                .strokeWidth(3);
        mMap.addCircle(circleOptions);

        //在map上畫polygon多邊形
        mMap.addPolygon(new PolygonOptions().add(new LatLng(24.135925, 120.672773),
                        new LatLng(24.142426, 120.679597),
                        new LatLng(24.140155, 120.682171),
                        new LatLng(24.135181, 120.676936))
                        .fillColor(0x55886600)
                        .strokeColor(Color.BLUE)
                        .strokeWidth(5)
        );

        //在指定緯經度位置標上旗標
        mMap.addMarker(new MarkerOptions().position(new LatLng(24.148407, 120.688578))
                        .position(new LatLng(24.143288, 120.684274))
                        .title("Title")
                        .snippet("snippet")
                                //.icon(BitmapDescriptorFactory.fromResource(R.drawable.flag_finish))
                        .anchor(0f, 1f)
        );

        //一開啟此app馬上位移並縮放到指定的緯經度位置
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(24.138745, 120.683588), 16.0f));

        //Add Image on Map,在巨匠位置加圖片
        //注意圖片一開始要編輯好解析度,若太大map會將其縮小,浪費資源
        mMap.addGroundOverlay(new GroundOverlayOptions()
                        .anchor(0.5f,1.0f)
                        .bearing(0.0f) //圖片角度
                        .image(BitmapDescriptorFactory.fromResource(R.drawable.tower))
                        .position(new LatLng(24.138745, 120.683588),100.0f)
        );
    }

    /*==== 以下的code都是 "導航" 相關 ====*/

    //取網頁產生的一大串json code , 一行一行讀到StringBuilder內
    String getJsonString() {
        String strUrl = "https://maps.googleapis.com/maps/api/directions/json?origin=" + startPoint.latitude + "," + startPoint.longitude + "&destination=" + endPoint.latitude + "," + endPoint.longitude;
        StringBuilder sb = new StringBuilder();
        try {
            URL url = new URL(strUrl);
            URLConnection conn = url.openConnection();
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String readString = null;
            while ((readString = br.readLine()) != null) {
                sb.append(readString);
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException Ｅ) {
        }
        //Log.d("Googlemap",sb.toString());
        return sb.toString();
    }

    //取回網頁copy下來的json code編碼 (是經過"JSON Editor Online"編碼後的字串)
    String getEncodeString(String jsonString) {
        String encodeString = null;
        try {
            JSONObject jo = new JSONObject(jsonString);
            encodeString = jo.getJSONArray("routes")
                    .getJSONObject(0)
                    .getJSONObject("overview_polyline")
                    .getString("points");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        //Log.d("Googlemap",encodeString);
        return encodeString;
    }

    // 將上面網頁取出的json code用下面的解碼程式解碼
    private ArrayList<LatLng> decodePolylines(String poly) {
        ArrayList<LatLng> points = new ArrayList<>();
        int len = poly.length();
        int index = 0;
        int lat = 0;
        int lng = 0;
        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = poly.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;
            shift = 0;
            result = 0;
            do {
                b = poly.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;
            LatLng p = new LatLng((double) lat / 1E5, (double) lng / 1E5);
            points.add(p);
        }
        return points;
    }

    //GeoCoder(將位置移到你輸入的地標名稱或地址位置)
    void transferAddress() {
        Geocoder geocoder = new Geocoder(this);
        int maxResult = 1;
        try {
            List<Address> addresses = geocoder.getFromLocationName(etAddress.getText().toString(), maxResult);
            //用for迴圈放輸入的地址字串
            for (Address ads : addresses) {
                LatLng point = new LatLng(ads.getLatitude(), ads.getLongitude());
                mMap.addMarker(new MarkerOptions()
                                .position(point)
                                .title("Title")
                                .snippet("snippet")
                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.flag_finish))
                                .anchor(0f, 1f)
                );
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 16.0f));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {

        }
    }

    //2-1.自創取得最後位置的方法
    void getLocation() {
        //取得設定準則(標準)的物件(用以設定定位精準度跟省電程度...等)
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        //建最佳位置提供者provider
        //在室外GPS信號很好時,可用此方法
        //provider = locationManager.getBestProvider(criteria, true);
        //在室內無GPS信號時,用此方法
        provider = LocationManager.NETWORK_PROVIDER;
        //透過provider取得最後已知的位置
        Location location = null;
        //回家試沒加try catch會不會有問題
        try {
            location = locationManager.getLastKnownLocation(provider);
        }catch (SecurityException e){

        }

        if(mMap!=null){
            LatLng latLng=new LatLng(location.getLatitude(),location.getLongitude());
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng,16.0f));
            //要顯現走過的軌跡在此加入下面行,不要clear掉,就會邊走邊標位置
            //如果加clear就可一直更新你的位置
            //mMap.addMarker()
        }
    }

    LocationListener locationListener=new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if(mMap!=null){
                LatLng latLng=new LatLng(location.getLatitude(),location.getLongitude());
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng,16.0f));
                //跳Toast顯示位置的位置提供者,海拔,方位角,速度,緯經度..等資訊
                //先建StringBuilder來串要顯示的資訊字串
                StringBuilder sb=new StringBuilder();
                sb.append("Provider:"+provider+"\n")
                        .append("海抜:"+location.getAltitude()+"公尺\n")
                        .append("方位:"+location.getBearing()+"\n")
                        .append("速度"+location.getSpeed()+"公尺/秒\n")
                        .append("緯度"+location.getLatitude()+"\n")
                        .append("經度" + location.getLongitude()+"\n");
                Toast.makeText(MapsActivity.this, sb, Toast.LENGTH_LONG).show();
            }

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
    };
}
