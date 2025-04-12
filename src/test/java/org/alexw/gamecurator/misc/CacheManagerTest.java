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
    private MockedStatic<Files> mockedFiles; 
    private static Field cacheField;
    private static Method saveCacheToFileMethod; 
    private static Method loadCacheFromFileMethod; 

    @TempDir
    Path tempDir;
    private Path testCachePath; 

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create(); 

    private Map<String, Object> getInternalCacheMap() throws Exception {

        return (Map<String, Object>) cacheField.get(null);
    }

    private void setInternalCacheMap(Map<String, ?> map) throws Exception {
        cacheField.set(null, map);
    }

    private Object createCacheEntry(String jsonData, long creationTime) throws Exception {
        Class<?> cacheEntryClass = Class.forName("org.alexw.gamecurator.misc.CacheManager$CacheEntry");
        return cacheEntryClass.getDeclaredConstructor(String.class, long.class)
                .newInstance(jsonData, creationTime);
    }

    private long getCreationTime(Object cacheEntry) throws Exception {
        Field timeField = cacheEntry.getClass().getDeclaredField("creationTime");
        timeField.setAccessible(true);
        return timeField.getLong(cacheEntry);
    }

    private String getJsonData(Object cacheEntry) throws Exception {
        Field dataField = cacheEntry.getClass().getDeclaredField("jsonData");
        dataField.setAccessible(true);
        return (String) dataField.get(cacheEntry);
    }

    @BeforeEach
    void setUp() throws Exception {

        testCachePath = tempDir.resolve("test_cache.json");

        cacheField = CacheManager.class.getDeclaredField("cache");
        cacheField.setAccessible(true);

        mockedCacheManager = mockStatic(CacheManager.class, CALLS_REAL_METHODS);

        mockedFiles = mockStatic(Files.class, CALLS_REAL_METHODS);

        loadCacheFromFileMethod = CacheManager.class.getDeclaredMethod("loadCacheFromFile");
        loadCacheFromFileMethod.setAccessible(true);
        mockedCacheManager.when(() -> loadCacheFromFileMethod.invoke(null))
           .thenReturn(new ConcurrentHashMap<>()); 

        saveCacheToFileMethod = CacheManager.class.getDeclaredMethod("saveCacheToFile");
        saveCacheToFileMethod.setAccessible(true);

        mockedCacheManager.when(() -> saveCacheToFileMethod.invoke(null)).thenAnswer(invocation -> null);

        Field actualCachePathField = CacheManager.class.getDeclaredField("CACHE_FILE_PATH");
        actualCachePathField.setAccessible(true);
        Path actualCachePath = (Path) actualCachePathField.get(null);

        mockedFiles.when(() -> Files.exists(eq(actualCachePath))).thenReturn(false); 
        mockedFiles.when(() -> Files.createDirectories(any(Path.class))).thenAnswer(invocation -> {

             return invocation.getArgument(0);
        });

         mockedFiles.when(() -> Files.newBufferedWriter(eq(actualCachePath), eq(StandardCharsets.UTF_8), any(StandardOpenOption.class)))
             .thenReturn(new BufferedWriter(new StringWriter())); 

        setInternalCacheMap(new ConcurrentHashMap<>());
    }

    @AfterEach
    void tearDown() {

        if (mockedFiles != null) {
            mockedFiles.close();
        }
        if (mockedCacheManager != null) {
            mockedCacheManager.close();
        }
    }

    @Test
    void put_addsItemToCache() throws Exception {
        String key = "testItem";
        String data = "{\"value\": 1}";
        long startTime = System.currentTimeMillis();

        CacheManager.put(key, data);

        Map<String, Object> internalCache = getInternalCacheMap();
        String internalKey = "cache_" + key; 
        assertTrue(internalCache.containsKey(internalKey), "Cache should contain the key");

        Object entry = internalCache.get(internalKey);
        assertNotNull(entry);
        assertEquals(data, getJsonData(entry));
        assertTrue(getCreationTime(entry) >= startTime, "Creation time should be recent");

        mockedCacheManager.verify(() -> saveCacheToFileMethod.invoke(null));
    }

    @Test
    void put_overwritesExistingItem() throws Exception {
        String key = "testItem";
        String oldData = "{\"value\": 1}";
        String newData = "{\"value\": 2}";
        String internalKey = "cache_" + key;

        Map<String, Object> internalCache = getInternalCacheMap();
        internalCache.put(internalKey, createCacheEntry(oldData, System.currentTimeMillis() - 1000));
        setInternalCacheMap(internalCache);

        CacheManager.put(key, newData);

        internalCache = getInternalCacheMap(); 
        assertEquals(1, internalCache.size());
        assertTrue(internalCache.containsKey(internalKey));
        Object entry = internalCache.get(internalKey);
        assertEquals(newData, getJsonData(entry));

        mockedCacheManager.verify(() -> saveCacheToFileMethod.invoke(null));
    }

    @Test
    void get_returnsNullForNonExistentItem() throws Exception {
        assertNull(CacheManager.get("nonExistent"));

         mockedCacheManager.verify(() -> saveCacheToFileMethod.invoke(null), never());
    }

    @Test
    void get_returnsDataForValidItem() throws Exception {
        String key = "validItem";
        String data = "{\"value\": 3}";
        String internalKey = "cache_" + key;

        Map<String, Object> internalCache = getInternalCacheMap();
        internalCache.put(internalKey, createCacheEntry(data, System.currentTimeMillis())); 
        setInternalCacheMap(internalCache);

        String retrievedData = CacheManager.get(key);
        assertEquals(data, retrievedData);

         mockedCacheManager.verify(() -> saveCacheToFileMethod.invoke(null), never());
    }

    @Test
    void get_returnsNullAndRemovesExpiredItem() throws Exception {
        String key = "expiredItem";
        String data = "{\"value\": 4}";
        String internalKey = "cache_" + key;

        Field ttlField = CacheManager.class.getDeclaredField("CACHE_TTL");
        ttlField.setAccessible(true);
        long ttl = ttlField.getLong(null);
        long expiredTime = System.currentTimeMillis() - ttl - 5000; 

        Map<String, Object> internalCache = getInternalCacheMap();
        internalCache.put(internalKey, createCacheEntry(data, expiredTime));
        setInternalCacheMap(internalCache);

        assertTrue(getInternalCacheMap().containsKey(internalKey));

        String retrievedData = CacheManager.get(key);

        assertNull(retrievedData, "Expired item should return null");
        assertFalse(getInternalCacheMap().containsKey(internalKey), "Expired item should be removed from cache");

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

        assertTrue(getInternalCacheMap().containsKey(internalKey)); 

        boolean removed = CacheManager.remove(key);

        assertTrue(removed);
        assertFalse(getInternalCacheMap().containsKey(internalKey));

        mockedCacheManager.verify(() -> saveCacheToFileMethod.invoke(null));
    }

    @Test
    void remove_returnsFalseForNonExistentItem() throws Exception {
        boolean removed = CacheManager.remove("nonExistentRemove");
        assertFalse(removed);

         mockedCacheManager.verify(() -> saveCacheToFileMethod.invoke(null), never());
    }

    @Test
    void clear_removesOnlyPrefixedItems() throws Exception {
        String prefixedKey1 = "cache_item1"; 
        String prefixedKey2 = "cache_item2"; 
        String nonPrefixedKey = "other_item"; 

        Map<String, Object> internalCache = getInternalCacheMap();

        Field prefixField = CacheManager.class.getDeclaredField("CACHE_PREFIX");
        prefixField.setAccessible(true);
        String prefix = (String) prefixField.get(null);

        internalCache.put(prefix + prefixedKey1, createCacheEntry("{}", System.currentTimeMillis()));
        internalCache.put(prefix + prefixedKey2, createCacheEntry("[]", System.currentTimeMillis()));
        internalCache.put(nonPrefixedKey, createCacheEntry("\"abc\"", System.currentTimeMillis())); 
        setInternalCacheMap(internalCache);

        assertEquals(3, getInternalCacheMap().size()); 

        CacheManager.clear(); 

        Map<String, Object> finalCache = getInternalCacheMap();
        assertEquals(1, finalCache.size(), "Only non-prefixed item should remain");
        assertFalse(finalCache.containsKey(prefix + prefixedKey1));
        assertFalse(finalCache.containsKey(prefix + prefixedKey2));
        assertTrue(finalCache.containsKey(nonPrefixedKey)); 

        mockedCacheManager.verify(() -> saveCacheToFileMethod.invoke(null));
    }

     @Test
    void clear_doesNotSaveWhenNoPrefixedItemsExist() throws Exception {
        String nonPrefixedKey = "other_item";
        Map<String, Object> internalCache = getInternalCacheMap();
        internalCache.put(nonPrefixedKey, createCacheEntry("\"abc\"", System.currentTimeMillis())); 
        setInternalCacheMap(internalCache);

        assertEquals(1, getInternalCacheMap().size()); 

        CacheManager.clear();

        assertEquals(1, getInternalCacheMap().size()); 

        mockedCacheManager.verify(() -> saveCacheToFileMethod.invoke(null), never());
    }

    @Test
    void getCount_returnsCorrectCountOfPrefixedItems() throws Exception {
         String prefixedKey1 = "item1";
         String prefixedKey2 = "item2";
         String nonPrefixedKey = "other_item"; 

         Map<String, Object> internalCache = getInternalCacheMap();

         Field prefixField = CacheManager.class.getDeclaredField("CACHE_PREFIX");
         prefixField.setAccessible(true);
         String prefix = (String) prefixField.get(null);

         internalCache.put(prefix + prefixedKey1, createCacheEntry("{}", System.currentTimeMillis()));
         internalCache.put(prefix + prefixedKey2, createCacheEntry("[]", System.currentTimeMillis()));
         internalCache.put(nonPrefixedKey, createCacheEntry("\"abc\"", System.currentTimeMillis()));
         setInternalCacheMap(internalCache);

         assertEquals(2, CacheManager.getCount()); 
    }

     @Test
    void getCount_returnsZeroWhenEmpty() throws Exception {
         setInternalCacheMap(new ConcurrentHashMap<>()); 
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