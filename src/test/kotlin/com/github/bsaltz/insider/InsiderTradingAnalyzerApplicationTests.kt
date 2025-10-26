package com.github.bsaltz.insider

import com.google.cloud.storage.Storage
import com.google.cloud.vision.v1.ImageAnnotatorClient
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase
class InsiderTradingAnalyzerApplicationTests {
    @MockitoBean
    private lateinit var storage: Storage

    @MockitoBean
    private lateinit var imageAnnotatorClient: ImageAnnotatorClient

    @Test
    fun contextLoads() {
    }
}
