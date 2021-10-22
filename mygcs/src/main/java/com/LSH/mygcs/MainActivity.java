package com.LSH.mygcs;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputEditText;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.MAVLink.enums.MAV_CMD;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.util.FusedLocationSource;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.companion.solo.SoloAttributes;
import com.o3dr.services.android.lib.drone.companion.solo.SoloState;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Battery;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.GuidedState;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.mission.item.spatial.Waypoint;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;
import com.o3dr.services.android.lib.model.AbstractCommandListener;
import com.o3dr.services.android.lib.model.SimpleCommandListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class MainActivity extends AppCompatActivity implements DroneListener, TowerListener, LinkListener, OnMapReadyCallback {

    private static final String TAG = MainActivity.class.getSimpleName();
    private final int MY_PERMISSION_REQUEST_SMS = 1001;
    private NaverMap mNaverMap;
    private MapFragment mMapFragment;
    private LocationOverlay mLocationOverlay;
    private Spinner mModeSelector;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private FusedLocationSource locationSource;
    private Marker dronePosition = new Marker();
    private Drone drone;
    private int droneType = Type.TYPE_UNKNOWN;
    private ControlTower controlTower;
    private final Handler handler = new Handler();
    private double mAltitude = 3.0;
    private static final int DEFAULT_UDP_PORT = 14550;
    private static final int DEFAULT_USB_BAUD_RATE = 57600;
    private Marker targetAdress = new Marker();
    private Marker homeposition = new Marker();
    private Boolean goal = false;
    private Boolean returnHome = false;
    //private Spinner modeSelector;

    Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        locationSource =
                new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        FragmentManager fm = getSupportFragmentManager();
        mMapFragment = (MapFragment)fm.findFragmentById(R.id.map);
        if (mMapFragment == null) {
            mMapFragment = MapFragment.newInstance();
            fm.beginTransaction().add(R.id.map, mMapFragment).commit();
        }
        mMapFragment.getMapAsync(this);

        final Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);


        this.mModeSelector = (Spinner) findViewById(R.id.modeSelector);
        this.mModeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onFlightModeSelected(view);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        mainHandler = new Handler(getApplicationContext().getMainLooper());

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.SEND_SMS)){
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("info");
                builder.setMessage("This app won't work properly unless you grant SMS permission");

                builder.setNeutralButton("OK",new DialogInterface.OnClickListener(){
                   @Override
                   public void onClick(DialogInterface dialog, int which){
                       ActivityCompat.requestPermissions(MainActivity.this,new String[] {Manifest.permission.SEND_SMS}, MY_PERMISSION_REQUEST_SMS);
                   }
                });

                AlertDialog dialog = builder.create();
                dialog.show();
            }
            else {
                ActivityCompat.requestPermissions(this,new String[] {Manifest.permission.SEND_SMS}, MY_PERMISSION_REQUEST_SMS);
            }
        }
    }

    private void SendSMS(String number,String msg){
        SmsManager sms = SmsManager.getDefault();
        sms.sendTextMessage(number,null,msg,null,null);
    }

    public void ReturnHome(View view){
        returnHome = true;
        homeposition.setPosition(new LatLng(locationSource.getLastLocation().getLatitude(),locationSource.getLastLocation().getLongitude()));
        homeposition.setMap(mNaverMap);
        GotoPoint(new LatLong(homeposition.getPosition().latitude,homeposition.getPosition().longitude));
    }

    public void AltitudeBTNTap(View view){
        doBtnVisible("addAltitude","subAltitude","AltitudeOK");
    }

    public void AltitudeOKBTNTap(View view){
        doBtnInvisible("addAltitude","subAltitude","AltitudeOK");
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.mNaverMap = naverMap;
        UiSettings uiSettings = naverMap.getUiSettings();
        uiSettings.setZoomControlEnabled(false);
        uiSettings.setScaleBarEnabled(false);
        naverMap.setMapType(NaverMap.MapType.Satellite);
        //mLocationOverlay = naverMap.getLocationOverlay();
        //mLocationOverlay.setVisible(true);
        //mLocationOverlay.setIcon(OverlayImage.fromResource(R.drawable.location_overlay_icon));
        naverMap.setLocationSource(locationSource);
        naverMap.setLocationTrackingMode(LocationTrackingMode.NoFollow);
        marking();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,  @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(
                requestCode, permissions, grantResults)) {
            if (!locationSource.isActivated()) { // 권한 거부됨
                mNaverMap.setLocationTrackingMode(LocationTrackingMode.None);
            }
            return;
        }
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);
    }

    @Override
    public void onStart() {
        super.onStart();
        this.controlTower.connect(this);
        //updateVehicleModesForType(this.droneType);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (this.drone.isConnected()) {
            this.drone.disconnect();
            //updateConnectedButton(false);
        }

        this.controlTower.unregisterDrone(this.drone);
        this.controlTower.disconnect();
    }

    public void onBtnConnectTap(View view) {
        if (this.drone.isConnected()) {
            this.drone.disconnect();
        } else {
            ConnectionParameter connectionParams = ConnectionParameter.newUdpConnection(null);
            this.drone.connect(connectionParams);
        }

    }

    public void onFlightModeSelected(View view) {
        VehicleMode vehicleMode = (VehicleMode) this.mModeSelector.getSelectedItem();

        VehicleApi.getApi(this.drone).setVehicleMode(vehicleMode, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Vehicle mode change successful.");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Vehicle mode change failed: " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("Vehicle mode change timed out.");
            }
        });
    }

    @Override
    public void onDroneEvent(String event, Bundle extras) {
        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
/*
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
 */
                checkSoloState();

                break;

            case AttributeEvent.STATE_DISCONNECTED:
                alertUser("Drone Disconnected");
/*
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
 */
                break;

            case AttributeEvent.STATE_UPDATED:
            case AttributeEvent.STATE_ARMING:
                updateArmButton();
                break;

            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = this.drone.getAttribute(AttributeType.TYPE);
                if (newDroneType.getDroneType() != this.droneType) {
                    this.droneType = newDroneType.getDroneType();

                    updateVehicleModesForType(this.droneType);

                }
                break;

            case AttributeEvent.STATE_VEHICLE_MODE:
                updateVehicleMode();
                break;

            case AttributeEvent.SPEED_UPDATED:
                updateSpeed();
                break;

            case AttributeEvent.ALTITUDE_UPDATED:
                updateAltitude();
                break;

            case AttributeEvent.HOME_UPDATED:
//                updateDistanceFromHome();
                break;

            case AttributeEvent.BATTERY_UPDATED:
                updateBattery();

            case AttributeEvent.GPS_COUNT:
                countGPS();

            case AttributeEvent.ATTITUDE_UPDATED:
                updateAttitude();

            case AttributeEvent.GPS_POSITION:
                updateGps();

            default:
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    protected void updateArmButton() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        Button armButton = (Button) findViewById(R.id.ARMBtn);

        if (!this.drone.isConnected()) {
            armButton.setVisibility(View.INVISIBLE);
        } else {
            armButton.setVisibility(View.VISIBLE);
        }

        if (vehicleState.isFlying()) {
            // Land
            armButton.setText("LAND");
        } else if (vehicleState.isArmed()) {
            // Take off
            armButton.setText("TAKE OFF");
        } else if (vehicleState.isConnected()) {
            // Connected but not Armed
            armButton.setText("ARM");
        }
    }

    public void armButtonClick(View view) {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);

        if (vehicleState.isFlying()) {
            // Land
            VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LAND, new SimpleCommandListener() {
                @Override
                public void onSuccess() {
                    alertUser("land the vehicle.");
                }
                @Override
                public void onError(int executionError) {
                    alertUser("Unable to land the vehicle.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Unable to land the vehicle.");
                }
            });
        } else if (vehicleState.isArmed()) {
            // Take off
            ControlApi.getApi(this.drone).takeoff(mAltitude, new AbstractCommandListener() {

                @Override
                public void onSuccess() {
                    alertUser("Taking off...");
                }

                @Override
                public void onError(int i) {
                    alertUser("Unable to take off.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Unable to take off.");
                }
            });
        }
        else {
            // Connected but not Armed
            VehicleApi.getApi(this.drone).arm(true, false, new SimpleCommandListener() {
                @Override
                public void onError(int executionError) {
                    alertUser("Unable to arm vehicle.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Arming operation timed out.");
                }
            });
        }
    }

    protected void updateVehicleModesForType(int droneType) {
        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(droneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(this, android.R.layout.simple_spinner_item, vehicleModes);
        vehicleModeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.mModeSelector.setAdapter(vehicleModeArrayAdapter);
    }

    protected void updateVehicleMode() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        ArrayAdapter arrayAdapter = (ArrayAdapter) this.mModeSelector.getAdapter();
        this.mModeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }

    protected void updateSpeed() {
        TextView speedView = (TextView) findViewById(R.id.valueSpeed);
        Speed droneSpeed = this.drone.getAttribute(AttributeType.SPEED);
        speedView.setText(String.format("%3.1f", droneSpeed.getGroundSpeed()) + "m/s");
    }

    protected void updateAltitude() {
        TextView altitudeView = (TextView) findViewById(R.id.valueAltitude);
        Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        altitudeView.setText(String.format("%3.1f", droneAltitude.getAltitude()) + "m");
    }

    protected void updateBattery(){
        TextView batteryView = (TextView) findViewById(R.id.valueVolt);
        Battery droneBattery = this.drone.getAttribute(AttributeType.BATTERY);
        batteryView.setText(String.format("%3.1f", droneBattery.getBatteryVoltage()) + "V");
    }

    protected void countGPS(){
        TextView countGPS = (TextView) findViewById(R.id.valueSatellite);
        Gps gps = this.drone.getAttribute(AttributeType.GPS);
        countGPS.setText(String.format("%d", gps.getSatellitesCount()));
    }

    protected void updateAttitude(){
        TextView viewYaw = (TextView) findViewById(R.id.valueYAW);
        Attitude yaw = this.drone.getAttribute(AttributeType.ATTITUDE);
        int yaw_360 = (int) yaw.getYaw();
        if(yaw_360 < 0){
            yaw_360 = 360 - Math.abs(yaw_360);
            if(yaw_360 == 360) yaw_360 = 0;
        }
        viewYaw.setText(String.format("%d", yaw_360 ) + "deg");
        //mLocationOverlay.setBearing(yaw_360);
    }

    protected void updateGps(){
        dronePosition.setMap(null);
        Gps gps = this.drone.getAttribute(AttributeType.GPS);
        LatLong position = new LatLong(gps.getPosition());
        dronePosition.setPosition(new LatLng(position.getLatitude(),position.getLongitude()));
        dronePosition.setMap(mNaverMap);

        if(CheckGoal(new LatLng(position.getLatitude(),position.getLongitude()))&&goal){
            goal = false;
            if(!returnHome){
                final EditText num = (EditText)findViewById(R.id.numbertext);
                final EditText msg = (EditText)findViewById(R.id.SMStext);
                SendSMS(num.getText().toString(),msg.getText().toString());
            }
            VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LAND, new SimpleCommandListener() {
                @Override
                public void onSuccess() {
                    alertUser("land the vehicle.");
                }
                @Override
                public void onError(int executionError) {
                    alertUser("Unable to land the vehicle.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Unable to land the vehicle.");
                }
            });
        }
        //mLocationOverlay.setPosition(new LatLng(position.getLatitude(), position.getLongitude()));
    }

    private void checkSoloState() {
        final SoloState soloState = drone.getAttribute(SoloAttributes.SOLO_STATE);
        if (soloState == null){
            alertUser("Unable to retrieve the solo state.");
        }
        else {
            alertUser("Solo state is up to date.");
        }
    }

    @Override
    public void onDroneServiceInterrupted(String errorMsg) {

    }

    @Override
    public void onLinkStateUpdated(@NonNull LinkConnectionStatus connectionStatus) {

    }

    @Override
    public void onTowerConnected() {
        alertUser("DroneKit-Android Connected");
        this.controlTower.registerDrone(this.drone, this.handler);
        this.drone.registerDroneListener(this);
    }

    @Override
    public void onTowerDisconnected() {
        alertUser("DroneKit-Android Interrupted");
    }

    public void addAltitude(View view){
        if(mAltitude < 10.0) this.mAltitude = mAltitude + 0.5;
        Button altitude = (Button) findViewById(R.id.AltitudeBTN);
        altitude.setText(mAltitude + "M");
    }

    public void subAltitude(View view){
        if(mAltitude > 3.0) this.mAltitude = mAltitude - 0.5;
        Button altitude = (Button) findViewById(R.id.AltitudeBTN);
        altitude.setText(mAltitude + "M");
    }

    public void GuidedFly(View view){
        GotoPoint(new LatLong(targetAdress.getPosition().latitude,targetAdress.getPosition().longitude));
        returnHome = false;
    }

    protected void GotoPoint(final LatLong point) {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        if (vehicleState.isFlying()) {
            State vehiclemode = drone.getAttribute(AttributeType.STATE);
            VehicleMode dronemode = vehiclemode.getVehicleMode();
            goal = true;
            if(dronemode != VehicleMode.COPTER_GUIDED) {
                AlertDialog.Builder alt_bld = new AlertDialog.Builder(this);
                alt_bld.setMessage("확인하시면 가이드모드로 전환후 기체가 이동합니다.").setCancelable(false).setPositiveButton("확인", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // Action for 'Yes' Button
                        VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_GUIDED,
                                new AbstractCommandListener() {
                                    @Override
                                    public void onSuccess() {
                                        ControlApi.getApi(drone).goTo(point, true, null);
                                    }

                                    @Override
                                    public void onError(int i) {

                                    }

                                    @Override
                                    public void onTimeout() {

                                    }
                                });
                    }
                }).setNegativeButton("취소", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
                AlertDialog alert = alt_bld.create();
                // Title for AlertDialog
                alert.setTitle("가이드 비행");
                // Icon for AlertDialog
                //alert.setIcon(R.drawable.drone);
                alert.show();
            }
            else {
                ControlApi.getApi(drone).goTo(point, true, null);
            }
        }
    }

    public boolean CheckGoal(LatLng recentLatLng) {
        GuidedState guidedState = drone.getAttribute(AttributeType.GUIDED_STATE);
        LatLng target = new LatLng(guidedState.getCoordinate().getLatitude(),
                guidedState.getCoordinate().getLongitude());
        return target.distanceTo(recentLatLng) <= 1;
    }

    // Helper methods
    // ==========================================================

    protected void alertUser(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        Log.d(TAG, message);
    }

    private void runOnMainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }

    public void marking(){
        Button CLEARBtn = (Button) findViewById(R.id.MarkingBTN);
        final EditText text = (EditText)findViewById(R.id.adress);
        CLEARBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                geocoder geocode = new geocoder();
                geocode.execute(text.getText().toString());
            }
        });
    }

    protected void doBtnVisible(String...strings){
        for(int i =0; i<strings.length; i++){
            Button button = findViewById(getResources().getIdentifier(strings[i], "id", "com.LSH.mygcs"));
            button.setVisibility(View.VISIBLE);
        }
    }

    protected void doBtnInvisible(String...strings){
        for(int i =0; i<strings.length; i++){
            Button button = findViewById(getResources().getIdentifier(strings[i], "id", "com.LSH.mygcs"));
            button.setVisibility(View.INVISIBLE);
        }
    }

    private class geocoder extends AsyncTask<String, Void, JSONArray> {
        @Override
        protected JSONArray doInBackground(String... strings) {
            String str, receiveMsg;
            JSONArray jarray = null;
            try {
                URL url = new URL("https://naveropenapi.apigw.ntruss.com/map-geocode/v2/geocode?query="+strings[0]);
                HttpURLConnection http = (HttpURLConnection) url.openConnection();
                XmlPullParserFactory parserFactory = XmlPullParserFactory.newInstance();
                XmlPullParser parser = parserFactory.newPullParser();
                http.setRequestMethod("GET");
                http.setRequestProperty("Content-Type", "application/json");
                http.setRequestProperty("X-NCP-APIGW-API-KEY-ID", "f24i09guo5");
                http.setRequestProperty("X-NCP-APIGW-API-KEY", "g70kU7oYGHx5ftB7e9ICW7N3Ud3hu12h4BRlGeFL");
                http.connect();
                if (http.getResponseCode() == http.HTTP_OK) {
                    InputStreamReader tmp = new InputStreamReader(http.getInputStream(), "UTF-8");
                    BufferedReader reader = new BufferedReader(tmp);
                    StringBuffer buffer = new StringBuffer();
                    while ((str = reader.readLine()) != null) {
                        buffer.append(str);
                    }
                    receiveMsg = buffer.toString();
                    jarray = new JSONObject(receiveMsg).getJSONArray("addresses");
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (XmlPullParserException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return jarray;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        protected void onProgressUpdate(Integer... progress) {
            // 파일 다운로드 퍼센티지 표시 작업
        }

        @Override
        protected void onPostExecute(JSONArray jarray) {
            try {
                JSONObject latlng = jarray.getJSONObject(0);
                double latitude,longitude;
                longitude = latlng.getDouble("x");
                latitude = latlng.getDouble("y");
                targetAdress.setPosition(new LatLng(latitude,longitude));
                targetAdress.setMap(mNaverMap);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
