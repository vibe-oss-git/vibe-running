package com.viberunning.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.viberunning.data.model.Activity
import com.viberunning.data.model.LocationPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object KmlExporter {

    private fun formatIsoTimestamp(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis))
    }

    fun exportToKml(
        context: Context,
        activity: Activity,
        points: List<LocationPoint>,
        useImperial: Boolean
    ): Uri? {
        if (points.isEmpty()) return null

        val kml = buildKmlString(activity, points, useImperial)

        val exportDir = File(context.cacheDir, "exports")
        exportDir.mkdirs()

        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(activity.startTime))
        val file = File(exportDir, "vibe_run_${dateStr}.kml")
        file.writeText(kml)

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun createShareIntent(uri: Uri): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.google-earth.kml+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun buildKmlString(activity: Activity, points: List<LocationPoint>, useImperial: Boolean): String {
        val startDate = FormatUtils.formatDateTime(activity.startTime)
        val distance = FormatUtils.formatDistance(activity.distanceMeters, useImperial)
        val duration = FormatUtils.formatDuration(activity.durationMillis)

        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">""")
        sb.appendLine("""<Document>""")
        sb.appendLine("""  <name>Vibe Run - $startDate</name>""")
        sb.appendLine("""  <description>Distance: $distance | Duration: $duration</description>""")

        // Style for the track line
        sb.appendLine("""  <Style id="trackStyle">""")
        sb.appendLine("""    <LineStyle>""")
        sb.appendLine("""      <color>ff0078ff</color>""")
        sb.appendLine("""      <width>4</width>""")
        sb.appendLine("""    </LineStyle>""")
        sb.appendLine("""  </Style>""")

        // Style for start pin
        sb.appendLine("""  <Style id="startStyle">""")
        sb.appendLine("""    <IconStyle>""")
        sb.appendLine("""      <color>ff00ff00</color>""")
        sb.appendLine("""      <scale>1.2</scale>""")
        sb.appendLine("""      <Icon><href>http://maps.google.com/mapfiles/kml/paddle/grn-circle.png</href></Icon>""")
        sb.appendLine("""    </IconStyle>""")
        sb.appendLine("""  </Style>""")

        // Style for end pin
        sb.appendLine("""  <Style id="endStyle">""")
        sb.appendLine("""    <IconStyle>""")
        sb.appendLine("""      <color>ff0000ff</color>""")
        sb.appendLine("""      <scale>1.2</scale>""")
        sb.appendLine("""      <Icon><href>http://maps.google.com/mapfiles/kml/paddle/red-circle.png</href></Icon>""")
        sb.appendLine("""    </IconStyle>""")
        sb.appendLine("""  </Style>""")

        // Start placemark
        val start = points.first()
        sb.appendLine("""  <Placemark>""")
        sb.appendLine("""    <name>Start</name>""")
        sb.appendLine("""    <styleUrl>#startStyle</styleUrl>""")
        sb.appendLine("""    <Point><coordinates>${start.longitude},${start.latitude},${start.altitude}</coordinates></Point>""")
        sb.appendLine("""  </Placemark>""")

        // End placemark
        val end = points.last()
        sb.appendLine("""  <Placemark>""")
        sb.appendLine("""    <name>Finish</name>""")
        sb.appendLine("""    <styleUrl>#endStyle</styleUrl>""")
        sb.appendLine("""    <Point><coordinates>${end.longitude},${end.latitude},${end.altitude}</coordinates></Point>""")
        sb.appendLine("""  </Placemark>""")

        // Track as LineString
        sb.appendLine("""  <Placemark>""")
        sb.appendLine("""    <name>Route</name>""")
        sb.appendLine("""    <styleUrl>#trackStyle</styleUrl>""")
        sb.appendLine("""    <LineString>""")
        sb.appendLine("""      <extrude>0</extrude>""")
        sb.appendLine("""      <tessellate>1</tessellate>""")
        sb.appendLine("""      <altitudeMode>clampToGround</altitudeMode>""")
        sb.appendLine("""      <coordinates>""")
        for (point in points) {
            sb.appendLine("""        ${point.longitude},${point.latitude},${point.altitude}""")
        }
        sb.appendLine("""      </coordinates>""")
        sb.appendLine("""    </LineString>""")
        sb.appendLine("""  </Placemark>""")

        // gx:Track with timestamps for animated playback
        sb.appendLine("""  <Placemark>""")
        sb.appendLine("""    <name>Timed Track</name>""")
        sb.appendLine("""    <gx:Track>""")
        sb.appendLine("""      <altitudeMode>clampToGround</altitudeMode>""")
        for (point in points) {
            sb.appendLine("""      <when>${formatIsoTimestamp(point.timestamp)}</when>""")
        }
        for (point in points) {
            sb.appendLine("""      <gx:coord>${point.longitude} ${point.latitude} ${point.altitude}</gx:coord>""")
        }
        sb.appendLine("""    </gx:Track>""")
        sb.appendLine("""  </Placemark>""")

        sb.appendLine("""</Document>""")
        sb.appendLine("""</kml>""")

        return sb.toString()
    }
}
