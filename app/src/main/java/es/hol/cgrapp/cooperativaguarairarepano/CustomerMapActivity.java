package es.hol.cgrapp.cooperativaguarairarepano;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private GoogleMap mMap;

    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;

    private DatabaseReference mCustomerDatabase;
    private FirebaseAuth mAuth;

    //boton de logout en esta ventana de la app y de request

    private Button mLogout, mRequest, mSettings, mHistory;

    private LatLng pickupLocation, destinationLatLng;

    private Boolean requestBol= false;

    private Marker pickupMarker;

    private String destination, userID, mPassagers;
    //necesarios para traer informacion del conductor *
    private LinearLayout mDriverInfo;
    private ImageView mDriverProfileImage;
    private TextView mDriverName, mDriverPhone, mDriverCar, mDriverNumber, mDriverColor;
    private EditText mPassagersField;

    private RatingBar mRatingBar;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
//prueba de numero de pasajeros.
        mPassagersField= (EditText) findViewById(R.id.passagers);
        mAuth = FirebaseAuth.getInstance();
        userID = mAuth.getCurrentUser().getUid();
        mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(userID);
//---------------------
        mapFragment.getMapAsync(this);
        destinationLatLng  = new LatLng(0.0,0.0);
        mLogout = (Button) findViewById(R.id.logout);
        mRequest = (Button) findViewById(R.id.request);
        mSettings = (Button) findViewById(R.id.settings);
        mHistory = (Button) findViewById(R.id.history);

        //necesarios para traer informacion del conductor * -- botones asociados al layout de este activity
        mDriverInfo = (LinearLayout) findViewById(R.id.driverInfo);
        mDriverProfileImage = (ImageView) findViewById(R.id.driverProfileImage);
        mDriverName = (TextView) findViewById(R.id.driverName);
        mDriverPhone = (TextView) findViewById(R.id.driverPhone);
        mDriverCar= (TextView) findViewById(R.id.driverCar);
        mDriverNumber= (TextView) findViewById(R.id.driverNumber);
        mDriverColor= (TextView) findViewById(R.id.driverColor);




        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirebaseAuth.getInstance().signOut(); //firebase nos da esta linea para el log out, pretty easy tho
                Intent intent = new Intent(CustomerMapActivity.this, MainActivity.class); //redirige al usuario a la ventana inicial de login
                startActivity(intent);
                endRide();//que termine el request al cerrar sesion
                finish(); //cierra esta activity
                return;
            }
        });

        mRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (requestBol){
                   endRide();
                }else {
                    requestBol=true;
                    savePassagers();

                    //Punto de localizacion del customer, similar al del driver
                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");
                    GeoFire geoFire = new GeoFire(ref);
                    geoFire.setLocation(userId, new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude())); //quede aqui cuando se fue el internet

                    pickupLocation = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());

                    pickupMarker = mMap.addMarker(new MarkerOptions().position(pickupLocation).title("Recoger Aquí").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_pickup)));

                    mRequest.setText("Buscando un Conductor...");

                    getClosestDriver();
                }
            }
        });
        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CustomerMapActivity.this, CustomerSettingsActivity.class);
                startActivity(intent);
                return;
                //no se finaliza para que esto quede sobre el customer map activity
            }
        });

        mHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CustomerMapActivity.this, HistoryActivity.class);
                intent.putExtra("customerOrDriver", "Customers");
                startActivity(intent);
                return;
            }
        });
//Api Atucomplete!

    PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment)
      getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);

        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                // TODO: Get info about the selected place.
                destination = place.getName().toString();
               destinationLatLng = place.getLatLng();
            }
            @Override
            public void onError(Status status) {
            }
        });
    }
    private int radius = 1;
    private Boolean driverFound= false;
    private String driverFoundID;

    GeoQuery geoQuery;


    private void getClosestDriver() {
        DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference().child("driversAvailable");

        GeoFire geoFire = new GeoFire(driverLocation);

        geoQuery = geoFire.queryAtLocation(new GeoLocation(pickupLocation.latitude, pickupLocation.longitude), radius);
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if (!driverFound && requestBol) {
                    driverFound = true;
                    driverFoundID = key;


                //crearemos un child que le notificara al diver que tiene un customer en espera
                DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID).child("customerRequest");

                String customerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
//aqui se guardan estos valores en la base de datos
                HashMap map = new HashMap();
                map.put("customerRideId", customerId);

                map.put("destination", destination);

                map.put("destinationLat", destinationLatLng.latitude);
                map.put("destinationLng", destinationLatLng.longitude);
                driverRef.updateChildren(map);

                //Crearemos una funcion para decirle al customer donde esta el driver

                getDriverLocation();
                getDriverInfo();
                getHasRideEnded();
                mRequest.setText("Conductor Encontrado, buscando su localización...");
            }
        }

                @Override
                public void onKeyExited(String key) {

                }

                @Override
                public void onKeyMoved(String key, GeoLocation location) {

                }

                @Override
                public void onGeoQueryReady() {
                    if(!driverFound) {
                        radius++;
                        getClosestDriver();
                        if(radius > 10000) {
                    mRequest.setText("Lo sentimos, en estos momentos no hay conductores disponibles");

                        }
                    }

                }

                @Override
                public void onGeoQueryError(DatabaseError error) {

                }
            });
        }

//marker del conductor en el mapa del customer

private Marker mDriverMarker;
private DatabaseReference driverLocationRef;
private ValueEventListener driverLocationRefListener;
//funcion para obtener datos del driver y pasarselos al customer
private void getDriverLocation () {
//l es el nombre del child que geobase le coloca al contenedor de la lat y la long, g es el autenticador de la operacion

    driverLocationRef = FirebaseDatabase.getInstance().getReference().child("driversWorking").child(driverFoundID).child("l");
    driverLocationRefListener = driverLocationRef.addValueEventListener(new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            if(dataSnapshot.exists() && requestBol){
                List<Object> map = (List<Object>) dataSnapshot.getValue();
                double locationLat = 0;
                double locationLng = 0;
                if(map.get(0) != null){
                    locationLat = Double.parseDouble(map.get(0).toString());
                }
                if(map.get(1) != null){
                    locationLng = Double.parseDouble(map.get(1).toString());
                }
                LatLng driverLatLng = new LatLng(locationLat,locationLng);
                if(mDriverMarker != null){
                    mDriverMarker.remove();
                }
                Location loc1 = new Location("");
                loc1.setLatitude(pickupLocation.latitude);
                loc1.setLongitude(pickupLocation.longitude);

                Location loc2 = new Location("");
                loc2.setLatitude(driverLatLng.latitude);
                loc2.setLongitude(driverLatLng.longitude);

                float distance = loc1.distanceTo(loc2);


                if (distance<100){
                    mRequest.setText("Su taxi ha llegado!");
                }else{
                    mRequest.setText("Su taxi se encuentra a: " + String.valueOf(distance));
                }


                mDriverMarker = mMap.addMarker(new MarkerOptions().position(driverLatLng).title("Taxi").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_car)));
            }

        }

        @Override
        public void onCancelled(DatabaseError databaseError) {

        }
    });

}
    //Funcion para mostrar informacion del driver en el customer activity
    private void getDriverInfo(){
        mDriverInfo.setVisibility(View.VISIBLE);
        DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID);
        mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && dataSnapshot.getChildrenCount()>0){
                    if(dataSnapshot.child("name")!=null){
                        mDriverName.setText(dataSnapshot.child("name").getValue().toString());
                    }
                    if(dataSnapshot.child("phone")!=null){
                        mDriverPhone.setText(dataSnapshot.child("phone").getValue().toString());
                    }
                    if(dataSnapshot.child("car")!=null){
                        mDriverCar.setText(dataSnapshot.child("car").getValue().toString());
                    }
                    if(dataSnapshot.child("color")!=null){
                        mDriverColor.setText(dataSnapshot.child("color").getValue().toString());
                    }
                    if(dataSnapshot.child("number")!=null){
                        mDriverNumber.setText(dataSnapshot.child("number").getValue().toString());
                    }

                    if(dataSnapshot.child("profileImageUrl")!=null){
                        Glide.with(getApplication()).load(dataSnapshot.child("profileImageUrl").getValue().toString()).into(mDriverProfileImage);
                    }

                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }


//funcion para saber cuando el conductor ha cancelado la ride
private DatabaseReference driveHasEndedRef;
    private ValueEventListener driveHasEndedRefListener;
    private void getHasRideEnded(){
        driveHasEndedRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID).child("customerRequest").child("customerRideId");
        driveHasEndedRefListener = driveHasEndedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){

                }else{
                    endRide();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }
//funcoion para terminar el ride desde el customer
private void endRide(){
    requestBol = false;
    geoQuery.removeAllListeners();
    driverLocationRef.removeEventListener(driverLocationRefListener);
    driveHasEndedRef.removeEventListener(driveHasEndedRefListener);

    if (driverFoundID != null){
        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID).child("customerRequest");
        driverRef.removeValue();
        driverFoundID = null;

    }
    driverFound = false;
    radius = 1;
    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");
    GeoFire geoFire = new GeoFire(ref);
    geoFire.removeLocation(userId);

    if(pickupMarker != null){
        pickupMarker.remove();
    }
    if (mDriverMarker != null){
        mDriverMarker.remove();
    }
    mRequest.setText("Pedir un Taxi");

    mDriverInfo.setVisibility(View.GONE);
    mDriverName.setText("");
    mDriverPhone.setText("");
    mDriverCar.setText("");
    mDriverColor.setText("");
    mDriverNumber.setText("");
    mDriverProfileImage.setImageResource(R.mipmap.ic_default_user);
}

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        //no refrescara la localizacion cada segundo
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        buildGoogleApiClient();
        mMap.setMyLocationEnabled(true);

    }

    protected synchronized void buildGoogleApiClient(){
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    //gettin' the updated location, la parte divertida!
    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location; // location es el valor que la funcion de arriba pasa

        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude()); //cada vez que el usuario se mueva el centro del mapa sera el usuario

        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng)); //aqui toma la latitud y la longitud actual de los usuarios

        mMap.animateCamera(CameraUpdateFactory.zoomTo(12)); //mientras mas alto sea el zoom estaran mas cerca del piso! 1-21 value


    }


    // onConected: cuando el mapa esta listo y ready para empezar a trabajar, so we are gonna to create a request to get the location segundo a segundo
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000); // updatear location each sec so 1000ms
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY); //significa que sera super preciso el accuracy del gps, a coste de consumir mucha bateria, lower accuracy: lower battery consumo

//usaremos los servicios de localizacion
        //est if revisa si estan todos los permisos correctos del android manifest

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        //esto hace el refresh del location, si no usamos esto solo captara la localizacion una sola vez y no cada segundo :x
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);  //input las variables que definimos anteriormente


    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    //para saber cuando un conductor no estara disponbible debemos de marcar cuando se desconecte de la app - onstop

    @Override
    protected void onStop() {
        super.onStop();

    }

//prueba de numero de pasajeros
private void savePassagers() {
    mPassagers = mPassagersField.getText().toString();

    Map userInfo = new HashMap();
    userInfo.put("passagers", mPassagers);
    mCustomerDatabase.updateChildren(userInfo);
}

}