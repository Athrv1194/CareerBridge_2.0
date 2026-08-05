package com.careerbridge.payment.pdf;

import com.careerbridge.payment.dto.InvoiceData;
import com.careerbridge.payment.util.Gst;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Renders a one-page A4 GST invoice for a successful subscription payment.
 *
 * OpenPDF (com.lowagie.text), NOT iText 7, matching resume-service's ResumePdfBuilder: iText
 * Community is AGPL v3, and serving a generated PDF over HTTP would oblige publishing this
 * repository's source. Only the built-in Helvetica family is used -- never load a font from the
 * filesystem, since the path that exists on a developer machine does not exist in the container.
 *
 * This is the first table-based document in this project's PDF code: resume-service's builder is
 * pure flowing Paragraphs with no PdfPTable usage. Tables here use com.lowagie.text.pdf.PdfPTable /
 * PdfPCell / com.lowagie.text.Phrase -- the iText-4 lineage API -- never iText 7's Table/Cell.
 */
@Component
public class InvoicePdfBuilder {

    private static final Color PRIMARY = new Color(46, 116, 181);
    private static final Color TEXT_DARK = new Color(33, 33, 33);
    private static final Color TEXT_GRAY = new Color(100, 100, 100);
    private static final Color RULE = new Color(200, 200, 200);
    private static final Color HEADER_BG = new Color(240, 244, 249);

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 20, Font.BOLD, PRIMARY);
    private static final Font LABEL_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, TEXT_GRAY);
    private static final Font VALUE_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, TEXT_DARK);
    private static final Font TABLE_HEADER_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, TEXT_DARK);
    private static final Font TABLE_BODY_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, TEXT_DARK);
    private static final Font TOTAL_LABEL_FONT = new Font(Font.HELVETICA, 11, Font.BOLD, TEXT_DARK);
    private static final Font FOOTER_FONT = new Font(Font.HELVETICA, 8, Font.ITALIC, TEXT_GRAY);

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    /**
     * Placeholder only: this platform has no real GSTIN. Swapping in a real one is a one-constant
     * change here -- do not fabricate a plausible-looking number, since that would misrepresent a
     * real tax registration.
     */
    private static final String GSTIN_PLACEHOLDER = "GSTIN NOT YET REGISTERED";

    /**
     * @throws IOException if the PDF cannot be written; the service maps this to a 500.
     */
    public byte[] build(InvoiceData invoice) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Rectangle, then left, right, top, bottom -- OpenPDF's argument order, not the CSS one.
        Document document = new Document(PageSize.A4, 50, 50, 40, 40);

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            addHeader(document, invoice);
            addBillingDetails(document, invoice);
            addLineItemTable(document, invoice);
            addTotals(document, invoice.getGstSplit());
            addFooter(document);
        } catch (DocumentException ex) {
            throw new IOException("Failed to render invoice PDF", ex);
        } finally {
            // close() flushes the trailer; without it the byte array is an unreadable partial file.
            if (document.isOpen()) {
                document.close();
            }
        }

        return out.toByteArray();
    }

    private void addHeader(Document document, InvoiceData invoice) throws DocumentException {
        Paragraph title = new Paragraph("TAX INVOICE", TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        Paragraph brand = new Paragraph("CareerBridge", VALUE_FONT);
        brand.setAlignment(Element.ALIGN_CENTER);
        brand.setSpacingBefore(2f);
        document.add(brand);

        Paragraph gstin = new Paragraph(GSTIN_PLACEHOLDER, LABEL_FONT);
        gstin.setAlignment(Element.ALIGN_CENTER);
        document.add(gstin);

        addSeparator(document, 12f);
    }

    private void addBillingDetails(Document document, InvoiceData invoice) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100f);
        table.setSpacingAfter(12f);

        addDetailCell(table, "Invoice Number", invoice.getInvoiceNumber());
        addDetailCell(table, "Issue Date", format(invoice.getIssueDate()));
        addDetailCell(table, "Billed To", "User ID " + invoice.getUserId());
        addDetailCell(table, "Billing Period",
                format(invoice.getPeriodStart()) + " to " + format(invoice.getPeriodEnd()));
        addDetailCell(table, "Razorpay Order ID", nullToDash(invoice.getRazorpayOrderId()));
        addDetailCell(table, "Razorpay Payment ID", nullToDash(invoice.getRazorpayPaymentId()));

        document.add(table);
    }

    private void addDetailCell(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, LABEL_FONT));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPaddingBottom(2f);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, VALUE_FONT));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPaddingBottom(6f);
        table.addCell(valueCell);
    }

    private void addLineItemTable(Document document, InvoiceData invoice) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100f);
        table.setWidths(new float[]{3f, 1f});
        table.setSpacingBefore(4f);
        table.setSpacingAfter(8f);

        addTableHeaderCell(table, "Description");
        addTableHeaderCell(table, "Amount");

        String description = invoice.getPlanName() + " Subscription (" + invoice.getBillingCycle() + ")";
        addBodyCell(table, description, Element.ALIGN_LEFT);
        addBodyCell(table, formatRupees(invoice.getGstSplit().taxablePaise()), Element.ALIGN_RIGHT);

        document.add(table);
    }

    private void addTableHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, TABLE_HEADER_FONT));
        cell.setBackgroundColor(HEADER_BG);
        cell.setPadding(6f);
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, TABLE_BODY_FONT));
        cell.setPadding(6f);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    private void addTotals(Document document, Gst.GstSplit split) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(50f);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setWidths(new float[]{2f, 1f});
        table.setSpacingAfter(10f);

        addTotalRow(table, "Taxable Value", formatRupees(split.taxablePaise()), TABLE_BODY_FONT);
        addTotalRow(table, "CGST (9%)", formatRupees(split.cgstPaise()), TABLE_BODY_FONT);
        addTotalRow(table, "SGST (9%)", formatRupees(split.sgstPaise()), TABLE_BODY_FONT);
        addTotalRow(table, "Total Paid", formatRupees(split.totalPaise()), TOTAL_LABEL_FONT);

        document.add(table);
    }

    private void addTotalRow(PdfPTable table, String label, String value, Font font) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        labelCell.setPadding(4f);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, font));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setPadding(4f);
        table.addCell(valueCell);
    }

    private void addFooter(Document document) throws DocumentException {
        addSeparator(document, 6f);
        Paragraph footer = new Paragraph(
                "This is a system-generated invoice and does not require a signature.", FOOTER_FONT);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(6f);
        document.add(footer);
    }

    private void addSeparator(Document document, float spacingBefore) throws DocumentException {
        Chunk rule = new Chunk(new LineSeparator(0.5f, 100f, RULE, Element.ALIGN_CENTER, -2f));
        Paragraph wrapper = new Paragraph(rule);
        wrapper.setSpacingBefore(spacingBefore);
        document.add(wrapper);
    }

    private String format(java.time.LocalDate date) {
        return date == null ? "-" : date.format(DATE_FORMAT);
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    /** Paise -> a rupee string with two decimal places, e.g. 19900L -> "Rs. 199.00". */
    private String formatRupees(long paise) {
        BigDecimal rupees = BigDecimal.valueOf(paise, 2).setScale(2, RoundingMode.UNNECESSARY);
        return "Rs. " + rupees.toPlainString();
    }
}
