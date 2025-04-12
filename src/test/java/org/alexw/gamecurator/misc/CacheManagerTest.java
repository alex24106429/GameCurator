package org.alexw.gamecurator.misc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CacheManagerTest {

    private MockedStatic<CacheManager> mockedCacheManager;
    private MockedStatic<Files> mockedFiles; // Mock for java.nio.file.Files
    private static Field cacheField;
    private static Method saveCacheToFileMethod; // To verify calls
    private static Method loadCacheFromFileMethod; // To mock return value

    @TempDir
    Path tempDir;
    private Path testCachePath; // Path within tempDir for mocked operations

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create(); // For test assertions

    private Map<String, Object> getInternalCacheMap() throws Exception {
        // Type erasure means we get Map<String, Object>, need casting for CacheEntry
        return (Map<String, Object>) cacheField.get(null);
    }

    private void setInternalCacheMap(Map<String, ?> map) throws Exception {
        cacheField.set(null, map);
    }

    // Helper to create CacheEntry instances via reflection (since it's private)
    private Object createCacheEntry(String jsonData, long creationTime) throws Exception {
        Class<?> cacheEntryClass = Class.forName("org.alexw.gamecurator.misc.CacheManager$CacheEntry");
        return cacheEntryClass.getDeclaredConstructor(String.class, long.class)
                .newInstance(jsonData, creationTime);
    }

    // Helper to get creationTime from CacheEntry via reflection
    private long getCreationTime(Object cacheEntry) throws Exception {
        Field timeField = cacheEntry.getClass().getDeclaredField("creationTime");
        timeField.setAccessible(true);
        return timeField.getLong(cacheEntry);
    }
     // Helper to get jsonData from CacheEntry via reflection
    private String getJsonData(Object cacheEntry) throws Exception {
        Field dataField = cacheEntry.getClass().getDeclaredField("jsonData");
        dataField.setAccessible(true);
        return (String) dataField.get(cacheEntry);
    }


    @BeforeEach
    void setUp() throws Exception {
        // --- Define Test Path ---
        testCachePath = tempDir.resolve("test_cache.json");

        // --- Setup Reflection for internal cache map ---
        cacheField = CacheManager.class.getDeclaredField("cache");
        cacheField.setAccessible(true);

        // --- Setup Mocking ---
        // Mock CacheManager static methods first
        mockedCacheManager = mockStatic(CacheManager.class, CALLS_REAL_METHODS);

        // Mock Files static methods
        mockedFiles = mockStatic(Files.class, CALLS_REAL_METHODS);

        // --- Mock CacheManager internal methods ---
        // Mock loadCacheFromFile to return an empty map initially
        loadCacheFromFileMethod = CacheManager.class.getDeclaredMethod("loadCacheFromFile");
        loadCacheFromFileMethod.setAccessible(true);
        mockedCacheManager.when(() -> loadCacheFromFileMethod.invoke(null))
           .thenReturn(new ConcurrentHashMap<>()); // Return empty map, bypassing file read

        // Mock saveCacheToFile to prevent actual file writing
        saveCacheToFileMethod = CacheManager.class.getDeclaredMethod("saveCacheToFile");
        saveCacheToFileMethod.setAccessible(true);
        // We still want to verify it's called, but don't want it to *do* anything
        mockedCacheManager.when(() -> saveCacheToFileMethod.invoke(null)).thenAnswer(invocation -> null);


        // --- Mock Files methods to interact with our test path ---
        // Get the *actual* CACHE_FILE_PATH used by CacheManager for mocking targets
        Field actualCachePathField = CacheManager.class.getDeclaredField("CACHE_FILE_PATH");
        actualCachePathField.setAccessible(true);
        Path actualCachePath = (Path) actualCachePathField.get(null);

        mockedFiles.when(() -> Files.exists(eq(actualCachePath))).thenReturn(false); // Assume file doesn't exist initially for load
        mockedFiles.when(() -> Files.createDirectories(any(Path.class))).thenAnswer(invocation -> {
             // Simulate directory creation if needed, usually just return the path
             return invocation.getArgument(0);
        });
        // Mock writing - return correct type (BufferedWriter)
         mockedFiles.when(() -> Files.newBufferedWriter(eq(actualCachePath), eq(StandardCharsets.UTF_8), any(StandardOpenOption.class)))
             .thenReturn(new BufferedWriter(new StringWriter())); // Return a dummy BufferedWriter


        // Reset the internal cache to a known empty state for each test *after* mocking load
        setInternalCacheMap(new ConcurrentHashMap<>());
    }

    @AfterEach
    void tearDown() {
        // Close mocks in reverse order of creation
        if (mockedFiles != null) {
            mockedFiles.close();
        }
        if (mockedCacheManager != null) {
            mockedCacheManager.close();
        }
    }

    // --- Tests ---

    @Test
    void put_addsItemToCache() throws Exception {
        String key = "testItem";
        String data = "{\"value\": 1}";
        long startTime = System.currentTimeMillis();

        CacheManager.put(key, data);

        Map<String, Object> internalCache = getInternalCacheMap();
        String internalKey = "cache_" + key; // Check prefixed key
        assertTrue(internalCache.containsKey(internalKey), "Cache should contain the key");

        Object entry = internalCache.get(internalKey);
        assertNotNull(entry);
        assertEquals(data, getJsonData(entry));
        assertTrue(getCreationTime(entry) >= startTime, "Creation time should be recent");

        // Verify internal save method was triggered
        mockedCacheManager.verify(() -> saveCacheToFileMethod.invoke(null));
    }

    @Test
    void put_overwritesExistingItem() throws Exception {
        String key = "testItem";
        String oldData = "{\"value\": 1}";
        String newData = "{\"value\": 2}";
        String internalKey = "cache_" + key;

        // Pre-populate cache
        Map<String, Object> internalCache = getInternalCacheMap();
        internalCache.put(internalKey, createCacheEntry(oldData, System.currentTimeMillis() - 1000));
        setInternalCacheMap(internalCache);

        CacheManager.put(key, newData);

        internalCache = getInternalCacheMap(); // Re-get after put
        assertEquals(1, internalCache.size());
        assertTrue(internalCache.containsKey(internalKey));
        Object entry = internalCache.get(internalKey);
        assertEquals(newData, getJsonData(entry));

        // Verify internal save method was triggered
        mockedCacheManager.verify(() -> saveCacheToFileMethod.invoke(null));
    }


    @Test
    void get_returnsNullForNonExistentItem() throws Exception {
        assertNull(CacheManager.get("nonExistent"));
        // Verify internal save method was NOT triggered
         mockedCacheManager.verify(() -> saveCacheToFileMethod.invoke(null), never());
    }

    @Test
    void get_returnsDataForValidItem() throws Exception {
        String key = "validItem";
        String data = "{\"value\": 3}";
        String internalKey = "cache_" + key;

        Map<String, Object> internalCache = getInternalCacheMap();
        internalCache.put(internalKey, createCacheEntry(data, System.currentTimeMillis())); // Not expired
        setInternalCacheMap(internalCache);

        String retrievedData = CacheManager.get(key);
        assertEquals(data, retrievedData);

         // Verify internal save method was NOT triggered for a valid get
         mockedCacheManager.verify(() -> saveCacheToFileMethod.invoke(null), never());
    }

    @Test
    void get_returnsNullAndRemovesExpiredItem() throws Exception {
        String key = "expiredItem";
        String data = "{\"value\": 4}";
        String internalKey = "cache_" + key;
        // Access TTL via reflection
        Field ttlField = CacheManager.class.getDeclaredField("CACHE_TTL");
        ttlField.setAccessible(true);
        long ttl = ttlField.getLong(null);
        long expiredTime = System.currentTimeMillis() - ttl - 5000; // 5 seconds past TTL

        Map<String, Object> internalCache = getInternalCacheMap();
        internalCache.put(internalKey, createCacheEntry(data, expiredTime));
        setInternalCacheMap(internalCache);

        // Pre-check
        assertTrue(getInternalCacheMap().containsKey(internalKey));

        // Act
        String retrievedData = CacheManager.get(key);

        // Assert
        assertNull(retrievedData, "Expired item should return null");
        assertFalse(getInternalCacheMap().containsKey(internalKey), "Expired item should be removed from cache");

        // Verify internal save method WAS triggered due to removal
        mockedCacheManager.verify(() -> saveCacheToFileMethod.invoke(null));
    }

    @Test
    void remove_removesExistingItem() throws Exception {
        String key = "removeItem";
        String data = "{\"value\": 5}";
        String internalKey = "cache_" + key;

        Map<String, Object> internalCache = getInternalCacheMap();
        internalCache.put(internalKey, createCacheEntry(data, System.currentTimeMillis()));
        setInternalCacheMap(internalCache);

        assertTrue(getInternalCacheMap().containsKey(internalKey)); // Pre-check

        boolean removed = CacheManager.remove(key);

        assertTrue(removed);
        assertFalse(getInternalCacheMap().containsKey(internalKey));

        // Verify internal save method was triggered
        mockedCacheManager.verify(() -> saveCacheToFileMethod.invoke(null));
    }

    @Test
    void remove_returnsFalseForNonExistentItem() throws Exception {
        boolean removed = CacheManager.remove("nonExistentRemove");
        assertFalse(removed);
         // Verify internal save method was NOT triggered
         mockedCacheManager.verify(() -> saveCacheToFileMethod.invoke(null), never());
    }

    @Test
    void clear_removesOnlyPrefixedItems() throws Exception {
        String prefixedKey1 = "cache_item1"; // Uses default prefix
        String prefixedKey2 = "cache_item2"; // Uses default prefix
        String nonPrefixedKey = "other_item"; // Does NOT use prefix

        Map<String, Object> internalCache = getInternalCacheMap();
        // Need to use the actual prefix when putting directly for test setup
        Field prefixField = CacheManager.class.getDeclaredField("CACHE_PREFIX");
        prefixField.setAccessible(true);
        String prefix = (String) prefixField.get(null);

        internalCache.put(prefix + prefixedKey1, createCacheEntry("{}", System.currentTimeMillis()));
        internalCache.put(prefix + prefixedKey2, createCacheEntry("[]", System.currentTimeMillis()));
        internalCache.put(nonPrefixedKey, createCacheEntry("\"abc\"", System.currentTimeMillis())); // Add non-prefixed item directly
        setInternalCacheMap(internalCache);

        assertEquals(3, getInternalCacheMap().size()); // Pre-check

        CacheManager.clear(); // clear() uses the prefix internally

        Map<String, Object> finalCache = getInternalCacheMap();
        assertEquals(1, finalCache.size(), "Only non-prefixed item should remain");
        assertFalse(finalCache.containsKey(prefix + prefixedKey1));
        assertFalse(finalCache.containsKey(prefix + prefixedKey2));
        assertTrue(finalCache.containsKey(nonPrefixedKey)); // Non-prefixed should remain

        // Verify internal save method was triggered because items were removed
        mockedCacheManager.verify(() -> saveCacheToFileMethod.invoke(null));
    }

     @Test
    void clear_doesNotSaveWhenNoPrefixedItemsExist() throws Exception {
        String nonPrefixedKey = "other_item";
        Map<String, Object> internalCache = getInternalCacheMap();
        internalCache.put(nonPrefixedKey, createCacheEntry("\"abc\"", System.currentTimeMillis())); // Add non-prefixed item directly
        setInternalCacheMap(internalCache);

        assertEquals(1, getInternalCacheMap().size()); // Pre-check

        CacheManager.clear();

        assertEquals(1, getInternalCacheMap().size()); // Should be unchanged

        // Verify internal save method was NOT triggered
        mockedCacheManager.verify(() -> saveCacheToFileMethod.invoke(null), never());
    }


    @Test
    void getCount_returnsCorrectCountOfPrefixedItems() throws Exception {
         String prefixedKey1 = "item1";
         String prefixedKey2 = "item2";
         String nonPrefixedKey = "other_item"; // Does NOT use prefix

         Map<String, Object> internalCache = getInternalCacheMap();
         // Need to use the actual prefix when putting directly for test setup
         Field prefixField = CacheManager.class.getDeclaredField("CACHE_PREFIX");
         prefixField.setAccessible(true);
         String prefix = (String) prefixField.get(null);

         internalCache.put(prefix + prefixedKey1, createCacheEntry("{}", System.currentTimeMillis()));
         internalCache.put(prefix + prefixedKey2, createCacheEntry("[]", System.currentTimeMillis()));
         internalCache.put(nonPrefixedKey, createCacheEntry("\"abc\"", System.currentTimeMillis()));
         setInternalCacheMap(internalCache);

         assertEquals(2, CacheManager.getCount()); // Should only count prefixed items
    }

     @Test
    void getCount_returnsZeroWhenEmpty() throws Exception {
         setInternalCacheMap(new ConcurrentHashMap<>()); // Ensure empty
         assertEquals(0, CacheManager.getCount());
    }

     @Test
    void getCount_returnsZeroWhenOnlyNonPrefixedItemsExist() throws Exception {
         String nonPrefixedKey = "other_item";
         Map<String, Object> internalCache = getInternalCacheMap();
         internalCache.put(nonPrefixedKey, createCacheEntry("\"abc\"", System.currentTimeMillis()));
         setInternalCacheMap(internalCache);
         assertEquals(0, CacheManager.getCount());
    }
}