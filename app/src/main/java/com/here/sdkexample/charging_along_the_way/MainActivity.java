package com.here.sdkexample.charging_along_the_way;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.here.sdk.core.Color;
import com.here.sdk.core.GeoCoordinates;
import com.here.sdk.core.GeoOrientationUpdate;
import com.here.sdk.core.GeoPolyline;
import com.here.sdk.core.errors.InstantiationErrorException;
import com.here.sdk.mapview.MapImage;
import com.here.sdk.mapview.MapImageFactory;
import com.here.sdk.mapview.MapMarker;
import com.here.sdk.mapview.MapMeasure;
import com.here.sdk.mapview.MapPolyline;
import com.here.sdk.mapview.MapScheme;
import com.here.sdk.mapview.MapView;
import com.here.sdk.routing.ChargingConnectorType;
import com.here.sdk.routing.EVCarOptions;
import com.here.sdk.routing.OptimizationMode;
import com.here.sdk.routing.RefreshRouteOptions;
import com.here.sdk.routing.Route;
import com.here.sdk.routing.RouteHandle;
import com.here.sdk.routing.RoutePlace;
import com.here.sdk.routing.RoutingEngine;
import com.here.sdk.routing.Section;
import com.here.time.Duration;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class MainActivity extends AppCompatActivity {
    ImageButton importRoute;
    Button cancelButton;
    Button sendButton;

    EditText apiKeyEdit;
    EditText departureTimeEdit;
    EditText arrivalTimeEdit;
    EditText routingModeEdit;
    EditText alternativesEdit;
    EditText originEdit;
    EditText destinationEdit;
    EditText transportModeEdit;
    EditText freeFlowSpeedTableEdit;
    EditText trafficSpeedTableEdit;
    EditText ascentEdit;
    EditText descentEdit;
    EditText auxiliaryConsumptionEdit;
    EditText initialChargeEdit;
    EditText maxChargeEdit;
    EditText chargingCurveEdit;
    EditText maxChargingVoltageEdit;
    EditText maxChargingCurrentEdit;
    EditText maxChargeAfterChargingStationEdit;
    EditText minChargeAtChargingStationEdit;
    EditText minChargeAtDestinationEdit;
    EditText chargingSetupDurationEdit;
    EditText connectorTypesEdit;
    EditText makeReachableEdit;
    EditText preferredBrandsEdit;
    EditText returnEdit;

    OkHttpClient httpClient = new OkHttpClient();
    MapView mapView;

    RoutingEngine routingEngine;

    List<MapMarker> mapMarkerList = new ArrayList<>();
    List<MapPolyline> mapPolylines = new ArrayList<>();

    String baseURL = "";

    Map<Integer, Double> freeFlowSpeedTable = new HashMap<>();

    Map<Integer, Double> trafficTable = new HashMap<>();

    Map<Double, Double> chargingCurve = new HashMap<>();

    EVCarOptions evCarOptions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        importRoute = findViewById(R.id.importRoute);

        initializeRoutingService();

        // Get a MapView instance from the layout.
        mapView = findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);

        mapView.setOnReadyListener(() -> {
            // This will be called each time after this activity is resumed.
            // It will not be called before the first map scene was loaded.
            // Any code that requires map data may not work as expected beforehand.
            Log.d("MapViewInfo", "HERE Rendering Engine attached.");
        });

        loadMapScene();
    }

    public void initializeRoutingService() {
        try {
            routingEngine = new RoutingEngine();

            importRoute.setOnClickListener(l -> showBottomSheetDialog());

        } catch (InstantiationErrorException e) {
            throw new RuntimeException("Initialization of RoutingEngine failed: " + e.error.name());
        }
    }

    private void loadMapScene() {
        // Load a scene from the HERE SDK to render the map with a map scheme.
        mapView.getMapScene().loadScene(MapScheme.NORMAL_DAY, mapError -> {
            if (mapError == null) {
                double distanceInMeters = 1000 * 10;
                mapView.getCamera().lookAt(
                        new GeoCoordinates(52.530932, 13.384915),new MapMeasure(MapMeasure.Kind.DISTANCE, distanceInMeters));
            } else {
                Log.d("MapViewInfo", "Loading map failed: mapError: " + mapError.name());
            }
        });
    }

    private class GetRouteTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            try {
                getRoute(baseURL);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {

            System.out.println("Terminated");

            super.onPostExecute(aVoid);
        }
    }

    public void getRoute(String requestUrl) throws Exception {
        Request request = new Request.Builder()
                .url(requestUrl)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {

            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            // Get response body
            String responseBody = response.body().string();

            JSONObject responseObject = new JSONObject(responseBody);

            //Log.i("ResponseObject", responseObject.toString());

            JSONArray routes = responseObject.getJSONArray("routes");

            //Log.i("RoutesJSONArray", routes.toString());

            for (int i = 0; i < routes.length(); i++) {
                String routeHandle = routes.getJSONObject(i).getString("routeHandle");

                Log.i("RoutesJSONArray", routeHandle);

                importRouteFromSDK(routeHandle);
            }
        }
    }

    public void importRouteFromSDK(String routeHandle) {
        routingEngine.importRoute(new RouteHandle(routeHandle),
                new RefreshRouteOptions(evCarOptions),
                (routingError, list) -> {
                    if (routingError == null) {
                        Route newRoute = list != null ? list.get(0) : null;

                        if (newRoute != null) {
                            showRouteOnMap(newRoute);
                            Log.i("RouteInfo", "Route created");
                        }
                        // ...
                    } else {
                        // Handle error.
                        Log.e("RouteError", routingError.toString());
                    }
                });
    }

    private void showRouteOnMap(Route route) {
        // Optionally, clear any previous route.
        clearMap();

        // Show route as polyline.
        GeoPolyline routeGeoPolyline = route.getGeometry();
        float widthInPixels = 20;
        MapPolyline routeMapPolyline = new MapPolyline(routeGeoPolyline,
                widthInPixels,
                Color.valueOf(0, 0.56f, 0.54f, 0.63f)); // RGBA

        mapView.getMapScene().addMapPolyline(routeMapPolyline);
        mapPolylines.add(routeMapPolyline);

        mapView.getCamera().lookAt(route.getBoundingBox(), new GeoOrientationUpdate(0.0, 0.0));

        RoutePlace startPoint =
                route.getSections().get(0).getDeparturePlace();
        RoutePlace destination =
                route.getSections().get(route.getSections().size() - 1).getArrivalPlace();

        // Draw a circle to indicate starting point and destination.
        addMapMarker(startPoint.mapMatchedCoordinates, R.drawable.green_dot);
        addMapMarker(destination.mapMatchedCoordinates, R.drawable.green_dot);

        // Log maneuver instructions per route section.
        List<Section> sections = route.getSections();
        for (Section section : sections) {
            if (section.getArrivalPlace().mapMatchedCoordinates.latitude
                    != destination.mapMatchedCoordinates.latitude &&
                    section.getArrivalPlace().mapMatchedCoordinates.longitude
                            != destination.mapMatchedCoordinates.longitude) {
                addMapMarker(section.getArrivalPlace().mapMatchedCoordinates, R.drawable.red_dot);
            }
        }
    }

    private void addMapMarker(GeoCoordinates geoCoordinates, int resourceId) {
        MapImage mapImage = MapImageFactory.fromResource(this.getResources(), resourceId);
        MapMarker mapMarker = new MapMarker(geoCoordinates, mapImage);
        mapView.getMapScene().addMapMarker(mapMarker);
        mapMarkerList.add(mapMarker);
    }

    public void clearMap() {
        clearWaypointMapMarker();
        clearRoute();
    }

    private void clearWaypointMapMarker() {
        for (MapMarker mapMarker : mapMarkerList) {
            mapView.getMapScene().removeMapMarker(mapMarker);
        }
        mapMarkerList.clear();
    }

    private void clearRoute() {
        for (MapPolyline mapPolyline : mapPolylines) {
            mapView.getMapScene().removeMapPolyline(mapPolyline);
        }
        mapPolylines.clear();
    }

    private void showBottomSheetDialog() {
        evCarOptions = new EVCarOptions();

        baseURL = "https://router.hereapi.com/v8/routes";

        final BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        bottomSheetDialog.setContentView(R.layout.bottom_sheet_routing_api);

        bottomSheetDialog.findViewById(R.id.bottom_sheet_header);
        bottomSheetDialog.findViewById(R.id.bottom_sheet_body);

        cancelButton = bottomSheetDialog.findViewById(R.id.cancel_request);
        sendButton = bottomSheetDialog.findViewById(R.id.submit_request);

        apiKeyEdit = bottomSheetDialog.findViewById(R.id.apiKeyEdit);
        departureTimeEdit = bottomSheetDialog.findViewById(R.id.departureTimeEdit);
        arrivalTimeEdit = bottomSheetDialog.findViewById(R.id.arrivalTimeEdit);
        routingModeEdit = bottomSheetDialog.findViewById(R.id.routingModeEdit);
        alternativesEdit = bottomSheetDialog.findViewById(R.id.alternativesEdit);
        originEdit = bottomSheetDialog.findViewById(R.id.originEdit);
        destinationEdit = bottomSheetDialog.findViewById(R.id.destinationEdit);
        transportModeEdit = bottomSheetDialog.findViewById(R.id.transportModeEdit);
        freeFlowSpeedTableEdit = bottomSheetDialog.findViewById(R.id.freeFlowSpeedTableEdit);
        trafficSpeedTableEdit = bottomSheetDialog.findViewById(R.id.trafficSpeedTableEdit);
        ascentEdit = bottomSheetDialog.findViewById(R.id.ascentEdit);
        descentEdit = bottomSheetDialog.findViewById(R.id.descentEdit);
        auxiliaryConsumptionEdit = bottomSheetDialog.findViewById(R.id.auxiliaryConsumptionEdit);
        initialChargeEdit = bottomSheetDialog.findViewById(R.id.initialChargeEdit);
        maxChargeEdit = bottomSheetDialog.findViewById(R.id.maxChargeEdit);
        chargingCurveEdit = bottomSheetDialog.findViewById(R.id.chargingCurveEdit);
        maxChargingVoltageEdit = bottomSheetDialog.findViewById(R.id.maxChargingVoltageEdit);
        maxChargingCurrentEdit = bottomSheetDialog.findViewById(R.id.maxChargingCurrentEdit);
        maxChargeAfterChargingStationEdit = bottomSheetDialog.findViewById(R.id.maxChargeAfterChargingStationEdit);
        minChargeAtChargingStationEdit = bottomSheetDialog.findViewById(R.id.minChargeAtChargingStationEdit);
        minChargeAtDestinationEdit = bottomSheetDialog.findViewById(R.id.minChargeAtDestinationEdit);
        chargingSetupDurationEdit = bottomSheetDialog.findViewById(R.id.chargingSetupDurationEdit);
        connectorTypesEdit = bottomSheetDialog.findViewById(R.id.connectorTypesEdit);
        makeReachableEdit = bottomSheetDialog.findViewById(R.id.makeReachableEdit);
        preferredBrandsEdit = bottomSheetDialog.findViewById(R.id.preferredBrandsEdit);
        returnEdit = bottomSheetDialog.findViewById(R.id.returnEdit);

        assert cancelButton != null;
        cancelButton.setOnClickListener(view -> bottomSheetDialog.dismiss());

        sendButton.setOnClickListener(view -> {
            if (apiKeyEdit.getText().toString().isEmpty() || originEdit.getText().toString().isEmpty() || destinationEdit.getText().toString().isEmpty()
                    || freeFlowSpeedTableEdit.getText().toString().isEmpty()) {
                apiKeyEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                originEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                destinationEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                freeFlowSpeedTableEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));

                Toast.makeText(MainActivity.this, "Parameters in red color are mandatory", Toast.LENGTH_SHORT).show();
            } else {
                makeURL(bottomSheetDialog);
            }
        });

        bottomSheetDialog.show();
    }

    public void makeURL(BottomSheetDialog bottomSheetDialog) {

        boolean doTask = true;

        apiKeyEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));

        baseURL += "?apiKey=" + apiKeyEdit.getText().toString();

        departureTimeEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));

        if (!departureTimeEdit.getText().toString().equals("")) {

            if (departureTimeEdit.getText().toString().equals("any")) {
                baseURL += "&departureTime=" + departureTimeEdit.getText().toString();

                evCarOptions.routeOptions.departureTime = Date.from(Instant.now());
            } else {
                boolean isDate = isDate(departureTimeEdit.getText().toString());

                if (isDate) {
                    baseURL += "&departureTime=" + departureTimeEdit.getText().toString();

                    evCarOptions.routeOptions.departureTime = Date.from(Instant.parse(departureTimeEdit.getText().toString()));
                } else {
                    departureTimeEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                    doTask = false;
                }
            }
        }

        arrivalTimeEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));

        if (!arrivalTimeEdit.getText().toString().equals("")) {

            if (arrivalTimeEdit.getText().toString().equals("any")) {
                baseURL += "&arrivalTime=" + arrivalTimeEdit.getText().toString();

                evCarOptions.routeOptions.arrivalTime = Date.from(Instant.now());
            } else {
                boolean isDate = isDate(arrivalTimeEdit.getText().toString());

                if (isDate) {
                    baseURL += "&arrivalTime=" + arrivalTimeEdit.getText().toString();

                    evCarOptions.routeOptions.departureTime = Date.from(Instant.parse(arrivalTimeEdit.getText().toString()));
                } else {
                    arrivalTimeEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                    doTask = false;
                }
            }
        }

        routingModeEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));

        if (!routingModeEdit.getText().toString().equals("")) {

            if (routingModeEdit.getText().toString().equals("fast")) {
                baseURL += "&routingMode=" + routingModeEdit.getText().toString();

                evCarOptions.routeOptions.optimizationMode = OptimizationMode.FASTEST;
            } else if (routingModeEdit.getText().toString().equals("short")) {
                baseURL += "&routingMode=" + routingModeEdit.getText().toString();

                evCarOptions.routeOptions.optimizationMode = OptimizationMode.SHORTEST;
            } else {
                routingModeEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                doTask = false;
            }
        }

        alternativesEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));

        if (!alternativesEdit.getText().toString().equals("")) {

            boolean isInteger = isInteger(alternativesEdit.getText().toString());

            if (isInteger) {
                baseURL += "&alternatives=" + alternativesEdit.getText().toString();

                evCarOptions.routeOptions.alternatives = Integer.parseInt(alternativesEdit.getText().toString());

            } else {
                alternativesEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                doTask = false;
            }
        }

        originEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));
        baseURL += "&origin=" + originEdit.getText().toString();

        destinationEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));
        baseURL += "&destination=" + destinationEdit.getText().toString();

        baseURL += "&transportMode=" + transportModeEdit.getText().toString();

        freeFlowSpeedTableEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));
        try {
            if (freeFlowSpeedTableEdit.getText().toString().split(",").length % 2 == 0) {
                baseURL += "&ev[freeFlowSpeedTable]=" + freeFlowSpeedTableEdit.getText().toString();

                evCarOptions.consumptionModel.freeFlowSpeedTable =
                        getFreeFlowSpeedTable(freeFlowSpeedTableEdit.getText().toString().split(","));
            } else {
                freeFlowSpeedTableEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                doTask = false;
            }
        } catch (Error error) {
            freeFlowSpeedTableEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
            doTask = false;
        }

        trafficSpeedTableEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));

        if (!trafficSpeedTableEdit.getText().toString().equals("")) {
            try {
                if (trafficSpeedTableEdit.getText().toString().split(",").length % 2 == 0) {
                    baseURL += "&ev[trafficSpeedTable]=" + trafficSpeedTableEdit.getText().toString();

                    evCarOptions.consumptionModel.trafficSpeedTable =
                            getTrafficSpeedTable(trafficSpeedTableEdit.getText().toString().split(","));

                } else {
                    trafficSpeedTableEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                    doTask = false;
                }
            } catch (Error error) {
                trafficSpeedTableEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                doTask = false;
            }
        }

        ascentEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));

        if (!ascentEdit.getText().toString().equals("")) {
            boolean isInteger = isInteger(ascentEdit.getText().toString());

            if (isInteger) {
                baseURL += "&ev[ascent]=" + ascentEdit.getText().toString();
                evCarOptions.consumptionModel.ascentConsumptionInWattHoursPerMeter = Integer.parseInt(ascentEdit.getText().toString());
            } else {
                ascentEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                doTask = false;
            }
        }

        descentEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));

        if (!descentEdit.getText().toString().equals("")) {
            boolean isDouble = isDouble(descentEdit.getText().toString());

            if (isDouble) {
                baseURL += "&ev[descent]=" + descentEdit.getText().toString();
                evCarOptions.consumptionModel.descentRecoveryInWattHoursPerMeter = Double.parseDouble(descentEdit.getText().toString());
            } else {
                descentEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                doTask = false;
            }
        }

        auxiliaryConsumptionEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));

        if (!auxiliaryConsumptionEdit.getText().toString().equals("")) {
            boolean isDouble = isDouble(auxiliaryConsumptionEdit.getText().toString());

            if (isDouble) {
                baseURL += "&ev[auxiliaryConsumption]=" + auxiliaryConsumptionEdit.getText().toString();
                evCarOptions.consumptionModel.auxiliaryConsumptionInWattHoursPerSecond = Double.parseDouble(auxiliaryConsumptionEdit.getText().toString());
            } else {
                auxiliaryConsumptionEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                doTask = false;
            }
        }

        initialChargeEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));

        if (!initialChargeEdit.getText().toString().equals("")) {
            boolean isInteger = isInteger(initialChargeEdit.getText().toString());

            if (isInteger) {
                baseURL += "&ev[initialCharge]=" + initialChargeEdit.getText().toString();
                evCarOptions.batterySpecifications.initialChargeInKilowattHours = Integer.parseInt(initialChargeEdit.getText().toString());
            } else {
                initialChargeEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                doTask = false;
            }
        }

        maxChargeEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));

        if (!maxChargeEdit.getText().toString().equals("")) {
            boolean isInteger = isInteger(maxChargeEdit.getText().toString());

            if (isInteger) {
                baseURL += "&ev[maxCharge]=" + maxChargeEdit.getText().toString();
                evCarOptions.batterySpecifications.totalCapacityInKilowattHours = Integer.parseInt(maxChargeEdit.getText().toString());
            } else {
                maxChargeEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                doTask = false;
            }
        }

        chargingCurveEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));

        if (!chargingCurveEdit.getText().toString().equals("")) {
            try {
                if (chargingCurveEdit.getText().toString().split(",").length % 2 == 0) {
                    baseURL += "&ev[chargingCurve]=" + chargingCurveEdit.getText().toString();

                    evCarOptions.batterySpecifications.chargingCurve = getChargingCurve(chargingCurveEdit.getText().toString().split(","));
                } else {
                    //put error dialog here
                    chargingCurveEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                    doTask = false;
                }
            } catch (Error error) {
                chargingCurveEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                doTask = false;
            }
        }

        maxChargingVoltageEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));

        if (!maxChargingVoltageEdit.getText().toString().equals("")) {
            boolean isDouble = isDouble(maxChargingVoltageEdit.getText().toString());

            if (isDouble) {
                baseURL += "&ev[maxChargingVoltage]=" + maxChargingVoltageEdit.getText().toString();
                evCarOptions.batterySpecifications.maxChargingVoltageInVolts = Double.parseDouble(maxChargingVoltageEdit.getText().toString());
            } else {
                maxChargingVoltageEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                doTask = false;
            }
        }

        maxChargingCurrentEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));

        if (!maxChargingCurrentEdit.getText().toString().equals("")) {
            boolean isDouble = isDouble(maxChargingCurrentEdit.getText().toString());

            if (isDouble) {
                baseURL += "&[maxChargingCurrent]=" + maxChargingCurrentEdit.getText().toString();
                evCarOptions.batterySpecifications.maxChargingCurrentInAmperes = Double.parseDouble(maxChargingCurrentEdit.getText().toString());
            } else {
                maxChargingCurrentEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                doTask = false;
            }
        }

        maxChargeAfterChargingStationEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));

        if (!maxChargeAfterChargingStationEdit.getText().toString().equals("")) {
            boolean isInteger = isInteger(maxChargeAfterChargingStationEdit.getText().toString());

            if (isInteger) {
                baseURL += "&ev[maxChargeAfterChargingStation]=" + maxChargeAfterChargingStationEdit.getText().toString();
                evCarOptions.batterySpecifications.targetChargeInKilowattHours = Integer.parseInt(maxChargeAfterChargingStationEdit.getText().toString());
            } else {
                maxChargeAfterChargingStationEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                doTask = false;
            }
        }

        minChargeAtChargingStationEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));

        if (!minChargeAtChargingStationEdit.getText().toString().equals("")) {
            boolean isDouble = isDouble(minChargeAtChargingStationEdit.getText().toString());

            if (isDouble) {
                baseURL += "&ev[minChargeAtChargingStation]=" + minChargeAtChargingStationEdit.getText().toString();
                evCarOptions.batterySpecifications.minChargeAtChargingStationInKilowattHours = Double.parseDouble(minChargeAtChargingStationEdit.getText().toString());
            } else {
                minChargeAtChargingStationEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                doTask = false;
            }
        }

        minChargeAtDestinationEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));

        if (!minChargeAtDestinationEdit.getText().toString().equals("")) {
            boolean isDouble = isDouble(minChargeAtDestinationEdit.getText().toString());

            if (isDouble) {
                baseURL += "&ev[minChargeAtDestination]=" + minChargeAtDestinationEdit.getText().toString();
                evCarOptions.batterySpecifications.minChargeAtDestinationInKilowattHours = Double.parseDouble(minChargeAtDestinationEdit.getText().toString());
            } else {
                minChargeAtDestinationEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                doTask = false;
            }
        }

        chargingSetupDurationEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));

        if (!chargingSetupDurationEdit.getText().toString().equals("")) {
            boolean isLong = isLong(chargingSetupDurationEdit.getText().toString());

            if (isLong) {
                baseURL += "&ev[chargingSetupDuration]=" + chargingSetupDurationEdit.getText().toString();
                evCarOptions.batterySpecifications.chargingSetupDuration = Duration.ofSeconds(Long.parseLong(chargingSetupDurationEdit.getText().toString()));
            } else {
                chargingSetupDurationEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                doTask = false;
            }
        }

        connectorTypesEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));

        if (!connectorTypesEdit.getText().toString().equals("")) {
            baseURL += "&ev[connectorTypes]=" + connectorTypesEdit.getText().toString();

            if (connectorTypesEdit.getText().toString().equals("iec62196Type1Combo")) {
                evCarOptions.batterySpecifications.connectorTypes = Collections.singletonList(ChargingConnectorType.IEC_62196_TYPE_1_COMBO);
            } else if (connectorTypesEdit.getText().toString().equals("iec62196Type2Combo")) {
                evCarOptions.batterySpecifications.connectorTypes = Collections.singletonList(ChargingConnectorType.IEC_62196_TYPE_1_COMBO);
            } else if (connectorTypesEdit.getText().toString().equals("chademo")) {
                evCarOptions.batterySpecifications.connectorTypes = Collections.singletonList(ChargingConnectorType.IEC_62196_TYPE_1_COMBO);
            } else if (connectorTypesEdit.getText().toString().equals("tesla")) {
                evCarOptions.batterySpecifications.connectorTypes = Collections.singletonList(ChargingConnectorType.IEC_62196_TYPE_1_COMBO);
            } else {
                connectorTypesEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                doTask = false;
            }
        }

        makeReachableEdit.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));

        if (!makeReachableEdit.getText().toString().equals("")) {
            if(makeReachableEdit.getText().toString().equals("true") ||
                    makeReachableEdit.getText().toString().equals("false")) {
                baseURL += "&ev[makeReachable]=" + makeReachableEdit.getText().toString();
            } else {
                makeReachableEdit.setBackgroundColor(android.graphics.Color.parseColor("#FAD4D4"));
                doTask = false;
            }
        }

        if (!preferredBrandsEdit.getText().toString().equals("")) {
            baseURL += "&ev[preferredBrands]" + preferredBrandsEdit.getText().toString();
        }

        baseURL += "&return=" + returnEdit.getText().toString();

        System.out.println("URL: " + baseURL);

        if (doTask) {
            bottomSheetDialog.dismiss();

            GetRouteTask downloadTask = new GetRouteTask();

            downloadTask.execute();
        }
    }

    public Map<Integer, Double> getFreeFlowSpeedTable(String[] freeFlowTable) {
        freeFlowSpeedTable.clear();

        System.out.println(Arrays.toString(freeFlowTable));
        System.out.println(freeFlowTable.length);

        int indiceUno = 0;
        int indiceDos = 1;

        for (int i = 0; i < freeFlowTable.length; i++) {

            if (i == 0) {

                freeFlowSpeedTable.put(Integer.parseInt(freeFlowTable[i + indiceUno]), Double.parseDouble(freeFlowTable[i + indiceDos]));

                indiceUno += 1;
                indiceDos += 1;

            } else {
                if (indiceDos + i < freeFlowTable.length) {

                    freeFlowSpeedTable.put(Integer.parseInt(freeFlowTable[i + indiceUno]), Double.parseDouble(freeFlowTable[i + indiceDos]));

                    indiceUno += 1;
                    indiceDos += 1;
                }
            }
        }

        return freeFlowSpeedTable;
    }

    public Map<Integer, Double> getTrafficSpeedTable(String[] traffcTable) {
        trafficTable.clear();

        System.out.println(Arrays.toString(traffcTable));
        System.out.println(traffcTable.length);

        int indiceUno = 0;
        int indiceDos = 1;

        for (int i = 0; i < traffcTable.length; i++) {

            if (i == 0) {

                trafficTable.put(Integer.parseInt(traffcTable[i + indiceUno]), Double.parseDouble(traffcTable[i + indiceDos]));

                indiceUno += 1;
                indiceDos += 1;

            } else {
                if (indiceDos + i < traffcTable.length) {

                    trafficTable.put(Integer.parseInt(traffcTable[i + indiceUno]), Double.parseDouble(traffcTable[i + indiceDos]));

                    indiceUno += 1;
                    indiceDos += 1;
                }
            }
        }

        return trafficTable;
    }

    public Map<Double, Double> getChargingCurve(String[] chargingCurv) {
        chargingCurve.clear();

        System.out.println(Arrays.toString(chargingCurv));
        System.out.println(chargingCurv.length);

        int indiceUno = 0;
        int indiceDos = 1;

        for (int i = 0; i < chargingCurv.length; i++) {

            if (i == 0) {

                chargingCurve.put(Double.parseDouble(chargingCurv[i + indiceUno]), Double.parseDouble(chargingCurv[i + indiceDos]));

                indiceUno += 1;
                indiceDos += 1;

            } else {
                if (indiceDos + i < chargingCurv.length) {

                    chargingCurve.put(Double.parseDouble(chargingCurv[i + indiceUno]), Double.parseDouble(chargingCurv[i + indiceDos]));

                    indiceUno += 1;
                    indiceDos += 1;
                }
            }
        }

        return chargingCurve;
    }

    public boolean isDouble(String value) {
        try {
            Double doubleValue = Double.parseDouble(value);

            return true;
        } catch (Error error) {
            return false;
        }
    }

    public boolean isLong(String value) {
        try {
            Long longValue = Long.parseLong(value);

            return true;
        } catch (Error error) {
            return false;
        }
    }

    public boolean isInteger(String value) {
        try {
            Integer doubleValue = Integer.parseInt(value);

            return true;
        } catch (Error error) {
            return false;
        }
    }

    public boolean isDate(String value) {
        try {
            Date doubleValue = Date.from(Instant.parse(value));

            return true;
        } catch (Error error) {
            return false;
        }
    }
}