package com.example.wardriver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wardriver.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var wifiManager: WifiManager
    private lateinit var locationManager: LocationManager

    private val loggedRecords = mutableListOf<ApRecord>()
    private val seenBssids = mutableSetOf<String>()
    private lateinit var adapter: ApAdapter

    private var currentLocation: Location? = null
    private var isScanning = false

    private val scanHandler = Handler(Looper.getMainLooper())
    private val scanRunnable = object : Runnable {
        override fun run() {
            if (isScanning) {
                triggerWifiScan()
                scanHandler.postDelayed(this, SCAN_INTERVAL_MS)
            }
        }
    }

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                processScanResults()
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineLocationGranted) {
            startWardriving()
        } else {
            Toast.makeText(this, "Location permission is required for Wardriving", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        adapter = ApAdapter(loggedRecords)
        binding.rvApList.layoutManager = LinearLayoutManager(this)
        binding.rvApList.adapter = adapter

        binding.btnToggleScan.setOnClickListener {
            if (isScanning) {
                stopWardriving()
            } else {
                checkPermissionsAndStart()
            }
        }

        binding.btnExportCsv.setOnClickListener {
            exportLogCsv()
        }
    }

    private fun checkPermissionsAndStart() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation) {
            startWardriving()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun startWardriving() {
        isScanning = true
        binding.btnToggleScan.text = getString(R.string.stop_scanning)
        binding.tvScanStatus.text = getString(R.string.status_scanning)

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                1f,
                this
            )
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                2000L,
                5f,
                this
            )
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission error starting GPS: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        registerReceiver(wifiScanReceiver, intentFilter)

        scanHandler.post(scanRunnable)
    }

    private fun stopWardriving() {
        isScanning = false
        binding.btnToggleScan.text = getString(R.string.start_scanning)
        binding.tvScanStatus.text = getString(R.string.status_stopped)

        scanHandler.removeCallbacks(scanRunnable)
        try {
            unregisterReceiver(wifiScanReceiver)
        } catch (_: IllegalArgumentException) { }

        locationManager.removeUpdates(this)
    }

    private fun triggerWifiScan() {
        try {
            @Suppress("DEPRECATION")
            wifiManager.startScan()
        } catch (e: Exception) {
            binding.tvScanStatus.text = "Scan trigger failed: ${e.message}"
        }
    }

    private fun processScanResults() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        @Suppress("DEPRECATION")
        val results = wifiManager.scanResults ?: return
        val loc = currentLocation

        val newRecords = mutableListOf<ApRecord>()
        val lat = loc?.latitude ?: 0.0
        val lon = loc?.longitude ?: 0.0

        for (result in results) {
            val bssid = result.BSSID ?: continue
            if (!seenBssids.contains(bssid)) {
                seenBssids.add(bssid)
                @Suppress("DEPRECATION")
                val ssid = result.SSID ?: ""
                val record = ApRecord(
                    bssid = bssid,
                    ssid = ssid,
                    rssi = result.level,
                    capabilities = result.capabilities ?: "",
                    latitude = lat,
                    longitude = lon
                )
                newRecords.add(record)
            }
        }

        if (newRecords.isNotEmpty()) {
            adapter.addRecords(newRecords)
            binding.tvApCount.text = getString(R.string.ap_count_fmt, loggedRecords.size)
            binding.rvApList.smoothScrollToPosition(loggedRecords.size - 1)
        }
    }

    override fun onLocationChanged(location: Location) {
        currentLocation = location
        binding.tvGpsStatus.text = String.format(
            Locale.US,
            "GPS: %.5f, %.5f (±%.1fm)",
            location.latitude,
            location.longitude,
            location.accuracy
        )
    }

    private fun exportLogCsv() {
        if (loggedRecords.isEmpty()) {
            Toast.makeText(this, "No records to export", Toast.LENGTH_SHORT).show()
            return
        }

        val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "wardrive_$timestampStr.csv"
        val file = File(getExternalFilesDir(null), fileName)

        file.bufferedWriter().use { writer ->
            writer.write("WigleWifi-1.4,appRelease=1.0,model=Android,release=1.0\n")
            writer.write("MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type\n")
            val isoFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            for (rec in loggedRecords) {
                val timeStr = isoFormat.format(Date(rec.timestamp))
                writer.write("${rec.bssid},\"${rec.ssid}\",${rec.capabilities},$timeStr,0,${rec.rssi},${rec.latitude},${rec.longitude},0,0,WIFI\n")
            }
        }

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Export Wardrive CSV"))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isScanning) {
            stopWardriving()
        }
    }

    companion object {
        private const val SCAN_INTERVAL_MS = 10000L
    }
}
