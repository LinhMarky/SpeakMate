package com.example.speakmate;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class GeneratedDeckStoreTest {

    private Context context;
    private LocalDeckStore store;

    @Before
    public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        store = new LocalDeckStore(context);
    }

    @Test
    public void localDeckStore_persistsAndRestoresSavedWordIdAndStatus() {
        List<Flashcard> cards = new ArrayList<>();
        cards.add(new Flashcard(101L, "Destination", "Điểm đến", "Our destination is near.", "NEW"));
        cards.add(new Flashcard(102L, "Journey", "Chuyến đi", "Safe journey!", "LEARNING"));

        String deckId = store.createDeck("Test Deck AI", "B1", cards);
        assertNotNull(deckId);

        List<Flashcard> restored = store.getCards(deckId);
        assertEquals(2, restored.size());

        assertEquals(101L, restored.get(0).getSavedWordId());
        assertEquals("Destination", restored.get(0).getWord());
        assertEquals("Điểm đến", restored.get(0).getMeaning());
        assertEquals("NEW", restored.get(0).getStatus());

        assertEquals(102L, restored.get(1).getSavedWordId());
        assertEquals("Journey", restored.get(1).getWord());
        assertEquals("LEARNING", restored.get(1).getStatus());

        // Dọn dẹp
        store.deleteDeck(deckId);
    }

    @Test
    public void localDeckStore_legacyCardsWithoutServerId_defaultToZeroAndLocal() {
        List<Flashcard> legacyCards = new ArrayList<>();
        legacyCards.add(new Flashcard("OldWord", "Từ cũ", "An old example"));

        String deckId = store.createDeck("Legacy Deck", "A1", legacyCards);
        assertNotNull(deckId);

        List<Flashcard> restored = store.getCards(deckId);
        assertEquals(1, restored.size());
        assertEquals(0L, restored.get(0).getSavedWordId());
        assertEquals("LOCAL", restored.get(0).getStatus());

        store.deleteDeck(deckId);
    }

    @Test
    public void deckSessionData_encodeAndDecode_preservesServerInfo() {
        List<Flashcard> original = new ArrayList<>();
        original.add(new Flashcard(555L, "Airport", "Sân bay", "At the airport", "MASTERED"));
        original.add(new Flashcard(0L, "LocalCard", "Nghĩa", "", "LOCAL"));

        String encoded = DeckSessionData.encode(original);
        ArrayList<Flashcard> decoded = DeckSessionData.decode(encoded);

        assertEquals(2, decoded.size());
        assertEquals(555L, decoded.get(0).getSavedWordId());
        assertEquals("MASTERED", decoded.get(0).getStatus());
        assertEquals(0L, decoded.get(1).getSavedWordId());
        assertEquals("LOCAL", decoded.get(1).getStatus());
    }

    @Test
    public void serverResponseMapping_correctlyMapsCreatedAndExisting() throws Exception {
        // Giả lập response từ POST /saved-words/from-generated
        JSONObject response = new JSONObject();

        JSONArray created = new JSONArray();
        JSONObject c1 = new JSONObject()
                .put("id", 201)
                .put("word", "Passport")
                .put("meaning", "Hộ chiếu")
                .put("example", "Show your passport")
                .put("status", "NEW");
        created.put(c1);

        JSONArray existing = new JSONArray();
        JSONObject e1 = new JSONObject()
                .put("id", 99)
                .put("word", "  Luggage  ")
                .put("meaning", "Hành lý")
                .put("example", "Heavy luggage")
                .put("status", "LEARNING");
        existing.put(e1);

        response.put("created", created);
        response.put("existing", existing);

        // Ánh xạ tương tự như GeneratedDeckReviewFragment
        Map<String, Flashcard> serverMap = new HashMap<>();
        JSONArray createdArr = response.optJSONArray("created");
        JSONArray existingArr = response.optJSONArray("existing");

        if (createdArr != null) {
            for (int i = 0; i < createdArr.length(); i++) {
                JSONObject obj = createdArr.getJSONObject(i);
                serverMap.put(DeckSessionData.normalize(obj.getString("word")),
                        new Flashcard(obj.getLong("id"), obj.getString("word"),
                                obj.getString("meaning"), obj.optString("example", ""),
                                obj.optString("status", "NEW")));
            }
        }
        if (existingArr != null) {
            for (int i = 0; i < existingArr.length(); i++) {
                JSONObject obj = existingArr.getJSONObject(i);
                serverMap.put(DeckSessionData.normalize(obj.getString("word")),
                        new Flashcard(obj.getLong("id"), obj.getString("word"),
                                obj.getString("meaning"), obj.optString("example", ""),
                                obj.optString("status", "NEW")));
            }
        }

        // Kiểm tra ánh xạ
        Flashcard cardNew = serverMap.get(DeckSessionData.normalize("passport"));
        assertNotNull(cardNew);
        assertEquals(201L, cardNew.getSavedWordId());
        assertEquals("NEW", cardNew.getStatus());

        Flashcard cardExist = serverMap.get(DeckSessionData.normalize("LUGGAGE"));
        assertNotNull(cardExist);
        assertEquals(99L, cardExist.getSavedWordId());
        assertEquals("LEARNING", cardExist.getStatus());
    }
}

