package app.yomishiori.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlProgressPolicyTest {
    private val original = "https://example.com/shuukura/ch1/page1"

    private fun reason(url: String, rule: String? = null, enabled: Boolean = true) =
        UrlProgressPolicy.rejectReason(url, original, rule, enabled)

    @Test fun `same site pages are tracked`() {
        assertNull(reason("https://example.com/shuukura/ch1/page2"))
        assertNull(reason("https://example.com/shuukura/ch2/page1?x=1#p3"))
    }

    @Test fun `subdomains of the same site are tracked`() {
        assertNull(reason("https://www.example.com/shuukura/ch3"))
        assertNull(reason("https://m.example.com/shuukura/ch3"))
    }

    @Test fun `http on the same site is still the same site`() {
        assertNull(reason("http://example.com/shuukura/ch3"))
    }

    @Test fun `other sites and ad hosts are rejected`() {
        assertEquals(RejectReason.OTHER_SITE, reason("https://ads.doubleclick.net/click?x"))
        assertEquals(RejectReason.OTHER_SITE, reason("https://example.org/shuukura/ch1"))
        assertEquals(RejectReason.OTHER_SITE, reason("https://notexample.com/ch1"))
        assertEquals(RejectReason.OTHER_SITE, reason("https://example.com.evil.io/ch1"))
    }

    @Test fun `login and account pages are rejected`() {
        assertEquals(RejectReason.EXCLUDED_PAGE, reason("https://example.com/login?next=/ch2"))
        assertEquals(RejectReason.EXCLUDED_PAGE, reason("https://example.com/user/Account/settings"))
        assertEquals(RejectReason.EXCLUDED_PAGE, reason("https://example.com/cdn-cgi/challenge-platform/x"))
        assertEquals(RejectReason.EXCLUDED_PAGE, reason("https://example.com/shuukura/ch2/?__cf_chl_tk=abc"))
        assertNull(reason("https://example.com/shuukura/the-login-arc"))
    }

    @Test fun `non web schemes are rejected`() {
        listOf("about:blank", "data:text/html,hi", "javascript:alert(1)", "file:///sdcard/x.html", "content://x/y", "intent://x#Intent;end", "")
            .forEach { assertEquals(it, RejectReason.NOT_WEB, reason(it)) }
    }

    @Test fun `paused tracking rejects everything`() {
        assertEquals(RejectReason.PAUSED, reason("https://example.com/shuukura/ch1/page2", enabled = false))
    }

    @Test fun `explicit host rule replaces same-site matching`() {
        val rule = "reader.example.net, cdn-mirror.org"
        assertNull(reason("https://reader.example.net/ch1", rule))
        assertNull(reason("https://a.cdn-mirror.org/ch1", rule))
        assertEquals(RejectReason.OTHER_SITE, reason("https://example.com/shuukura/ch2", rule))
    }

    @Test fun `registrable domain handles country second-level domains and IPs`() {
        assertEquals("syosetu.com", UrlProgressPolicy.registrableDomain("ncode.syosetu.com"))
        assertEquals("example.co.jp", UrlProgressPolicy.registrableDomain("www.example.co.jp"))
        assertEquals("kakuyomu.jp", UrlProgressPolicy.registrableDomain("kakuyomu.jp"))
        assertEquals("127.0.0.1", UrlProgressPolicy.registrableDomain("127.0.0.1"))
        assertEquals(RejectReason.OTHER_SITE, UrlProgressPolicy.rejectReason("https://other.co.jp/x", "https://www.example.co.jp/a", null, true))
    }

    @Test fun `missing original url rejects unless a rule is set`() {
        assertEquals(RejectReason.OTHER_SITE, UrlProgressPolicy.rejectReason("https://example.com/a", null, null, true))
        assertNull(UrlProgressPolicy.rejectReason("https://example.com/a", null, "example.com", true))
    }
}
