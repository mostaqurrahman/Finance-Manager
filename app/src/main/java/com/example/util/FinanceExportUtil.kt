package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.model.Account
import com.example.data.model.Transaction
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFormat(
    val extension: String,
    val mimeType: String
) {
    CSV("csv", "text/csv"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
}

data class ExportOutcome(
    val fileName: String,
    val recordCount: Int,
    val savedToDownloads: Boolean,
    val shareUri: Uri,
    val mimeType: String
)

object FinanceExportUtil {

    fun generateTransactionsCsvBytes(transactions: List<Transaction>): ByteArray {
        val sb = StringBuilder()
        sb.appendLine("Date,Time,Type,Amount (BDT),Account,To Account,Category,Person,Description")

        val sorted = transactions.sortedByDescending { it.date + (it.time ?: "") }
        for (tx in sorted) {
            val fields = listOf(
                tx.date,
                tx.time ?: "",
                tx.type,
                formatAmount(tx.amount),
                tx.accountName ?: tx.accountId,
                tx.toAccountName ?: tx.toAccountId ?: "",
                tx.categoryName ?: "",
                tx.personName ?: "",
                tx.description ?: ""
            )
            sb.appendLine(fields.joinToString(",") { escapeCsvField(it) })
        }

        // Prepend UTF-8 BOM so Microsoft Excel and Google Sheets render Bangla text properly
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val contentBytes = sb.toString().toByteArray(StandardCharsets.UTF_8)
        return bom + contentBytes
    }

    fun generateTransactionsXlsxBytes(
        transactions: List<Transaction>,
        accounts: List<Account>,
        summary: FinancialSummary
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            writeZipEntry(
                zos,
                "[Content_Types].xml",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                </Types>
                """.trimIndent()
            )

            writeZipEntry(
                zos,
                "_rels/.rels",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """.trimIndent()
            )

            writeZipEntry(
                zos,
                "xl/workbook.xml",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="Transactions" sheetId="1" r:id="rId1"/>
                    <sheet name="Summary" sheetId="2" r:id="rId2"/>
                  </sheets>
                </workbook>
                """.trimIndent()
            )

            writeZipEntry(
                zos,
                "xl/_rels/workbook.xml.rels",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
                  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                </Relationships>
                """.trimIndent()
            )

            writeZipEntry(
                zos,
                "xl/styles.xml",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <fonts count="2">
                    <font><sz val="11"/><name val="Calibri"/></font>
                    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
                  </fonts>
                  <fills count="3">
                    <fill><patternFill patternType="none"/></fill>
                    <fill><patternFill patternType="gray125"/></fill>
                    <fill><patternFill patternType="solid"><fgColor rgb="FF059669"/></patternFill></fill>
                  </fills>
                  <borders count="1">
                    <border><left/><right/><top/><bottom/><diagonal/></border>
                  </borders>
                  <cellStyleXfs count="1">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
                  </cellStyleXfs>
                  <cellXfs count="2">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
                  </cellXfs>
                </styleSheet>
                """.trimIndent()
            )

            writeZipEntry(zos, "xl/worksheets/sheet1.xml", buildTransactionsSheetXml(transactions))
            writeZipEntry(zos, "xl/worksheets/sheet2.xml", buildSummarySheetXml(accounts, summary, transactions.size))
        }
        return baos.toByteArray()
    }

    fun exportTransactions(
        context: Context,
        format: ExportFormat,
        transactions: List<Transaction>,
        accounts: List<Account>,
        summary: FinancialSummary
    ): Result<ExportOutcome> = runCatching {
        val dateStamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val fileName = "HisabKhata_Transactions_$dateStamp.${format.extension}"
        val bytes = when (format) {
            ExportFormat.CSV -> generateTransactionsCsvBytes(transactions)
            ExportFormat.XLSX -> generateTransactionsXlsxBytes(transactions, accounts, summary)
        }

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val cacheFile = File(exportDir, fileName)
        cacheFile.writeBytes(bytes)
        val shareUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )

        val savedToDownloads = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveBytesToMediaStoreDownloads(context, fileName, format.mimeType, bytes)
        } else {
            false
        }

        ExportOutcome(
            fileName = fileName,
            recordCount = transactions.size,
            savedToDownloads = savedToDownloads,
            shareUri = shareUri,
            mimeType = format.mimeType
        )
    }

    fun createShareIntent(outcome: ExportOutcome, chooserTitle: String): Intent {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = outcome.mimeType
            putExtra(Intent.EXTRA_STREAM, outcome.shareUri)
            putExtra(Intent.EXTRA_SUBJECT, outcome.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, chooserTitle)
    }

    fun createShareOutcomesIntent(outcomes: List<ExportOutcome>, chooserTitle: String): Intent {
        if (outcomes.size <= 1) {
            return createShareIntent(outcomes.first(), chooserTitle)
        }
        val uris = ArrayList<Uri>(outcomes.map { it.shareUri })
        val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, outcomes.joinToString(", ") { it.fileName })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, chooserTitle)
    }

    /**
     * Generates ONE complete multi-sheet OOXML .xlsx workbook for the unified report.
     */
    fun generateUnifiedReportXlsxBytes(report: UnifiedReportData): ByteArray {
        val sheets = buildUnifiedSheets(report)
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            val sheetOverrides = (1..sheets.size).joinToString("\n") { i ->
                """  <Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
            }
            writeZipEntry(
                zos,
                "[Content_Types].xml",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                $sheetOverrides
                  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                </Types>
                """.trimIndent()
            )

            writeZipEntry(
                zos,
                "_rels/.rels",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """.trimIndent()
            )

            val sheetEntries = sheets.mapIndexed { idx, spec ->
                val n = idx + 1
                """    <sheet name="${escapeXml(spec.name)}" sheetId="$n" r:id="rId$n"/>"""
            }.joinToString("\n")
            writeZipEntry(
                zos,
                "xl/workbook.xml",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                $sheetEntries
                  </sheets>
                </workbook>
                """.trimIndent()
            )

            val relEntries = sheets.indices.joinToString("\n") { idx ->
                val n = idx + 1
                """  <Relationship Id="rId$n" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$n.xml"/>"""
            }
            val stylesRelId = "rId${sheets.size + 1}"
            writeZipEntry(
                zos,
                "xl/_rels/workbook.xml.rels",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                $relEntries
                  <Relationship Id="$stylesRelId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                </Relationships>
                """.trimIndent()
            )

            writeZipEntry(
                zos,
                "xl/styles.xml",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <fonts count="2">
                    <font><sz val="11"/><name val="Calibri"/></font>
                    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
                  </fonts>
                  <fills count="3">
                    <fill><patternFill patternType="none"/></fill>
                    <fill><patternFill patternType="gray125"/></fill>
                    <fill><patternFill patternType="solid"><fgColor rgb="FF059669"/></fgColor></fill>
                  </fills>
                  <borders count="1">
                    <border><left/><right/><top/><bottom/><diagonal/></border>
                  </borders>
                  <cellStyleXfs count="1">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
                  </cellStyleXfs>
                  <cellXfs count="2">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
                  </cellXfs>
                </styleSheet>
                """.trimIndent()
            )

            sheets.forEachIndexed { idx, spec ->
                writeZipEntry(zos, "xl/worksheets/sheet${idx + 1}.xml", spec.xml)
            }
        }
        return baos.toByteArray()
    }

    /**
     * Generates ONE complete multi-page A4 PDF document with Bengali Unicode support,
     * summary cards, chart, all 14 sections, account statements, and Page X of Y footers.
     */
    fun generateUnifiedReportPdfBytes(context: Context, report: UnifiedReportData): ByteArray {
        val pageWidth = 595
        val pageHeight = 842
        val marginX = 32f
        val contentWidth = pageWidth - marginX * 2f
        val topMargin = 36f
        val bottomLimit = pageHeight - 44f

        val recordedPages = mutableListOf<Picture>()
        var currentPicture = Picture()
        var canvas = currentPicture.beginRecording(pageWidth, pageHeight)
        var y = topMargin

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(209, 250, 229)
            textSize = 9.5f
        }
        val sectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 8.5f
        }
        val mutedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 8f
        }
        val headerCellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 8.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
            color = Color.rgb(226, 232, 240)
        }

        fun startNewPage() {
            currentPicture.endRecording()
            recordedPages.add(currentPicture)
            currentPicture = Picture()
            canvas = currentPicture.beginRecording(pageWidth, pageHeight)
            y = topMargin
            // Subtle continuation header
            canvas.drawText(
                "HisabKhata • ${report.periodType.labelEn} (${report.periodLabelEn})",
                marginX,
                22f,
                mutedPaint
            )
            fillPaint.color = Color.rgb(226, 232, 240)
            canvas.drawRect(marginX, 27f, pageWidth - marginX, 27.6f, fillPaint)
            y = 38f
        }

        fun ensureSpace(neededHeight: Float) {
            if (y + neededHeight > bottomLimit) {
                startNewPage()
            }
        }

        fun drawWrappedText(text: String, x: Float, yPos: Float, width: Float, paint: TextPaint): Float {
            val layout = StaticLayout.Builder
                .obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(24))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.05f)
                .build()
            canvas.save()
            canvas.translate(x, yPos)
            layout.draw(canvas)
            canvas.restore()
            return layout.height.toFloat()
        }

        fun drawSectionHeader(title: String) {
            ensureSpace(28f)
            y += 6f
            fillPaint.color = Color.rgb(5, 150, 105)
            canvas.drawRect(marginX, y, marginX + 3.5f, y + 14f, fillPaint)
            canvas.drawText(title, marginX + 9f, y + 11.5f, sectionPaint)
            y += 20f
        }

        fun drawTable(
            headers: List<String>,
            rows: List<List<String>>,
            colWeights: List<Float>,
            headerColor: Int = Color.rgb(5, 150, 105)
        ) {
            val totalWeight = colWeights.sum().coerceAtLeast(1f)
            val colWidths = colWeights.map { (it / totalWeight) * contentWidth }

            fun drawTableHeader() {
                ensureSpace(22f)
                fillPaint.color = headerColor
                canvas.drawRect(marginX, y, marginX + contentWidth, y + 18f, fillPaint)
                var cx = marginX
                headers.forEachIndexed { idx, h ->
                    val w = colWidths[idx]
                    val clipped = if (h.length > 26) h.take(24) + ".." else h
                    canvas.drawText(clipped, cx + 4f, y + 12.5f, headerCellPaint)
                    cx += w
                }
                y += 18f
            }

            drawTableHeader()
            val safeRows = if (rows.isEmpty()) listOf(List(headers.size) { i -> if (i == 0) "No records in this period" else "-" }) else rows

            safeRows.forEachIndexed { rIdx, row ->
                var maxCellH = 15f
                val layouts = row.mapIndexed { cIdx, cellText ->
                    val w = (colWidths[cIdx] - 8f).toInt().coerceAtLeast(20)
                    val safe = cellText.ifBlank { "-" }
                    val sl = StaticLayout.Builder
                        .obtain(safe, 0, safe.length, bodyPaint, w)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .build()
                    val h = sl.height + 7f
                    if (h > maxCellH) maxCellH = h
                    sl
                }

                if (y + maxCellH > bottomLimit) {
                    startNewPage()
                    drawTableHeader()
                }

                fillPaint.color = if (rIdx % 2 == 0) Color.WHITE else Color.rgb(248, 250, 252)
                canvas.drawRect(marginX, y, marginX + contentWidth, y + maxCellH, fillPaint)
                canvas.drawRect(marginX, y, marginX + contentWidth, y + maxCellH, borderPaint)

                var cx = marginX
                layouts.forEachIndexed { cIdx, sl ->
                    canvas.save()
                    canvas.translate(cx + 4f, y + 3.5f)
                    sl.draw(canvas)
                    canvas.restore()
                    cx += colWidths[cIdx]
                }
                y += maxCellH
            }
            y += 8f
        }

        // Cover Banner
        fillPaint.color = Color.rgb(5, 150, 105)
        canvas.drawRoundRect(RectF(marginX, y, marginX + contentWidth, y + 66f), 8f, 8f, fillPaint)
        canvas.drawText(
            "HisabKhata • Complete ${report.periodType.labelEn}",
            marginX + 14f,
            y + 22f,
            titlePaint
        )
        canvas.drawText(
            "Period: ${report.periodLabelEn} (${report.startDate} to ${report.endDate}) • ${report.transactionCount} Transactions",
            marginX + 14f,
            y + 39f,
            subtitlePaint
        )
        canvas.drawText(
            "Account Holder: ${report.ownerName}${if (report.ownerEmail.isNotBlank()) " (${report.ownerEmail})" else ""} • Generated: ${report.generatedAt}",
            marginX + 14f,
            y + 53f,
            subtitlePaint
        )
        y += 78f

        // KPI Summary Cards (4 columns)
        val cardGap = 8f
        val cardW = (contentWidth - cardGap * 3f) / 4f
        val cardItems = listOf(
            Triple("Period Income", FinancialEngine.formatBdt(report.periodIncome), Color.rgb(5, 150, 105)),
            Triple("Net Expense", FinancialEngine.formatBdt(report.periodNetExpense), Color.rgb(220, 38, 38)),
            Triple("Net Cash Flow", FinancialEngine.formatBdt(report.periodNetCashFlow), Color.rgb(15, 23, 42)),
            Triple("Net Worth", FinancialEngine.formatBdt(report.netWorth), Color.rgb(13, 148, 136))
        )
        cardItems.forEachIndexed { idx, (label, valStr, accent) ->
            val cx = marginX + idx * (cardW + cardGap)
            fillPaint.color = Color.rgb(248, 250, 252)
            canvas.drawRoundRect(RectF(cx, y, cx + cardW, y + 46f), 6f, 6f, fillPaint)
            canvas.drawRoundRect(RectF(cx, y, cx + cardW, y + 46f), 6f, 6f, borderPaint)
            canvas.drawText(label, cx + 8f, y + 16f, mutedPaint)
            val valPaint = TextPaint(sectionPaint).apply {
                color = accent
                textSize = 10.5f
            }
            canvas.drawText(valStr, cx + 8f, y + 34f, valPaint)
        }
        y += 58f

        // Vector Bar Chart: Income vs Gross Expense vs Net Expense vs Refunds
        drawSectionHeader("1. Executive Cash Flow & Balance Sheet Visual")
        ensureSpace(78f)
        val maxBarVal = maxOf(report.periodIncome, report.periodGrossExpense, report.periodNetExpense, 1.0)
        val bars = listOf(
            Triple("Income", report.periodIncome, Color.rgb(5, 150, 105)),
            Triple("Gross Expense", report.periodGrossExpense, Color.rgb(244, 63, 94)),
            Triple("Net Expense", report.periodNetExpense, Color.rgb(220, 38, 38)),
            Triple("Refunds", report.periodRefunds, Color.rgb(37, 99, 235))
        )
        val barTrackW = contentWidth - 190f
        bars.forEach { (label, amount, barColor) ->
            canvas.drawText(label, marginX, y + 10f, bodyPaint)
            fillPaint.color = Color.rgb(241, 245, 249)
            canvas.drawRoundRect(RectF(marginX + 95f, y + 2f, marginX + 95f + barTrackW, y + 11f), 4f, 4f, fillPaint)
            val fillW = ((amount / maxBarVal) * barTrackW).toFloat().coerceAtLeast(if (amount > 0) 4f else 0f)
            if (fillW > 0f) {
                fillPaint.color = barColor
                canvas.drawRoundRect(RectF(marginX + 95f, y + 2f, marginX + 95f + fillW, y + 11f), 4f, 4f, fillPaint)
            }
            canvas.drawText(FinancialEngine.formatBdt(amount), marginX + 102f + barTrackW, y + 10f, bodyPaint)
            y += 16f
        }
        y += 6f

        // 2. Overall Financial Summary Table
        drawSectionHeader("2. Overall Income, Expense, Refund & Balance Sheet Summary")
        drawTable(
            headers = listOf("Financial Metric", "Amount (BDT)", "Notes"),
            rows = listOf(
                listOf("Period Total Income", FinancialEngine.formatBdt(report.periodIncome), "All income transactions in period"),
                listOf("Period Gross Expense", FinancialEngine.formatBdt(report.periodGrossExpense), "Expenses before refunds"),
                listOf("Period Refunds Received", FinancialEngine.formatBdt(report.periodRefunds), "Deducted from gross expense"),
                listOf("Period Net Expense", FinancialEngine.formatBdt(report.periodNetExpense), "Gross Expense - Refunds"),
                listOf("Period Net Cash Flow", FinancialEngine.formatBdt(report.periodNetCashFlow), "Savings Rate: ${report.savingsRatePercent}%"),
                listOf("Internal Transfers", "${report.transferCount} transfers", "Volume ${FinancialEngine.formatBdt(report.transferVolume)} (neutral)"),
                listOf("Available Liquidity", FinancialEngine.formatBdt(report.availableLiquidity), "Cash + Bank + MFS"),
                listOf("Total Savings & DPS/FDR", FinancialEngine.formatBdt(report.totalSavings), "Savings accounts balance"),
                listOf("Loan Receivable (Given)", FinancialEngine.formatBdt(report.loanGivenOutstanding), "Outstanding due from borrowers"),
                listOf("Loan Payable (Taken)", FinancialEngine.formatBdt(report.loanTakenOutstanding), "Outstanding debt payable"),
                listOf("Total Assets / Liabilities", FinancialEngine.formatBdt(report.totalAssets), "Liabilities: ${FinancialEngine.formatBdt(report.totalLiabilities)}"),
                listOf("Net Worth", FinancialEngine.formatBdt(report.netWorth), "Total Assets - Total Liabilities")
            ),
            colWeights = listOf(2.4f, 1.5f, 3.0f)
        )

        // 3. Transaction Type Summary
        drawSectionHeader("3. Overall Financial Transaction Summary by Type")
        drawTable(
            headers = listOf("Transaction Type", "Count", "Total Amount"),
            rows = report.typeSummaries.map {
                listOf(it.type, it.count.toString(), FinancialEngine.formatBdt(it.totalAmount))
            },
            colWeights = listOf(3f, 1f, 2f),
            headerColor = Color.rgb(30, 41, 59)
        )

        // 4. Account-wise Summary
        drawSectionHeader("4. Account-wise Summary (Opening, Inflows, Outflows, Closing)")
        drawTable(
            headers = listOf("Account", "Type", "Opening Bal.", "Money In", "Money Out", "Closing Bal."),
            rows = report.accountSummaries.map {
                listOf(
                    it.accountName,
                    it.accountType,
                    FinancialEngine.formatBdt(it.periodOpeningBalance),
                    FinancialEngine.formatBdt(it.moneyIn),
                    FinancialEngine.formatBdt(it.moneyOut),
                    FinancialEngine.formatBdt(it.periodClosingBalance)
                )
            },
            colWeights = listOf(2f, 1.3f, 1.4f, 1.3f, 1.3f, 1.4f)
        )

        // 5. Trend Analysis
        val trendTitle = if (report.periodType == ReportPeriodType.MONTHLY) {
            "5. Daily Income & Expense Analysis (${report.periodLabelEn})"
        } else {
            "5. Monthly Income & Expense Analysis (${report.periodLabelEn})"
        }
        drawSectionHeader(trendTitle)
        drawTable(
            headers = listOf("Period", "Count", "Income", "Net Expense", "Net Cash Flow"),
            rows = report.trendBuckets.map {
                listOf(
                    it.label,
                    it.txCount.toString(),
                    FinancialEngine.formatBdt(it.income),
                    FinancialEngine.formatBdt(it.netExpense),
                    FinancialEngine.formatBdt(it.netCashFlow)
                )
            },
            colWeights = listOf(2f, 0.8f, 1.4f, 1.4f, 1.5f),
            headerColor = Color.rgb(30, 41, 59)
        )

        // 6. Person-wise Summary & Details
        drawSectionHeader("6. Person-wise Summary & Expense Attribution")
        drawTable(
            headers = listOf("Person", "Relationship", "Tx", "Expense", "Income / Other", "Share"),
            rows = report.personSummaries.map {
                listOf(
                    it.personName,
                    it.relationship,
                    it.txCount.toString(),
                    FinancialEngine.formatBdt(it.totalExpense),
                    FinancialEngine.formatBdt(it.totalIncome + it.totalOtherFlow),
                    "${it.expenseSharePercent}%"
                )
            },
            colWeights = listOf(2f, 1.4f, 0.7f, 1.3f, 1.4f, 0.9f),
            headerColor = Color.rgb(37, 99, 235)
        )

        if (report.personDetails.isNotEmpty()) {
            drawSectionHeader("7. Person-wise Detailed Transactions")
            drawTable(
                headers = listOf("Date", "Person", "Category", "Account", "Amount", "Description"),
                rows = report.personDetails.map {
                    listOf(
                        it.date,
                        it.personName,
                        it.categoryName,
                        it.accountName,
                        FinancialEngine.formatBdt(it.amount),
                        it.description.take(28)
                    )
                },
                colWeights = listOf(1.2f, 1.6f, 1.4f, 1.4f, 1.2f, 1.8f),
                headerColor = Color.rgb(37, 99, 235)
            )
        }

        // 8. Category-wise Summary & Details
        drawSectionHeader("8. Category-wise Income & Expense Summary")
        drawTable(
            headers = listOf("Type", "Category", "Tx Count", "Amount", "Share"),
            rows = report.categorySummaries.map {
                listOf(
                    it.type,
                    it.categoryName,
                    it.txCount.toString(),
                    FinancialEngine.formatBdt(it.amount),
                    "${it.sharePercent}%"
                )
            },
            colWeights = listOf(1.1f, 2.5f, 0.9f, 1.5f, 1f),
            headerColor = Color.rgb(225, 29, 72)
        )

        if (report.categoryDetails.isNotEmpty()) {
            drawSectionHeader("9. Category-wise Detailed Transaction Analysis")
            drawTable(
                headers = listOf("Date", "Category", "Person", "Account", "Amount", "Description"),
                rows = report.categoryDetails.map {
                    listOf(
                        it.date,
                        it.categoryName,
                        it.personName,
                        it.accountName,
                        FinancialEngine.formatBdt(it.amount),
                        it.description.take(28)
                    )
                },
                colWeights = listOf(1.2f, 1.6f, 1.3f, 1.3f, 1.2f, 1.8f),
                headerColor = Color.rgb(225, 29, 72)
            )
        }

        // 10. Loans, Savings, Assets & Liabilities
        drawSectionHeader("10. Loans, Savings, Assets & Liabilities Summary")
        drawTable(
            headers = listOf("Type", "Party", "Principal", "Paid", "Remaining", "Status"),
            rows = report.loanRows.map {
                listOf(
                    it.type,
                    it.partyName,
                    FinancialEngine.formatBdt(it.principal),
                    FinancialEngine.formatBdt(it.paidAmount),
                    FinancialEngine.formatBdt(it.remainingAmount),
                    it.status
                )
            },
            colWeights = listOf(1f, 2.2f, 1.3f, 1.2f, 1.3f, 1.1f),
            headerColor = Color.rgb(79, 70, 229)
        )

        drawTable(
            headers = listOf("Class", "Item Name", "Category", "Original / Target", "Current / Remaining", "Status"),
            rows = (report.assetItems + report.liabilityItems).map {
                listOf(
                    it.classification,
                    it.name,
                    it.category,
                    FinancialEngine.formatBdt(it.originalOrTarget),
                    FinancialEngine.formatBdt(it.currentOrRemaining),
                    it.status
                )
            },
            colWeights = listOf(1f, 2.2f, 1.4f, 1.4f, 1.4f, 1f),
            headerColor = Color.rgb(13, 148, 136)
        )

        // 11. Separate Detailed Statement for Every Account
        drawSectionHeader("11. Detailed Statements for All Accounts (${report.accountStatements.size})")
        for (stmt in report.accountStatements) {
            ensureSpace(54f)
            y += drawWrappedText(
                "${stmt.accountName} (${stmt.accountType}) — Opening: ${FinancialEngine.formatBdt(stmt.openingBalance)} | In: ${FinancialEngine.formatBdt(stmt.totalMoneyIn)} | Out: ${FinancialEngine.formatBdt(stmt.totalMoneyOut)} | Closing: ${FinancialEngine.formatBdt(stmt.closingBalance)}",
                marginX,
                y,
                contentWidth,
                TextPaint(bodyPaint).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            ) + 4f

            val stmtRows = buildList {
                add(
                    listOf(
                        report.startDate,
                        "OPENING",
                        "Brought forward",
                        "-",
                        "-",
                        FinancialEngine.formatBdt(stmt.openingBalance)
                    )
                )
                for (entry in stmt.entries) {
                    add(
                        listOf(
                            entry.date,
                            entry.type,
                            entry.counterpartyOrDetail.take(26),
                            if (entry.moneyIn > 0) FinancialEngine.formatBdt(entry.moneyIn) else "-",
                            if (entry.moneyOut > 0) FinancialEngine.formatBdt(entry.moneyOut) else "-",
                            FinancialEngine.formatBdt(entry.runningBalance)
                        )
                    )
                }
                add(
                    listOf(
                        report.endDate,
                        "CLOSING",
                        "${stmt.entries.size} entries",
                        FinancialEngine.formatBdt(stmt.totalMoneyIn),
                        FinancialEngine.formatBdt(stmt.totalMoneyOut),
                        FinancialEngine.formatBdt(stmt.closingBalance)
                    )
                )
            }
            drawTable(
                headers = listOf("Date", "Type", "Detail / Counterparty", "Money In", "Money Out", "Balance"),
                rows = stmtRows,
                colWeights = listOf(1.2f, 1.3f, 2.4f, 1.2f, 1.2f, 1.3f)
            )
        }

        // 12. All Transactions in Period
        drawSectionHeader("12. All Transaction Records (${report.transactions.size})")
        drawTable(
            headers = listOf("Date", "Type", "Amount", "Account", "Category / Person", "Description"),
            rows = report.transactions.map { tx ->
                val accDisplay = if (tx.toAccountName.isNotBlank()) {
                    "${tx.accountName} -> ${tx.toAccountName}"
                } else {
                    tx.accountName
                }
                val meta = listOfNotNull(
                    tx.categoryName.takeIf { it.isNotBlank() },
                    tx.personName.takeIf { it.isNotBlank() }
                ).joinToString(" • ").ifBlank { "-" }
                listOf(
                    tx.date,
                    tx.type,
                    FinancialEngine.formatBdt(tx.amount),
                    accDisplay,
                    meta,
                    tx.description.take(28)
                )
            },
            colWeights = listOf(1.1f, 1.2f, 1.1f, 1.6f, 1.6f, 1.8f),
            headerColor = Color.rgb(15, 23, 42)
        )

        // Close last recording
        currentPicture.endRecording()
        recordedPages.add(currentPicture)

        // Render recorded vector Pictures into PdfDocument with Page X of Y footers
        val totalPages = recordedPages.size
        val pdfDocument = PdfDocument()
        try {
            recordedPages.forEachIndexed { idx, picture ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, idx + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val pCanvas: Canvas = page.canvas
                pCanvas.drawColor(Color.WHITE)
                pCanvas.drawPicture(picture)

                // Footer with reporting period and Page X of Y
                fillPaint.color = Color.rgb(226, 232, 240)
                pCanvas.drawRect(marginX, pageHeight - 28f, pageWidth - marginX, pageHeight - 27.3f, fillPaint)
                pCanvas.drawText(
                    "HisabKhata • ${report.periodLabelEn} (${report.startDate} to ${report.endDate})",
                    marginX,
                    pageHeight - 14f,
                    mutedPaint
                )
                val pageStr = "Page ${idx + 1} of $totalPages"
                val pw = mutedPaint.measureText(pageStr)
                pCanvas.drawText(pageStr, pageWidth - marginX - pw, pageHeight - 14f, mutedPaint)

                pdfDocument.finishPage(page)
            }
            val baos = ByteArrayOutputStream()
            pdfDocument.writeTo(baos)
            return baos.toByteArray()
        } finally {
            pdfDocument.close()
        }
    }

    /**
     * Unified export entry point producing PDF, Excel (.xlsx), or Both (two separate files).
     */
    fun exportUnifiedReport(
        context: Context,
        format: ReportExportFormat,
        report: UnifiedReportData
    ): Result<List<ExportOutcome>> = runCatching {
        val outcomes = mutableListOf<ExportOutcome>()
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }

        if (format == ReportExportFormat.PDF || format == ReportExportFormat.BOTH) {
            val pdfName = UnifiedReportBuilder.buildFileName(report.periodType, report.periodKey, "pdf")
            val pdfBytes = generateUnifiedReportPdfBytes(context, report)
            val cacheFile = File(exportDir, pdfName).apply { writeBytes(pdfBytes) }
            val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
            val saved = saveBytesToMediaStoreDownloads(context, pdfName, "application/pdf", pdfBytes)
            outcomes += ExportOutcome(
                fileName = pdfName,
                recordCount = report.transactionCount,
                savedToDownloads = saved,
                shareUri = shareUri,
                mimeType = "application/pdf"
            )
        }

        if (format == ReportExportFormat.XLSX || format == ReportExportFormat.BOTH) {
            val xlsxName = UnifiedReportBuilder.buildFileName(report.periodType, report.periodKey, "xlsx")
            val xlsxBytes = generateUnifiedReportXlsxBytes(report)
            val cacheFile = File(exportDir, xlsxName).apply { writeBytes(xlsxBytes) }
            val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
            val mime = ExportFormat.XLSX.mimeType
            val saved = saveBytesToMediaStoreDownloads(context, xlsxName, mime, xlsxBytes)
            outcomes += ExportOutcome(
                fileName = xlsxName,
                recordCount = report.transactionCount,
                savedToDownloads = saved,
                shareUri = shareUri,
                mimeType = mime
            )
        }

        outcomes
    }

    private data class SheetSpec(val name: String, val xml: String)

    private fun sanitizeExcelSheetName(raw: String, existing: Set<String>): String {
        val cleaned = raw.replace(Regex("[\\\\/?*\\[\\]:]"), " ").trim().ifBlank { "Sheet" }
        var candidate = cleaned.take(31)
        var counter = 2
        while (existing.contains(candidate)) {
            val suffix = " ($counter)"
            candidate = cleaned.take((31 - suffix.length).coerceAtLeast(1)) + suffix
            counter++
        }
        return candidate
    }

    private fun buildSheetXml(
        colWidths: List<Int>,
        headers: List<String>,
        rows: List<List<CellSpec>>
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        if (colWidths.isNotEmpty()) {
            sb.append("<cols>")
            colWidths.forEachIndexed { i, w ->
                val c = i + 1
                sb.append("""<col min="$c" max="$c" width="$w" customWidth="1"/>""")
            }
            sb.append("</cols>")
        }
        sb.append("<sheetData>")
        sb.append(buildTextRow(1, headers, styleIndex = 1))
        if (rows.isEmpty()) {
            sb.append(buildMixedRow(2, listOf(CellSpec.Text("No records in selected period"))))
        } else {
            rows.forEachIndexed { idx, row ->
                sb.append(buildMixedRow(idx + 2, row))
            }
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun buildUnifiedSheets(report: UnifiedReportData): List<SheetSpec> {
        val sheets = mutableListOf<SheetSpec>()
        val usedNames = linkedSetOf<String>()

        fun addSheet(desiredName: String, colWidths: List<Int>, headers: List<String>, rows: List<List<CellSpec>>) {
            val unique = sanitizeExcelSheetName(desiredName, usedNames)
            usedNames += unique
            sheets += SheetSpec(unique, buildSheetXml(colWidths, headers, rows))
        }

        // 1. Executive Summary
        addSheet(
            "Executive Summary",
            listOf(28, 36, 20),
            listOf("Section / Metric", "Details", "Value (BDT / Count)"),
            listOf(
                listOf(CellSpec.Text("Report Period"), CellSpec.Text("${report.periodType.labelEn}: ${report.periodLabelEn}"), CellSpec.Text("${report.startDate} to ${report.endDate}")),
                listOf(CellSpec.Text("Account Holder"), CellSpec.Text(report.ownerName), CellSpec.Text(report.ownerEmail)),
                listOf(CellSpec.Text("Generated At"), CellSpec.Text(report.generatedAt), CellSpec.Number(report.transactionCount.toDouble())),
                listOf(CellSpec.Text("Period Total Income"), CellSpec.Text("Genuine income"), CellSpec.Number(report.periodIncome)),
                listOf(CellSpec.Text("Period Gross Expense"), CellSpec.Text("Before refunds"), CellSpec.Number(report.periodGrossExpense)),
                listOf(CellSpec.Text("Period Refunds"), CellSpec.Text("Returned/refunded"), CellSpec.Number(report.periodRefunds)),
                listOf(CellSpec.Text("Period Net Expense"), CellSpec.Text("Gross Expense - Refunds"), CellSpec.Number(report.periodNetExpense)),
                listOf(CellSpec.Text("Period Net Cash Flow"), CellSpec.Text("Savings Rate ${report.savingsRatePercent}%"), CellSpec.Number(report.periodNetCashFlow)),
                listOf(CellSpec.Text("Available Liquidity"), CellSpec.Text("Cash + Bank + MFS"), CellSpec.Number(report.availableLiquidity)),
                listOf(CellSpec.Text("Total Savings"), CellSpec.Text("Savings & DPS/FDR"), CellSpec.Number(report.totalSavings)),
                listOf(CellSpec.Text("Loan Receivable (Given)"), CellSpec.Text("Outstanding"), CellSpec.Number(report.loanGivenOutstanding)),
                listOf(CellSpec.Text("Loan Payable (Taken)"), CellSpec.Text("Outstanding"), CellSpec.Number(report.loanTakenOutstanding)),
                listOf(CellSpec.Text("Total Assets"), CellSpec.Text("Accounts + Goals + Receivable"), CellSpec.Number(report.totalAssets)),
                listOf(CellSpec.Text("Total Liabilities"), CellSpec.Text("Payables & Debt"), CellSpec.Number(report.totalLiabilities)),
                listOf(CellSpec.Text("Net Worth"), CellSpec.Text("Assets - Liabilities"), CellSpec.Number(report.netWorth))
            )
        )

        // 2. Overall Financial Summary
        addSheet(
            "Overall Financial Summary",
            listOf(28, 14, 20),
            listOf("Transaction Type / Metric", "Count", "Total Amount (BDT)"),
            report.typeSummaries.map {
                listOf(CellSpec.Text(it.type), CellSpec.Number(it.count.toDouble()), CellSpec.Number(it.totalAmount))
            } + listOf(
                listOf(CellSpec.Text("NET CASH FLOW (Income - Net Expense)"), CellSpec.Number(report.transactionCount.toDouble()), CellSpec.Number(report.periodNetCashFlow)),
                listOf(CellSpec.Text("INTERNAL TRANSFERS (Expense Neutral)"), CellSpec.Number(report.transferCount.toDouble()), CellSpec.Number(report.transferVolume))
            )
        )

        // 3. Account Summary
        addSheet(
            "Account Summary",
            listOf(22, 16, 18, 18, 16, 16, 16, 18, 12, 12),
            listOf("Account Name", "Type", "Provider", "Opening Balance", "Money In", "Money Out", "Net Change", "Closing Balance", "Period Tx", "Status"),
            report.accountSummaries.map {
                listOf(
                    CellSpec.Text(it.accountName),
                    CellSpec.Text(it.accountType),
                    CellSpec.Text(it.provider),
                    CellSpec.Number(it.periodOpeningBalance),
                    CellSpec.Number(it.moneyIn),
                    CellSpec.Number(it.moneyOut),
                    CellSpec.Number(it.netChange),
                    CellSpec.Number(it.periodClosingBalance),
                    CellSpec.Number(it.txCount.toDouble()),
                    CellSpec.Text(it.status)
                )
            }
        )

        // 4. All Transactions
        addSheet(
            "All Transactions",
            listOf(14, 12, 20, 15, 20, 20, 20, 20, 30),
            listOf("Date", "Time", "Type", "Amount (BDT)", "Account", "To Account", "Category", "Person", "Description"),
            report.transactions.map {
                listOf(
                    CellSpec.Text(it.date),
                    CellSpec.Text(it.time),
                    CellSpec.Text(it.type),
                    CellSpec.Number(it.amount),
                    CellSpec.Text(it.accountName),
                    CellSpec.Text(it.toAccountName),
                    CellSpec.Text(it.categoryName),
                    CellSpec.Text(it.personName),
                    CellSpec.Text(it.description)
                )
            }
        )

        // 5. Monthly / Yearly Summary
        val trendSheetName = if (report.periodType == ReportPeriodType.MONTHLY) "Monthly Summary" else "Yearly Summary"
        addSheet(
            trendSheetName,
            listOf(20, 12, 16, 16, 14, 16, 16),
            listOf("Period Bucket", "Tx Count", "Income", "Gross Expense", "Refunds", "Net Expense", "Net Cash Flow"),
            report.trendBuckets.map {
                listOf(
                    CellSpec.Text(it.label),
                    CellSpec.Number(it.txCount.toDouble()),
                    CellSpec.Number(it.income),
                    CellSpec.Number(it.grossExpense),
                    CellSpec.Number(it.refunds),
                    CellSpec.Number(it.netExpense),
                    CellSpec.Number(it.netCashFlow)
                )
            }
        )

        // 6. Person-wise Summary
        addSheet(
            "Person-wise Summary",
            listOf(24, 18, 12, 16, 16, 16, 14),
            listOf("Person Name", "Relationship", "Tx Count", "Total Expense", "Total Income", "Loan / Other Flow", "Expense Share (%)"),
            report.personSummaries.map {
                listOf(
                    CellSpec.Text(it.personName),
                    CellSpec.Text(it.relationship),
                    CellSpec.Number(it.txCount.toDouble()),
                    CellSpec.Number(it.totalExpense),
                    CellSpec.Number(it.totalIncome),
                    CellSpec.Number(it.totalOtherFlow),
                    CellSpec.Number(it.expenseSharePercent)
                )
            }
        )

        // 7. Person-wise Breakdown
        addSheet(
            "Person-wise Breakdown",
            listOf(22, 16, 14, 18, 18, 20, 15, 28),
            listOf("Person", "Relationship", "Date", "Type", "Account", "Category", "Amount (BDT)", "Description"),
            report.personDetails.map {
                listOf(
                    CellSpec.Text(it.personName),
                    CellSpec.Text(it.relationship),
                    CellSpec.Text(it.date),
                    CellSpec.Text(it.type),
                    CellSpec.Text(it.accountName),
                    CellSpec.Text(it.categoryName),
                    CellSpec.Number(it.amount),
                    CellSpec.Text(it.description)
                )
            }
        )

        // 8. Category-wise Summary
        addSheet(
            "Category-wise Summary",
            listOf(14, 24, 12, 18, 14),
            listOf("Type", "Category", "Tx Count", "Total Amount (BDT)", "Share (%)"),
            report.categorySummaries.map {
                listOf(
                    CellSpec.Text(it.type),
                    CellSpec.Text(it.categoryName),
                    CellSpec.Number(it.txCount.toDouble()),
                    CellSpec.Number(it.amount),
                    CellSpec.Number(it.sharePercent)
                )
            }
        )

        // 9. Category-wise Breakdown
        addSheet(
            "Category-wise Breakdown",
            listOf(22, 14, 14, 18, 18, 15, 28),
            listOf("Category", "Type", "Date", "Account", "Person", "Amount (BDT)", "Description"),
            report.categoryDetails.map {
                listOf(
                    CellSpec.Text(it.categoryName),
                    CellSpec.Text(it.categoryType),
                    CellSpec.Text(it.date),
                    CellSpec.Text(it.accountName),
                    CellSpec.Text(it.personName),
                    CellSpec.Number(it.amount),
                    CellSpec.Text(it.description)
                )
            }
        )

        // 10. Loan Summary
        addSheet(
            "Loan Summary",
            listOf(12, 24, 16, 16, 16, 14, 14, 14, 26),
            listOf("Type", "Person / Party", "Principal", "Paid Amount", "Remaining", "Start Date", "Due Date", "Status", "Notes"),
            report.loanRows.map {
                listOf(
                    CellSpec.Text(it.type),
                    CellSpec.Text(it.partyName),
                    CellSpec.Number(it.principal),
                    CellSpec.Number(it.paidAmount),
                    CellSpec.Number(it.remainingAmount),
                    CellSpec.Text(it.date),
                    CellSpec.Text(it.dueDate),
                    CellSpec.Text(it.status),
                    CellSpec.Text(it.notes)
                )
            }
        )

        // 11. Savings & Assets
        addSheet(
            "Savings & Assets",
            listOf(16, 26, 18, 18, 18, 14),
            listOf("Class", "Item Name", "Category", "Original / Target", "Current Value", "Status"),
            report.assetItems.map {
                listOf(
                    CellSpec.Text(it.classification),
                    CellSpec.Text(it.name),
                    CellSpec.Text(it.category),
                    CellSpec.Number(it.originalOrTarget),
                    CellSpec.Number(it.currentOrRemaining),
                    CellSpec.Text(it.status)
                )
            }
        )

        // 12. Liabilities
        addSheet(
            "Liabilities",
            listOf(16, 26, 18, 18, 18, 14),
            listOf("Class", "Liability Name", "Category", "Original Amount", "Remaining Payable", "Status"),
            report.liabilityItems.map {
                listOf(
                    CellSpec.Text(it.classification),
                    CellSpec.Text(it.name),
                    CellSpec.Text(it.category),
                    CellSpec.Number(it.originalOrTarget),
                    CellSpec.Number(it.currentOrRemaining),
                    CellSpec.Text(it.status)
                )
            }
        )

        // 13+. Detailed Statement Sheet for EVERY Account
        for (stmt in report.accountStatements) {
            val rows = buildList {
                add(
                    listOf(
                        CellSpec.Text(report.startDate),
                        CellSpec.Text("OPENING BALANCE"),
                        CellSpec.Text("Carried forward before ${report.startDate}"),
                        CellSpec.Text("-"),
                        CellSpec.Number(0.0),
                        CellSpec.Number(0.0),
                        CellSpec.Number(stmt.openingBalance)
                    )
                )
                for (e in stmt.entries) {
                    add(
                        listOf(
                            CellSpec.Text(e.date),
                            CellSpec.Text(e.type),
                            CellSpec.Text(e.counterpartyOrDetail),
                            CellSpec.Text(e.description),
                            CellSpec.Number(e.moneyIn),
                            CellSpec.Number(e.moneyOut),
                            CellSpec.Number(e.runningBalance)
                        )
                    )
                }
                add(
                    listOf(
                        CellSpec.Text(report.endDate),
                        CellSpec.Text("CLOSING BALANCE"),
                        CellSpec.Text("${stmt.entries.size} entries in period"),
                        CellSpec.Text("Period closing balance"),
                        CellSpec.Number(stmt.totalMoneyIn),
                        CellSpec.Number(stmt.totalMoneyOut),
                        CellSpec.Number(stmt.closingBalance)
                    )
                )
            }
            addSheet(
                "Stmt - ${stmt.accountName}",
                listOf(14, 18, 24, 28, 15, 15, 16),
                listOf("Date", "Type", "Counterparty / Detail", "Description", "Money In", "Money Out", "Running Balance"),
                rows
            )
        }

        return sheets
    }

    private fun saveBytesToMediaStoreDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching false
            resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: return@runCatching false
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        }.getOrDefault(false)
    }

    private fun buildTransactionsSheetXml(transactions: List<Transaction>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<cols><col min="1" max="1" width="14" customWidth="1"/><col min="2" max="2" width="12" customWidth="1"/><col min="3" max="3" width="18" customWidth="1"/><col min="4" max="4" width="14" customWidth="1"/><col min="5" max="6" width="20" customWidth="1"/><col min="7" max="8" width="22" customWidth="1"/><col min="9" max="9" width="32" customWidth="1"/></cols>""")
        sb.append("<sheetData>")

        val headers = listOf(
            "Date",
            "Time",
            "Type",
            "Amount (BDT)",
            "Account",
            "To Account",
            "Category",
            "Person",
            "Description"
        )
        sb.append(buildTextRow(rowIndex = 1, values = headers, styleIndex = 1))

        val sorted = transactions.sortedByDescending { it.date + (it.time ?: "") }
        if (sorted.isEmpty()) {
            sb.append(
                buildMixedRow(
                    rowIndex = 2,
                    cells = listOf(
                        CellSpec.Text("No transactions recorded")
                    )
                )
            )
        } else {
            sorted.forEachIndexed { index, tx ->
                val rowIdx = index + 2
                sb.append(
                    buildMixedRow(
                        rowIndex = rowIdx,
                        cells = listOf(
                            CellSpec.Text(tx.date),
                            CellSpec.Text(tx.time ?: ""),
                            CellSpec.Text(tx.type),
                            CellSpec.Number(tx.amount),
                            CellSpec.Text(tx.accountName ?: tx.accountId),
                            CellSpec.Text(tx.toAccountName ?: tx.toAccountId ?: ""),
                            CellSpec.Text(tx.categoryName ?: ""),
                            CellSpec.Text(tx.personName ?: ""),
                            CellSpec.Text(tx.description ?: "")
                        )
                    )
                )
            }
        }

        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun buildSummarySheetXml(
        accounts: List<Account>,
        summary: FinancialSummary,
        txCount: Int
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<cols><col min="1" max="1" width="30" customWidth="1"/><col min="2" max="2" width="18" customWidth="1"/></cols>""")
        sb.append("<sheetData>")

        sb.append(buildTextRow(1, listOf("Metric", "Value (BDT)"), styleIndex = 1))
        val metrics = listOf(
            "Total Transactions" to txCount.toDouble(),
            "Total Income" to summary.totalIncome,
            "Total Expense" to summary.totalExpense,
            "Available Balance" to summary.availableBalance,
            "Cash Balance" to summary.cashBalance,
            "Bank Balance" to summary.bankBalance,
            "Mobile Wallet Balance" to summary.mobileWalletBalance,
            "Total Savings" to summary.totalSavings,
            "Loan Receivable (Given)" to summary.loanGivenReceivable,
            "Loan Payable (Taken)" to summary.loanTakenPayable,
            "Net Worth" to summary.netWorth
        )
        var rowIdx = 2
        for ((label, value) in metrics) {
            sb.append(buildMixedRow(rowIdx++, listOf(CellSpec.Text(label), CellSpec.Number(value))))
        }

        rowIdx++
        sb.append(buildTextRow(rowIdx++, listOf("Account Name", "Current Balance (BDT)"), styleIndex = 1))
        for (acc in accounts) {
            sb.append(buildMixedRow(rowIdx++, listOf(CellSpec.Text(acc.name), CellSpec.Number(acc.currentBalance))))
        }

        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private sealed interface CellSpec {
        data class Text(val value: String) : CellSpec
        data class Number(val value: Double) : CellSpec
    }

    private fun buildTextRow(rowIndex: Int, values: List<String>, styleIndex: Int = 0): String {
        val sb = StringBuilder()
        sb.append("""<row r="$rowIndex">""")
        values.forEachIndexed { colIndex, text ->
            val ref = "${columnLetter(colIndex)}$rowIndex"
            val styleAttr = if (styleIndex > 0) """ s="$styleIndex"""" else ""
            sb.append("""<c r="$ref"$styleAttr t="inlineStr"><is><t>${escapeXml(text)}</t></is></c>""")
        }
        sb.append("</row>")
        return sb.toString()
    }

    private fun buildMixedRow(rowIndex: Int, cells: List<CellSpec>): String {
        val sb = StringBuilder()
        sb.append("""<row r="$rowIndex">""")
        cells.forEachIndexed { colIndex, cell ->
            val ref = "${columnLetter(colIndex)}$rowIndex"
            when (cell) {
                is CellSpec.Text -> {
                    sb.append("""<c r="$ref" t="inlineStr"><is><t>${escapeXml(cell.value)}</t></is></c>""")
                }
                is CellSpec.Number -> {
                    val safeNum = if (cell.value.isFinite()) formatAmount(cell.value) else "0"
                    sb.append("""<c r="$ref"><v>$safeNum</v></c>""")
                }
            }
        }
        sb.append("</row>")
        return sb.toString()
    }

    private fun columnLetter(zeroBasedIndex: Int): String {
        var idx = zeroBasedIndex
        val sb = StringBuilder()
        while (idx >= 0) {
            sb.append(('A'.code + (idx % 26)).toChar())
            idx = (idx / 26) - 1
        }
        return sb.reverse().toString()
    }

    private fun writeZipEntry(zos: ZipOutputStream, path: String, content: String) {
        zos.putNextEntry(ZipEntry(path))
        zos.write(content.toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()
    }

    private fun escapeCsvField(raw: String): String {
        val clean = raw.replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
        return if (clean.contains(',') || clean.contains('"')) {
            "\"${clean.replace("\"", "\"\"")}\""
        } else {
            clean
        }
    }

    private fun escapeXml(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> {
                    val code = ch.code
                    val isValidXmlChar = code == 0x9 || code == 0xA || code == 0xD ||
                        (code in 0x20..0xD7FF) || (code in 0xE000..0xFFFD)
                    if (isValidXmlChar) sb.append(ch)
                }
            }
        }
        return sb.toString()
    }

    private fun formatAmount(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }
}
