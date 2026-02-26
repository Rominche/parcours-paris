package com.parcoursparis.map.geocoding

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for NominatimGeocodingService: valid response returns list,
 * network failure throws GeocodingNetworkException (offline message).
 */
class NominatimGeocodingServiceTest {

    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun search_returnsResultsWhenServerReturnsValidJson() = runTest {
        server.start()
        val baseUrl = server.url("/").toString().trimEnd('/')
        val json = """[
            {"lat":"48.8566","lon":"2.3522","display_name":"Paris, France"},
            {"lat":"48.8606","lon":"2.3376","display_name":"Louvre, Paris"}
        ]"""
        server.enqueue(MockResponse().setBody(json).setResponseCode(200))

        val service = NominatimGeocodingService(baseUrl = baseUrl)
        val results = service.search("Paris", null)

        assertEquals(2, results.size)
        assertEquals("Paris, France", results[0].label)
        assertEquals(48.8566, results[0].latitude, 1e-6)
        assertEquals(2.3522, results[0].longitude, 1e-6)
        assertEquals("Louvre, Paris", results[1].label)
    }

    @Test(expected = GeocodingNetworkException::class)
    fun search_throwsGeocodingNetworkExceptionWhenConnectionFails() = runTest {
        server.start()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val baseUrl = server.url("/").toString().trimEnd('/')
        val service = NominatimGeocodingService(baseUrl = baseUrl)
        service.search("Paris", null)
    }
}
