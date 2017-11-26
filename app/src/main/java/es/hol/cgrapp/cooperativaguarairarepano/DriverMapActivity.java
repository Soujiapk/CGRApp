package es.hol.cgrapp.cooperativaguarairarepano;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Camera;
import android.location.Location;
import android.location.LocationListener;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.LocationCallback;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static es.hol.cgrapp.cooperativaguarairarepano.R.id.rideDistance;

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener, RoutingListener {

    private GoogleMap mMap;

    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;

//boton de logout en esta ventana de la app

    private Button mLogout, mSettings, mRideStatus, mHistory;
//necesarios para traer informacion del cliente *
    private LinearLayout mCustomerInfo;
    private ImageView mCustomerProfileImage;
    private TextView mCustomerName, mCustomerPhone, mCustomerDestination, mCustomerPassagers;



//variable para guardar id del customer que se le pasara al driver
private String customerId = "", destination;
private LatLng destinationLatLng, pickupLatLng;


//---
private SupportMapFragment mapFragment;


private Boolean isLogginOut = false;

private int status=0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        polylines = new ArrayList<>();
        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        mapFragment.getMapAsync(this);
//necesarios para traer informacion del cliente * -- botones asociados al layout de este activity
        mCustomerInfo = (LinearLayout) findViewById(R.id.customerInfo);
        mCustomerProfileImage = (ImageView) findViewById(R.id.customerProfileImage);
        mCustomerName = (TextView) findViewById(R.id.customerName);
        mCustomerPhone = (TextView) findViewById(R.id.customerPhone);
        mCustomerDestination= (TextView) findViewById(R.id.customerDestination);
        mCustomerPassagers= (TextView) findViewById(R.id.customerPassagers);

//---------------------------------------------------

        mSettings=(Button) findViewById(R.id.settings);
        mLogout = (Button) findViewById(R.id.logout);
        mRideStatus=(Button) findViewById(R.id.rideStatus);
        mHistory=(Button) findViewById(R.id.history);

        mRideStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch(status){
                    case 1: //el conductor esta en camino a recoger al cliente
                        status=2;// el conductor va camino al destino con el cliente ya en el auto
                        erasePolylines();
                        if(destinationLatLng.latitude!=0.0 && destinationLatLng.longitude!=0.0){
                            getRouteToMarker(destinationLatLng);
                        }
                        mRideStatus.setText("Carrera Terminada Satisfacoriamente!");

                        break;
                    case 2:// el conductor va camino al destino con el cliente ya en el auto
                        recordRide();
                        endRide();
                        break;
                }

            }
        });
        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                isLogginOut =true;

                disconnectDriver();

                FirebaseAuth.getInstance().signOut(); //firebase nos da esta linea para el log out, pretty easy tho
                Intent intent = new Intent(DriverMapActivity.this, MainActivity.class); //redirige al usuario a la ventana inicial de login
                startActivity(intent);
                finish(); //cierra esta activity
                return;
            }
        });

//click listener del boton de perfil
        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DriverMapActivity.this, DriverSettingsAcitivity2.class); //redirige al usuario a la ventana inicial de login
                startActivity(intent);
                finish(); //cierra esta activity
                return;

            }
        });

        mHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DriverMapActivity.this, HistoryActivity.class);
                intent.putExtra("customerOrDriver", "Drivers");
                startActivity(intent);
                return;
            }
        });
        getAssignedCustomer();

    }

//Crearemos una funcion para obtener el customer que se le asigno a este driver
//Esta funcion sera la que usaremos para verificar cuando debe mostrar informacion del usuario
private void getAssignedCustomer(){
    String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid();
    DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverId).child("customerRequest").child("customerRideId");
    assignedCustomerRef.addValueEventListener(new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            if(dataSnapshot.exists()){
                status=1; //el conductor va a en direccion al customer
                customerId = dataSnapshot.getValue().toString();
                getAssignedCustomerPickupLocation();
                getAssignedCustomerInfo();  //display informacion del usuario*
                getAssignedCustomerDestination();
            }else{
                endRide();

            }
        }
        @Override
        public void onCancelled(DatabaseError databaseError) {
        }
    });
}

    Marker pickupMarker;
    private DatabaseReference assignedCustomerPickupLocationRef;
    private ValueEventListener assignedCustomerPickupLocationRefListener;

   private void getAssignedCustomerPickupLocation(){
       assignedCustomerPickupLocationRef = FirebaseDatabase.getInstance().getReference().child("customerRequest").child(customerId).child("l");

       assignedCustomerPickupLocationRefListener =assignedCustomerPickupLocationRef.addValueEventListener(new ValueEventListener() {
           @Override
           public void onDataChange(DataSnapshot dataSnapshot) {
               if (dataSnapshot.exists() && !customerId.equals("")) {
                   List<Object> map = (List<Object>) dataSnapshot.getValue();
                   double locationLat = 0;
                   double locationLng = 0;

                   //verificando lat
                   if (map.get(0) != null) {
                       locationLat = Double.parseDouble(map.get(0).toString());
                   }
                   //verificando lng
                   if (map.get(1) != null) {
                       locationLng = Double.parseDouble(map.get(1).toString());
                   }

                   //añadiremos una marka en el mapa apuntando a donde se encuentra el driver con los valores encontrados arriba - este marker se creo arriba de la funcion

                   LatLng pickupLatLng = new LatLng(locationLat, locationLng);
                   //hay que remover el marker cada vez que mande uno nuevo la funcion para que no haya mas de uno en el mapa :) so - verificar creo que es pickuplatlng
                   pickupMarker = mMap.addMarker(new MarkerOptions().position(pickupLatLng).title("Su cliente esta aquí").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_pickup)));
                   getRouteToMarker(pickupLatLng);
               }
           }

           @Override
           public void onCancelled(DatabaseError databaseError) {

           }
       });

   }

    private void getRouteToMarker(LatLng pickupLatLng) {
        Routing routing = new Routing.Builder()
                .travelMode(AbstractRouting.TravelMode.DRIVING)
                .withListener(this)
                .alternativeRoutes(false)
                .waypoints(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()), pickupLatLng)
                .build();
        routing.execute();
    }

    //funcion para obtener el destino del customer
    private void getAssignedCustomerDestination(){
        String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverId).child("customerRequest");
        assignedCustomerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()) {
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if(map.get("destination")!=null){
                        destination = map.get("destination").toString();
                        mCustomerDestination.setText("Destino: " + destination);
                    }
                    else{
                        mCustomerDestination.setText("Destino: El cliente aun no ha marcado un destino");
                    }

                    Double destinationLat = 0.0;
                    Double destinationLng = 0.0;

                    if(map.get("destinationLat") != null){
                        destinationLat = Double.valueOf(map.get("destinationLat").toString());
                    }
                    if(map.get("destinationLng") != null){
                        destinationLng = Double.valueOf(map.get("destinationLng").toString());
                        destinationLatLng = new LatLng(destinationLat, destinationLng);
                    }

                }
            }



            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }
    //Funcion para mostrar informacion del usuario en el driver activity
    private void getAssignedCustomerInfo(){
        mCustomerInfo.setVisibility(View.VISIBLE);
        DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(customerId);
        mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && dataSnapshot.getChildrenCount()>0){
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if(map.get("name")!=null){
                        mCustomerName.setText(map.get("name").toString());
                    }
                    if(map.get("phone")!=null){
                        mCustomerPhone.setText(map.get("phone").toString());
                    }
                    if(map.get("passagers")!=null){
                        mCustomerPassagers.setText(map.get("passagers").toString());
                    }
                    if(map.get("profileImageUrl")!=null){
                        Glide.with(getApplication()).load(map.get("profileImageUrl").toString()).into(mCustomerProfileImage);
                    }

                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

//funcion para terminar el ride desde el driver
private void endRide(){
    mRideStatus.setText("Cliente Asignado");
    erasePolylines();

    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
    DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(userId).child("customerRequest");
    driverRef.removeValue();

    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");
    GeoFire geoFire = new GeoFire(ref);
    geoFire.removeLocation(customerId);
    customerId="";

    if(pickupMarker != null){
        pickupMarker.remove();
    }
    if (assignedCustomerPickupLocationRefListener != null){
        assignedCustomerPickupLocationRef.removeEventListener(assignedCustomerPickupLocationRefListener);
    }
    mCustomerInfo.setVisibility(View.GONE);
    mCustomerName.setText("");
    mCustomerPhone.setText("");
    mCustomerDestination.setText("El Destino aun no ha sido seleccionado por el cliente");
    mCustomerProfileImage.setImageResource(R.mipmap.ic_default_user);
}
   //record ride --usar en el customer tmbn
   private void recordRide(){
       String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
       DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(userId).child("history");
       DatabaseReference customerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(customerId).child("history");
       DatabaseReference historyRef = FirebaseDatabase.getInstance().getReference().child("history");
       String requestId = historyRef.push().getKey();
       driverRef.child(requestId).setValue(true);
       customerRef.child(requestId).setValue(true);

       HashMap map = new HashMap();
       map.put("driver", userId);
       map.put("customer", customerId);
       map.put("rating", 0);
       map.put("timestamp", getCurrentTimestamp());
       map.put("destination", destination);

       historyRef.child(requestId).updateChildren(map);
   }

    private Long getCurrentTimestamp() {
        Long timestamp = System.currentTimeMillis()/1000;
        return timestamp;
    }


    //-----------
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
        if(getApplicationContext()!=null){

            mLastLocation = location;
            LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());
            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(12));

            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference refAvailable = FirebaseDatabase.getInstance().getReference("driversAvailable");
            DatabaseReference refWorking = FirebaseDatabase.getInstance().getReference("driversWorking");
            GeoFire geoFireAvailable = new GeoFire(refAvailable);
            GeoFire geoFireWorking = new GeoFire(refWorking);

            switch (customerId){
                case "":
                    geoFireWorking.removeLocation(userId);
                    geoFireAvailable.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));
                    break;

                default:
                    geoFireAvailable.removeLocation(userId);
                    geoFireWorking.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));
                    break;
            }
        }
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

    private void disconnectDriver (){
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("driversAvailable");
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId);
       //pars que al desconectarse tmbn se remuevan los datos en el driversWorking
        DatabaseReference refWorking = FirebaseDatabase.getInstance().getReference("driversWorking");

        GeoFire geoFireWorking = new GeoFire(refWorking);
        geoFireWorking.removeLocation(userId);


    }
    //para saber cuando un conductor no estara disponbible debemos de marcar cuando se desconecte de la app - onstop
    final int LOCATION_REQUEST_CODE = 1;
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode){
            case LOCATION_REQUEST_CODE:{
                if(grantResults.length >0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    mapFragment.getMapAsync(this);
                } else{
                    Toast.makeText(getApplicationContext(), "Por favor acepte los permisos", Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }
    @Override
    protected void onStop() {
        super.onStop();

        if(!isLogginOut){
            endRide();
            disconnectDriver ();
        }

    }
    private List<Polyline> polylines;
    private static final int[] COLORS = new int[]{R.color.primary_dark_material_light};
    @Override
    public void onRoutingFailure(RouteException e) {
        if(e != null) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }else {
            Toast.makeText(this, "Something went wrong, Try again", Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    public void onRoutingStart() {

    }

    @Override
    public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex) {
        if(polylines.size()>0) {
            for (Polyline poly : polylines) {
                poly.remove();
            }
        }

        polylines = new ArrayList<>();
        //add route(s) to the map.
        for (int i = 0; i <route.size(); i++) {

            //In case of more than 5 alternative routes
            int colorIndex = i % COLORS.length;

            PolylineOptions polyOptions = new PolylineOptions();
            polyOptions.color(getResources().getColor(COLORS[colorIndex]));
            polyOptions.width(10 + i * 3);
            polyOptions.addAll(route.get(i).getPoints());
            Polyline polyline = mMap.addPolyline(polyOptions);
            polylines.add(polyline);

            Toast.makeText(getApplicationContext(),"Route "+ (i+1) +": distance - "+ route.get(i).getDistanceValue()+": duration - "+ route.get(i).getDurationValue(),Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    public void onRoutingCancelled() {

    }
    private void erasePolylines(){
        for(Polyline line : polylines){
            line.remove();
        }
        polylines.clear();
    }
}
