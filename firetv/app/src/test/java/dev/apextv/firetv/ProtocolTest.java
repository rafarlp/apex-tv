package dev.apextv.firetv;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProtocolTest {
    @Test public void acceptsCurrentOfficialPairingLinkAndRejectsLookalikes() {
        assertTrue(Protocol.verificationUrl("https://f1.com/pair"));
        assertTrue(Protocol.verificationUrl("https://f1.com/pair?user_code=SYNTHETIC"));
        assertTrue(Protocol.verificationUrl("https://account.formula1.com/#/en/tv-account-sync"));
        assertFalse(Protocol.verificationUrl("https://f1.com.attacker.example/pair"));
        assertFalse(Protocol.verificationUrl("https://f1.com/elsewhere"));
        assertFalse(Protocol.verificationUrl("http://f1.com/pair"));
    }
    @Test public void rejectsLookalikeAndCredentialBearingOrigins() {
        for (String bad : new String[]{"http://f1tv.formula1.com/x", "https://f1tv.formula1.com.attacker.example/x", "https://user@f1tv.formula1.com/x", "https://f1tv.formula1.com:444/x", "//attacker.example/x"}) {
            assertFalse(Protocol.firstParty(bad, "f1tv.formula1.com"));
            assertThrows(IllegalArgumentException.class, () -> Protocol.catalogUrl(bad));
        }
        assertEquals(Protocol.BASE + "/2.0/PAGE/1", Protocol.catalogUrl("/2.0/PAGE/1"));
    }
    @Test public void extractsOnlyValidF1SessionIds() {
        assertEquals("1000009181", Protocol.contentId("1000009181"));
        assertEquals("1000009181", Protocol.contentId("https://f1tv.formula1.com/detail/1000009181/race?action=play"));
        assertThrows(IllegalArgumentException.class, () -> Protocol.contentId("https://attacker.example/detail/1000009181"));
        assertThrows(IllegalArgumentException.class, () -> Protocol.contentId("1000009181&channelId=1"));
    }
    @Test public void catalogTraversesNestedRowsAndDeduplicatesVideos() throws Exception {
        JSONObject response = new JSONObject("""
          {"resultCode":"OK","resultObj":{"containers":[
            {"metadata":{"label":"Season"},"actions":[{"uri":"/2.0/PAGE/123"}],
             "retrieveItems":{"resultObj":{"containers":[
                {"metadata":{"title":"Race","contentType":"VIDEO","contentId":1000009181},"actions":[{"uri":"/2.0/CONTENT/VIDEO/1000009181"}]},
                {"metadata":{"title":"Race duplicate","contentType":"VIDEO","contentId":1000009181},"actions":[{"uri":"/2.0/CONTENT/VIDEO/1000009181"}]},
                {"metadata":{"title":"External"},"actions":[{"uri":"https://attacker.example/PAGE/1"}]}
             ]}}}
          ]}}
          """);
        var entries = Protocol.catalog(response);
        assertEquals(2, entries.size()); assertFalse(entries.get(0).video());
        assertEquals("1000009181", entries.get(1).contentId); assertTrue(entries.get(1).video());
    }
    @Test public void rejectsBackendFailureAndQueryInjection() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> Protocol.catalog(new JSONObject("{\"resultCode\":\"ERROR\"}")));
        assertThrows(IllegalArgumentException.class, () -> Protocol.playUrl("1000009181", "1&other=1", Protocol.Route.UHD_DASH));
    }
    @Test public void renderingVerificationNeedsDimensionsAndFrames() {
        assertFalse(Protocol.renderedUhd(3840, 2160, 0));
        assertFalse(Protocol.renderedUhd(1920, 1080, 500));
        assertFalse(Protocol.renderedUhd(2880, 1620, 500));
        assertTrue(Protocol.renderedUhd(3840, 2160, 1));
    }
}
