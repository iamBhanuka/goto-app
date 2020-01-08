package com.bhanuka.agoto

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.android.volley.toolbox.Volley
import com.bhanuka.agoto.data.remote.DirectionApi
import com.bhanuka.agoto.data.remote.DirectionResponse
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.maps.internal.PolylineEncoding
import com.google.maps.model.TravelMode
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    lateinit var locationRequest: LocationRequest
    lateinit var locationCallback: LocationCallback

    private var mMap: GoogleMap? = null

    private lateinit var mLastLocation: Location
    private var myMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var cameraMoved = false
    private var polyline:Polyline? = null

    companion object {
        private const val My_PERMISSION_CODE: Int = 1000
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Places.initialize(applicationContext, getString(R.string.googleMapApiKey))


        fusedLocationProviderClient = FusedLocationProviderClient(this)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        //request permission

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkLocationPermission()) {
                buildLocationRequest();
                buildLocationcallback();

                fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
                fusedLocationProviderClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.myLooper()
                )
            }
        } else {
            buildLocationRequest();
            buildLocationcallback();

            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
            fusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.myLooper()
            );
        }

        val button: Button = findViewById(R.id.button)
        button.setOnClickListener {
            val AUTOCOMPLETE_REQUEST_CODE = 1;

// Set the fields to specify which types of place data to
// return after the user has made a selection.
            val fields =
                listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)

// Start the autocomplete intent.
            val intent = Autocomplete.IntentBuilder(
                AutocompleteActivityMode.FULLSCREEN, fields
            )
                .build(this);
            startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                val place = Autocomplete.getPlaceFromIntent(data!!);
                val markerOptions = MarkerOptions()
                    .position(place.latLng!!)
                    .title("Fuck")

                if (mMap != null) {
                    destinationMarker = mMap?.addMarker(markerOptions)

                    getDirection(
                        TravelMode.DRIVING,
                        myMarker!!.position,
                        destinationMarker!!.position!!
                    )
                    //move camera

                    mMap?.moveCamera(CameraUpdateFactory.newLatLng(place.latLng))
                    mMap?.animateCamera(CameraUpdateFactory.zoomTo(11f))
                }
            } else if (resultCode == AutocompleteActivity.RESULT_ERROR) {
                // TODO: Handle the error.
                val status = Autocomplete.getStatusFromIntent(data!!);
                Log.i("sex", status.getStatusMessage());
            } else if (resultCode == RESULT_CANCELED) {
                // The user canceled the operation.
            }
        }
    }

    private fun getDirection(mode: TravelMode, origin: LatLng, destination: LatLng) {

        Log.e("origin", "${origin.latitude},${origin.longitude}")
        Log.e("destination", "${destination.latitude},${destination.longitude}")

        val result = DirectionApi.invoke().getDirections(
            "${origin.latitude},${origin.longitude}",
            "${destination.latitude},${destination.longitude}",
            mode.name.toLowerCase(),
            getString(R.string.googleMapApiKey)
        )

        result.enqueue(object : Callback<DirectionResponse> {
            override fun onResponse(
                call: Call<DirectionResponse>,
                response: Response<DirectionResponse>
            ) {
                Log.e("direction", "Success")
                addPolyline(response.body())
            }

            override fun onFailure(call: Call<DirectionResponse>, t: Throwable) {
                Log.e("direction", "unSuccess")
            }
        })
    }

    private fun addPolyline(result: DirectionResponse?) {
        polyline?.remove()
        if (result != null) {
            val decodedPath =
                PolylineEncoding.decode(result.routes[0].overview_polyline.points).map {
                    LatLng(it.lat, it.lng)
                }
            polyline = mMap?.addPolyline(PolylineOptions().addAll(decodedPath))
            val bound1 = LatLng(
                result.routes.first().bounds.northeast.lat,
                result.routes.first().bounds.northeast.lng
            )
            val bound2 = LatLng(
                result.routes.first().bounds.southwest.lat,
                result.routes.first().bounds.southwest.lng
            )
            mMap?.animateCamera(
                CameraUpdateFactory.newLatLngBounds(
                    LatLngBounds.builder()
                        .include(bound1)
                        .include(bound2)
                        .build(), 30
                )
            )
        }
    }


    private fun buildLocationRequest() {
        locationRequest = LocationRequest()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest.interval = 2000
        locationRequest.fastestInterval = 1000
        locationRequest.smallestDisplacement = 10f
    }

    private fun buildLocationcallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult?) {
                mLastLocation = p0!!.locations.get(p0?.locations.size - 1)

                Log.e("callback", "i am callback")

                if (myMarker != null) {
                    myMarker!!.remove()
                }

                val latitude = mLastLocation.latitude
                val longitude = mLastLocation.longitude

                val latLng = LatLng(latitude, longitude)
                val markerOptions = MarkerOptions()
                    .position(latLng)
                    .title("I'm Hear")

                if (mMap != null) {
                    myMarker = mMap?.addMarker(markerOptions)

                    if (!cameraMoved) {
                        //move camera
                        mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 11f))
                        cameraMoved = true
                    }

                    if (destinationMarker != null && myMarker != null) {
                        val difLat =
                            Math.abs(destinationMarker!!.position.latitude - myMarker!!.position.latitude)
                        val difLng =
                            Math.abs(destinationMarker!!.position.longitude - myMarker!!.position.longitude)

                        if (difLat < 0.0001 && difLng < 0.0001) {
                            showNotification()
                        }
                    }
                }
            }
        }
    }

    private fun showNotification() {
        val notificationBuilder = NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentTitle("You are reached!")
            .setContentText("You reached the destination location")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(0, notificationBuilder.build())
    }

    private fun checkLocationPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    ), My_PERMISSION_CODE
                )
            } else {
                ActivityCompat.requestPermissions(
                    this, arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    ), My_PERMISSION_CODE
                )
            }
            return false
        } else {
            return true
        }
    }


    //override OnRequestpermissionResults

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            My_PERMISSION_CODE -> {
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(
                            this,
                            android.Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        if (checkLocationPermission()) {

                            buildLocationRequest();
                            buildLocationcallback();

                            fusedLocationProviderClient =
                                LocationServices.getFusedLocationProviderClient(this);
                            fusedLocationProviderClient.requestLocationUpdates(
                                locationRequest,
                                locationCallback,
                                Looper.myLooper()
                            );
                            mMap?.isMyLocationEnabled = true
                        }
                    } else {
                        Toast.makeText(this, "Permission Denid", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }


    override fun onStop() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        super.onStop()
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                mMap?.isMyLocationEnabled = true
            }
        } else {
            mMap?.isMyLocationEnabled = true
        }
        mMap?.uiSettings?.isZoomControlsEnabled = true
    }

}
