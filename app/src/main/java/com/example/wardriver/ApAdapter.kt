package com.example.wardriver

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ApAdapter(private val items: MutableList<ApRecord>) :
    RecyclerView.Adapter<ApAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSsid: TextView = view.findViewById(R.id.tvSsid)
        val tvBssid: TextView = view.findViewById(R.id.tvBssid)
        val tvSignal: TextView = view.findViewById(R.id.tvSignal)
        val tvLocation: TextView = view.findViewById(R.id.tvLocation)
        val tvSecurity: TextView = view.findViewById(R.id.tvSecurity)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ap, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvSsid.text = if (item.ssid.isBlank()) "<Hidden SSID>" else item.ssid
        holder.tvBssid.text = item.bssid
        holder.tvSignal.text = "${item.rssi} dBm"
        holder.tvSecurity.text = item.capabilities
        val timeStr = dateFormat.format(Date(item.timestamp))
        holder.tvLocation.text = String.format(
            Locale.US,
            "Lat: %.5f, Lon: %.5f [%s]",
            item.latitude,
            item.longitude,
            timeStr
        )
    }

    override fun getItemCount(): Int = items.size

    fun addRecords(newRecords: List<ApRecord>) {
        val startPos = items.size
        items.addAll(newRecords)
        notifyItemRangeInserted(startPos, newRecords.size)
    }

    fun clear() {
        val size = items.size
        items.clear()
        notifyItemRangeRemoved(0, size)
    }
}
