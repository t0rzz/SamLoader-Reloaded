package app.samloader.common.version

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals

@Serializable
private data class ExpectedRequest(val url: String, val headers: Map<String, String>)

class VersionFetchTest {
    @Test
    fun buildRequest_matches_python_shape_for_INS_SM_S938B() {
        val res = this::class.java.getResourceAsStream("/expected_request_ins_sm-s938b.json")
            ?: error("expected_request_ins_sm-s938b.json not found on classpath")
        val expected = Json.decodeFromString<ExpectedRequest>(res.reader().readText())

        val actual = VersionFetch.buildRequest(model = "SM-S938B", region = "INS")

        assertEquals(expected.url, actual.url, "URL must match Python request")
        assertEquals(expected.headers, actual.headers, "Headers must match Python request exactly")
    }
}
