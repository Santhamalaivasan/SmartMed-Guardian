package com.smartmedguardian.util

import android.content.Context
import android.os.Environment
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.smartmedguardian.data.entity.AdherenceRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ReportGenerator(private val context: Context) {

    fun generateWeeklyAdherenceReport(records: List<AdherenceRecord>): String {
        val fileName = "Adherence_Report_${System.currentTimeMillis()}.pdf"
        val filePath = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName).absolutePath
        
        try {
            val writer = PdfWriter(filePath)
            val pdf = PdfDocument(writer)
            val document = Document(pdf)
            
            document.add(Paragraph("SmartMed Guardian Adherence Report").setBold().setFontSize(18f))
            document.add(Paragraph("Generated on: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())}"))
            document.add(Paragraph("\nSummary of medication activity for the past 7 days:"))
            
            records.forEach { record ->
                val date = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(record.scheduledTime))
                document.add(Paragraph("$date - ${record.medicineName}: ${record.status}"))
            }
            
            document.close()
            return filePath
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }
}
