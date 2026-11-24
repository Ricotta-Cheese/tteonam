package com.example.tteonam;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.firebase.firestore.GeoPoint;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// FirebaseControl/firestore.rules의 validPhoto 계약과 맞는지 확인한다.
public class PhotoDocumentTest {
    private static final Set<String> RULE_KEYS = new HashSet<>(Arrays.asList(
            "authorId", "visibility", "status", "capturedAt", "createdAt", "updatedAt",
            "imagePath", "thumbnailPath", "comment", "shareLocation", "location",
            "placeName", "scene"));

    private PhotoDocument.Builder base() {
        return PhotoDocument.builder("trip1", "user1", "photo1").capturedAt(1_000L);
    }

    @Test
    public void createMapHasExactlyTheFieldsTheRulesRequire() {
        Map<String, Object> map = base().build().toCreateMap();
        assertEquals(RULE_KEYS, map.keySet());
        assertEquals("uploading", map.get("status"));
        assertEquals("unknown", map.get("scene"));
        assertEquals("private", map.get("visibility"));
    }

    @Test
    public void pathsUseJpgExtensionExpectedByStorageRules() {
        PhotoDocument doc = base().build();
        assertEquals("trips/trip1/photos/user1/photo1/image.jpg", doc.getImagePath());
        assertEquals("trips/trip1/photos/user1/photo1/thumb.jpg", doc.getThumbnailPath());
    }

    @Test
    public void locationIsDroppedWhenSharingIsOff() {
        Map<String, Object> map = base()
                .location(37.5, 127.0, false)
                .placeName("서울")
                .build().toCreateMap();
        assertFalse((Boolean) map.get("shareLocation"));
        assertNull(map.get("location"));
        assertEquals("", map.get("placeName"));
    }

    @Test
    public void locationIsKeptWhenSharingIsOn() {
        Map<String, Object> map = base()
                .location(37.5, 127.0, true)
                .placeName("서울")
                .build().toCreateMap();
        assertTrue((Boolean) map.get("shareLocation"));
        assertEquals(new GeoPoint(37.5, 127.0), map.get("location"));
        assertEquals("서울", map.get("placeName"));
    }

    @Test
    public void commentIsTrimmedAndLimitedTo200Chars() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 250; i++) longText.append('가');
        assertEquals(200, base().comment(longText.toString()).build().getComment().length());
        assertEquals("", base().comment(null).build().getComment());
        assertEquals("hi", base().comment("  hi  ").build().getComment());
    }

    @Test
    public void capturedAtNeverLiesInTheFuture() {
        long future = System.currentTimeMillis() + 60_000L;
        PhotoDocument doc = base().capturedAt(future).build();
        assertTrue(doc.getCapturedAt().toDate().getTime() <= System.currentTimeMillis());
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsUnknownVisibility() {
        base().visibility("public").build();
    }
}
