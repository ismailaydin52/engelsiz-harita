package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.maps.android.PolyUtil
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.Locale
import java.net.URL




class GormeengelliActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var Gmap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var destinationInput: EditText
    private var currentLocation: LatLng? = null
    private var searchMarker: Marker? = null
    private var routePolyline: Polyline? = null
    private lateinit var placesClient: PlacesClient
    private lateinit var pathDetails: TextView
    private val stepLength = 0.70 // average step length in meters
    private lateinit var textToSpeech: TextToSpeech // text to speech google entregrasyon değişkeni

    private val RQ_SPEECH_REC= 102

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gormeengelli)



        var voicelistener_btn =findViewById<Button>(R.id.talkToPush)
        voicelistener_btn.setOnClickListener {
            askspeechinput()
        }



        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech.setLanguage(Locale("tr", "TR"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "This Language is not supported")
                }
            } else {
                Log.e("TTS", "Initialization Failed!")
            }
        }

        val Fragmentmap = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        Fragmentmap.getMapAsync(this)

        destinationInput = findViewById(R.id.destinationInput)
        pathDetails = findViewById(R.id.routeDetails)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        initializePlaces()

        val ButtonDestination: Button = findViewById(R.id.destinationButton)
        ButtonDestination.setOnClickListener {
            val adres = destinationInput.text.toString()
            if (adres.isNotEmpty()) {
                searchLocation(adres)
            } else {
                Toast.makeText(this, "adres girilmedi.", Toast.LENGTH_SHORT).show()
            }
        }

        val createRoute_btn: Button = findViewById(R.id.createRouteButton)
        createRoute_btn.setOnClickListener {
            searchMarker?.position?.let { destination ->
                currentLocation?.let { origin ->
                    drawPath(origin, destination, "transit")
                }
            }
        }

        val navigationstart_btn: Button = findViewById(R.id.startNavigationButton)
        navigationstart_btn.setOnClickListener {
            navigationStart()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        var speechText = findViewById<TextView>(R.id.destinationInput)

        if (requestCode == RQ_SPEECH_REC && resultCode == Activity.RESULT_OK  && data !=null) {

            val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            speechText.text = result?.get(0).toString()
            val adres = destinationInput.text.toString()
            if (adres.isNotEmpty()) {
                searchLocation(adres) // adres vrsa arama yap

            } else {
                Toast.makeText(this, "tekrar deneyiniz.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun askspeechinput() {
        if (!SpeechRecognizer.isRecognitionAvailable( this)){
            Toast.makeText(this, "ses tanımlanamadı", Toast.LENGTH_SHORT).show()
        }
        else{
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"tr-TR")
            i.putExtra(RecognizerIntent.EXTRA_PROMPT,"konuş")
            startActivityForResult(i,RQ_SPEECH_REC)
        }

    }

    private fun initializePlaces() {
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, "AIzaSyCHvk7BoPzS4ba8AFivA_fYQa1RwmgDgc0")
        }
        placesClient = Places.createClient(this)
    }

    private fun searchLocation(address: String) {
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(address)
            .build()

        placesClient.findAutocompletePredictions(request).addOnSuccessListener { response ->
            for (prediction in response.autocompletePredictions) {
                val placeId = prediction.placeId
                val placeFields = listOf(Place.Field.LAT_LNG)
                val fetchPlaceRequest = FetchPlaceRequest.builder(placeId, placeFields).build()

                placesClient.fetchPlace(fetchPlaceRequest).addOnSuccessListener { fetchPlaceResponse ->
                    val place = fetchPlaceResponse.place
                    place.latLng?.let {
                        Gmap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 15f))
                        searchMarker?.remove()
                        searchMarker = Gmap.addMarker(MarkerOptions().position(it).title(address))

                        searchMarker?.position?.let { destination ->
                            currentLocation?.let { origin ->
                                drawPath(origin, destination, "transit")
                            }
                        } // rota çiz

                        navigationStart() // navigasyonu başlats
                    }
                }.addOnFailureListener { exception ->
                    Toast.makeText(this, "Place not found: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.addOnFailureListener { exception ->
            Toast.makeText(this, "Error finding place: ${exception.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun drawPath(origin: LatLng, dest: LatLng, mode: String) {
        val url = generateRouteUrl(origin, dest, mode)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = URL(url).readText()
                withContext(Dispatchers.Main) {
                    extractRouteInfo(result)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GormeengelliActivity, "Error in drawing route: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun generateRouteUrl(origin: LatLng, dest: LatLng, mode: String): String {
        val originStr = "origin=${origin.latitude},${origin.longitude}"
        val destStr = "destination=${dest.latitude},${dest.longitude}"
        val params = "$originStr&$destStr&sensor=false&mode=$mode"
        val output = "json"
        return "https://maps.googleapis.com/maps/api/directions/$output?$params&key=AIzaSyCHvk7BoPzS4ba8AFivA_fYQa1RwmgDgc0"
    }

    private fun extractRouteInfo(jsonData: String) {
        val jsonObject = JSONObject(jsonData)
        val routes = jsonObject.getJSONArray("routes")
        val legs = routes.getJSONObject(0).getJSONArray("legs")
        val step = legs.getJSONObject(0).getJSONArray("steps")
        routePolyline?.remove()  // Clear the previous route
        val path = mutableListOf<LatLng>()

        for (i in 0 until step.length()) {
            val step = step.getJSONObject(i)
            val travelMode = step.getString("travel_mode")
            val polyline = step.getJSONObject("polyline").getString("points")
            val segmentPath = PolyUtil.decode(polyline)

            if (travelMode == "WALKING") {
                Gmap.addPolyline(PolylineOptions().addAll(segmentPath).color(Color.BLUE).width(10f))
                routeDetailsUpdate("Yürüyerek ${segmentPath.size} adım sonra dön.")
            } else if (travelMode == "TRANSIT") {
                val transitDetails = step.getJSONObject("transit_details")
                val departureStop = transitDetails.getJSONObject("departure_stop").getString("name")
                val arrivalStop = transitDetails.getJSONObject("arrival_stop").getString("name")
                val vehicleType = transitDetails.getJSONObject("line").getJSONObject("vehicle").getString("type")
                val lineName = transitDetails.getJSONObject("line").getString("short_name")

                // Otobüs ve durak bilgilerini güncelle
                updateNavigationStatus(lineName, arrivalStop)

                Gmap.addPolyline(PolylineOptions().addAll(segmentPath).color(Color.RED).width(10f))
                routeDetailsUpdate("$departureStop'dan $arrivalStop'a $lineName $vehicleType ile gidin. $arrivalStop'ta inin.")
            }
            path.addAll(segmentPath)
        }
        routePolyline = Gmap.addPolyline(PolylineOptions().addAll(path).width(12f).color(Color.TRANSPARENT))
    }

    private fun routeDetailsUpdate(detail: String) {  // parseDirections sınıfı için gerekli update route details fonksiyonu
        pathDetails.append("\n$detail")
    }
    private var userBusNumber: String = "" // updateNavigationStatus fonksiyonunun değişkeni 1
    private var usertStop: String = "" // updateNavigationStatus fonksiyonunun değişkeni 2
    private fun updateNavigationStatus(busNumber: String, stopName: String) { // navigatePath sınıfı için gerekli update navigasyon durumu fonksiyonu
        userBusNumber = busNumber
        usertStop = stopName
    }

    private fun navigationStart() {
        if (routePolyline == null || routePolyline!!.points.isEmpty()) {
            Toast.makeText(this, "Lütfen ilk önce rota oluşturun.", Toast.LENGTH_SHORT).show()
            return
        }
        val path = routePolyline!!.points
        navigationPath(path, 0)  // Start navigating from the first point
    }

    private fun navigationPath(path: List<LatLng>, index: Int) {
        if (index < path.size) {
            val currentPoint = path[index]
            Gmap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPoint, 17f))

            if (index < path.size - 1) {
                val nextPoint = path[index + 1]
                val direction = directionCalculate(currentPoint, nextPoint)
                val distance = SphericalUtil.computeDistanceBetween(currentPoint, nextPoint)
                val stepsToNext = (distance / stepLength).toInt()
                val detailedDirection = "$stepsToNext adım sonra $direction yönüne gidin."
                Toast.makeText(this, detailedDirection, Toast.LENGTH_LONG).show()
                textToSpeech.speak(detailedDirection, TextToSpeech.QUEUE_ADD, null, null)
            }

            // Periyodik uyarılar için kod
            val periodicAnnouncement = Runnable {
                if (userBusNumber.isNotEmpty() && usertStop.isNotEmpty()) {
                    val routeInfo = "Şu anda $userBusNumber numaralı otobüste, $usertStop'a doğru ilerliyorsunuz."
                    textToSpeech.speak(routeInfo, TextToSpeech.QUEUE_ADD, null, null)
                }
            }
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed(periodicAnnouncement, 20000)  // Her 20 saniyede bir tekrarla

            if (index < path.size - 1) {
                handler.postDelayed({
                    navigationPath(path, index + 1)
                }, 3000)  // Her adım için zaman gecikmesi simüle ediliyor
            } else {
                Toast.makeText(this, "Navigasyon tamamlandı.", Toast.LENGTH_LONG).show()
                textToSpeech.speak("Navigasyon tamamlandı.", TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }



    private fun directionCalculate(from: LatLng, to: LatLng): String {
        val bearing = SphericalUtil.computeHeading(from, to)
        return when {
            bearing > -45 && bearing <= 45 -> "ileri" // Kuzey
            bearing > 45 && bearing <= 135 -> "sola" // Batı
            bearing > -135 && bearing <= -45 -> "sağa" // Doğu
            else -> "geri" // Güney
        }
    }


    override fun onMapReady(googleMap: GoogleMap) {
        Gmap = googleMap
        Gmap.uiSettings.isZoomControlsEnabled = true
        enableUserLocation()
    }

    private fun enableUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCATION_PERMISSION)
            return
        }
        Gmap.isMyLocationEnabled = true
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                currentLocation = LatLng(location.latitude, location.longitude)
                Gmap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation!!, 15f))
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableUserLocation()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {  //Text to speech boş ise konuşmasın
        if (textToSpeech != null) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }

}
