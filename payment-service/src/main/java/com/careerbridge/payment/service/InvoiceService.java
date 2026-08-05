package com.careerbridge.payment.service;

import com.careerbridge.payment.dto.InvoiceDownload;

public interface InvoiceService {

    InvoiceDownload getInvoice(String callerRole, Long callerId, Long paymentId);
}
