package com.example.pdfmaker
import androidx.test.espresso.idling.CountingIdlingResource
object TestIdling { val create = CountingIdlingResource("pdf_generation") }
