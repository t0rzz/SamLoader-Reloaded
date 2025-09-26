package app.samloader.common.version

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Test
import kotlin.test.assertEquals

class VersionFetchTest {
    @Test
    fun buildRequest_matches_python_shape_for_INS_SM_S938B() {
        val res = this::class.java.getResourceAsStream("/expected_request_ins_sm-s938b.json")
            ?: error("expected_request_ins_sm-s938b.json not found on classpath")
        val text = res.reader().readText()
        val root = Json.parseToJsonElement(text).jsonObject
        val expectedUrl = root["url"]?.jsonPrimitive?.content
            ?: error("expected url in JSON")
        val headersObj = root["headers"]?.jsonObject ?: error("expected headers in JSON")
        val expectedUA = headersObj["User-Agent"]?.jsonPrimitive?.contentOrNull ?: ""

        val actual = VersionFetch.buildRequest(model = "SM-S938B", region = "INS")

        assertEquals(expectedUrl, actual.url, "URL must match Python request")
        assertEquals(expectedUA, actual.headers["User-Agent"], "User-Agent must match Python request")
        assertEquals(1, actual.headers.size, "Only User-Agent header should be present to match Python request")
    }
}
