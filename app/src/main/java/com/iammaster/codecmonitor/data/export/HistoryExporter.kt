package com.iammaster.codecmonitor.data.export

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.iammaster.codecmonitor.data.local.HistoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat(val extension: String, val mimeType: String) {
    CSV("csv", "text/csv"),
    MARKDOWN("md", "text/markdown"),
    PDF("pdf", "application/pdf")
}

object HistoryExporter {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    private fun timestamp(row: HistoryEntity) = isoFormat.format(Date(row.t))

    fun toCsv(rows: List<HistoryEntity>): String = buildString {
        appendLine("timestamp,device,mac,codec,bitrate_kbps,battery,type")
        for (row in rows) {
            appendLine(
                listOf(
                    timestamp(row),
                    row.device.orEmpty(),
                    row.mac.orEmpty(),
                    row.codec.orEmpty(),
                    row.bitrateKbps?.toString().orEmpty(),
                    row.battery?.toString().orEmpty(),
                    row.type.orEmpty()
                ).joinToString(",") { field -> if (field.contains(",")) "\"$field\"" else field }
            )
        }
    }

    fun toMarkdown(rows: List<HistoryEntity>): String = buildString {
        appendLine("# Codec Monitor report")
        appendLine()
        val bitrates = rows.mapNotNull { it.bitrateKbps }
        if (bitrates.isNotEmpty()) {
            appendLine("Min: ${bitrates.min()} kbps · Avg: ${bitrates.average().toInt()} kbps · Max: ${bitrates.max()} kbps")
            appendLine()
        }
        appendLine("| Timestamp | Device | Codec | Bitrate | Battery | Type |")
        appendLine("|---|---|---|---|---|---|")
        for (row in rows) {
            appendLine(
                "| ${timestamp(row)} | ${row.device.orEmpty()} | ${row.codec.orEmpty()} | " +
                    "${row.bitrateKbps?.toString().orEmpty()} | ${row.battery?.toString().orEmpty()} | ${row.type.orEmpty()} |"
            )
        }
    }

    fun toPdf(rows: List<HistoryEntity>): ByteArray {
        val document = PdfDocument()
        val pageWidth = 595 // A4 at 72dpi
        val pageHeight = 842
        val margin = 36f
        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true }
        val headerPaint = Paint().apply { textSize = 10f; isFakeBoldText = true }
        val rowPaint = Paint().apply { textSize = 9f }
        val colX = floatArrayOf(margin, margin + 150, margin + 260, margin + 330, margin + 400, margin + 460)
        val headers = listOf("Timestamp", "Device", "Codec", "Bitrate", "Battery", "Type")

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin

        canvas.drawText("Codec Monitor report", margin, y + 18f, titlePaint)
        y += 40f
        headers.forEachIndexed { i, h -> canvas.drawText(h, colX[i], y, headerPaint) }
        y += 16f

        for (row in rows) {
            if (y > pageHeight - margin) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = margin
                headers.forEachIndexed { i, h -> canvas.drawText(h, colX[i], y, headerPaint) }
                y += 16f
            }
            val values = listOf(
                timestamp(row), row.device.orEmpty(), row.codec.orEmpty(),
                row.bitrateKbps?.toString().orEmpty(), row.battery?.toString().orEmpty(), row.type.orEmpty()
            )
            values.forEachIndexed { i, v -> canvas.drawText(v, colX[i], y, rowPaint) }
            y += 14f
        }
        document.finishPage(page)

        val output = java.io.ByteArrayOutputStream()
        document.writeTo(output)
        document.close()
        return output.toByteArray()
    }

    fun export(rows: List<HistoryEntity>, format: ExportFormat): ByteArray = when (format) {
        ExportFormat.CSV -> toCsv(rows).toByteArray()
        ExportFormat.MARKDOWN -> toMarkdown(rows).toByteArray()
        ExportFormat.PDF -> toPdf(rows)
    }
}
