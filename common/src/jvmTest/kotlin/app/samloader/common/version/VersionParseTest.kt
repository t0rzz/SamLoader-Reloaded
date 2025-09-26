package app.samloader.common.version

import org.junit.Test
import kotlin.test.assertEquals

class VersionParseTest {
    @Test
    fun parse_latest_with_attributes_and_normalize() {
        val xml = """<?xml version="1.0" encoding="UTF-8" ?>
            <versioninfo>
            <url>https://fota-cloud-dn.ospserver.net/firmware/</url>
            <firmware>
              <model>SM-S938B</model>
              <cc>INS</cc>
              <version>
               <latest o="15">S938BXXS5AYG4/S938BOXM5AYG4/S938BXXS5AYG4</latest>
               <upgrade>
                <value rcount='1' fwsize='909153809'>S938BXXS1AYC2/S938BOXM1AYC2/S938BXXS1AYC2</value>
               </upgrade>
              </version>
             </firmware>
             <polling>
              <period>7</period>
              <time>08</time>
              <range>23</range>
             </polling>
            </versioninfo>
        """.trimIndent()

        val parsed = VersionFetch.extractLatest(xml)
        assertEquals(
            "S938BXXS5AYG4/S938BOXM5AYG4/S938BXXS5AYG4/S938BXXS5AYG4",
            parsed,
            "Latest version should be parsed and normalized to four parts"
        )
    }
}
