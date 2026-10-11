package com.example.pdfmaker
enum class PdfType(val label: String) {
    TEXT("Text Document"), INVOICE("Invoice / Receipt"), RESUME("Resume / CV"),
    CERTIFICATE("Certificate"), PACKING("Packing List"), MINUTES("Meeting Minutes"), FLYER("Event Flyer")
}
