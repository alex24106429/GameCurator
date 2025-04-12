package org.alexw.gamecurator;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LibraryManagerTest {

    private static final String PREF_LIBRARY = "libraryItems";
    private static final int GAME_ID_1 = 123;
    private static final int GAME_ID_2 = 456;

    @Mock
    private Preferences mockPrefs;

    private Gson gson; // Use real Gson
    private LibraryManager libraryManager;

    @BeforeEach
    void setUp() {
        gson = new Gson(); // Initialize real Gson
        // Reset static cache before each test to ensure isolation
        // Note: This assumes CacheManager has a clear or reset method.
        // If not, tests involving cache might interfere.
        // CacheManager.clear(); // Example - adjust if CacheManager API differs
        libraryManager = new LibraryManager(mockPrefs, gson);
    }

    // --- Test getLibraryItemIds ---

    @Test
    void getLibraryItemIds_whenNoPreference_returnsEmptySet() {
        when(mockPrefs.get(PREF_LIBRARY, "[]")).thenReturn("[]");
        Set<Integer> ids = libraryManager.getLibraryItemIds();
        assertNotNull(ids);
        assertTrue(ids.isEmpty());
    }

    @Test
    void getLibraryItemIds_whenPreferenceExists_returnsCorrectSet() {
        Set<Integer> expectedIds = new HashSet<>(Arrays.asList(GAME_ID_1, GAME_ID_2));
        String json = gson.toJson(expectedIds);
        when(mockPrefs.get(PREF_LIBRARY, "[]")).thenReturn(json);

        Set<Integer> actualIds = libraryManager.getLibraryItemIds();

        assertEquals(expectedIds, actualIds);
    }

    @Test
    void getLibraryItemIds_whenPreferenceInvalidJson_returnsEmptySet() {
        when(mockPrefs.get(PREF_LIBRARY, "[]")).thenReturn("[invalid json}");
        // No exception should be thrown, should return empty set gracefully
        Set<Integer> ids = libraryManager.getLibraryItemIds();
        assertNotNull(ids);
        assertTrue(ids.isEmpty());
        // Optionally, verify error was logged (if logging framework was used)
    }

    // --- Test addLibraryItem ---

    @Test
    void addLibraryItem_whenNewItem_addsIdAndSaves() throws BackingStoreException {
        // Arrange: Start with empty library
        when(mockPrefs.get(PREF_LIBRARY, "[]")).thenReturn("[]");
        JsonObject gameData = new JsonObject();
        gameData.addProperty("name", "Test Game");
        gameData.addProperty("genres", "Action"); // Add required fields

        // Act
        boolean added = libraryManager.addLibraryItem(GAME_ID_1, gameData);

        // Assert
        assertTrue(added);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockPrefs).put(eq(PREF_LIBRARY), jsonCaptor.capture());
        verify(mockPrefs).flush();

        Set<Integer> savedIds = gson.fromJson(jsonCaptor.getValue(), new com.google.gson.reflect.TypeToken<Set<Integer>>() {}.getType());
        assertTrue(savedIds.contains(GAME_ID_1));
        assertEquals(1, savedIds.size());

        // We can't easily verify static CacheManager.put, but ensure no NPE etc.
    }

     @Test
    void addLibraryItem_whenNewItemWithNullData_addsIdAndSavesWithoutCacheError() throws BackingStoreException {
        // Arrange: Start with empty library
        when(mockPrefs.get(PREF_LIBRARY, "[]")).thenReturn("[]");

        // Act
        boolean added = libraryManager.addLibraryItem(GAME_ID_1, null); // Pass null gameData

        // Assert
        assertTrue(added);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockPrefs).put(eq(PREF_LIBRARY), jsonCaptor.capture());
        verify(mockPrefs).flush();

        Set<Integer> savedIds = gson.fromJson(jsonCaptor.getValue(), new com.google.gson.reflect.TypeToken<Set<Integer>>() {}.getType());
        assertTrue(savedIds.contains(GAME_ID_1));
        assertEquals(1, savedIds.size());
        // Verify CacheManager.put was NOT called implicitly by checking no NPE occurred
    }

    @Test
    void addLibraryItem_whenExistingItem_returnsFalseAndDoesNotSave() throws BackingStoreException {
        // Arrange: Library already contains GAME_ID_1
        Set<Integer> existingIds = new HashSet<>(Collections.singletonList(GAME_ID_1));
        String json = gson.toJson(existingIds);
        when(mockPrefs.get(PREF_LIBRARY, "[]")).thenReturn(json);
        JsonObject gameData = new JsonObject(); // Data doesn't matter here

        // Act
        boolean added = libraryManager.addLibraryItem(GAME_ID_1, gameData);

        // Assert
        assertFalse(added);
        verify(mockPrefs, never()).put(anyString(), anyString());
        verify(mockPrefs, never()).flush();
        // Verify CacheManager.put was NOT called
    }

    // --- Test removeLibraryItem ---

    @Test
    void removeLibraryItem_whenItemExists_removesIdAndSaves() throws BackingStoreException {
        // Arrange: Library contains GAME_ID_1 and GAME_ID_2
        Set<Integer> existingIds = new HashSet<>(Arrays.asList(GAME_ID_1, GAME_ID_2));
        String json = gson.toJson(existingIds);
        when(mockPrefs.get(PREF_LIBRARY, "[]")).thenReturn(json);

        // Act
        boolean removed = libraryManager.removeLibraryItem(GAME_ID_1);

        // Assert
        assertTrue(removed);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockPrefs).put(eq(PREF_LIBRARY), jsonCaptor.capture());
        verify(mockPrefs).flush();

        Set<Integer> savedIds = gson.fromJson(jsonCaptor.getValue(), new com.google.gson.reflect.TypeToken<Set<Integer>>() {}.getType());
        assertFalse(savedIds.contains(GAME_ID_1));
        assertTrue(savedIds.contains(GAME_ID_2));
        assertEquals(1, savedIds.size());
        // We can't easily verify static CacheManager.remove
    }

    @Test
    void removeLibraryItem_whenItemDoesNotExist_returnsFalseAndDoesNotSave() throws BackingStoreException {
        // Arrange: Library contains only GAME_ID_2
        Set<Integer> existingIds = new HashSet<>(Collections.singletonList(GAME_ID_2));
        String json = gson.toJson(existingIds);
        when(mockPrefs.get(PREF_LIBRARY, "[]")).thenReturn(json);

        // Act
        boolean removed = libraryManager.removeLibraryItem(GAME_ID_1); // Try to remove non-existent ID

        // Assert
        assertFalse(removed);
        verify(mockPrefs, never()).put(anyString(), anyString());
        verify(mockPrefs, never()).flush();
        // Verify CacheManager.remove was NOT called
    }

    // --- Test isInLibrary ---

    @Test
    void isInLibrary_whenItemExists_returnsTrue() {
        Set<Integer> existingIds = new HashSet<>(Collections.singletonList(GAME_ID_1));
        String json = gson.toJson(existingIds);
        when(mockPrefs.get(PREF_LIBRARY, "[]")).thenReturn(json);

        assertTrue(libraryManager.isInLibrary(GAME_ID_1));
    }

    @Test
    void isInLibrary_whenItemDoesNotExist_returnsFalse() {
        Set<Integer> existingIds = new HashSet<>(Collections.singletonList(GAME_ID_2));
        String json = gson.toJson(existingIds);
        when(mockPrefs.get(PREF_LIBRARY, "[]")).thenReturn(json);

        assertFalse(libraryManager.isInLibrary(GAME_ID_1));
    }

    @Test
    void isInLibrary_whenLibraryEmpty_returnsFalse() {
        when(mockPrefs.get(PREF_LIBRARY, "[]")).thenReturn("[]");
        assertFalse(libraryManager.isInLibrary(GAME_ID_1));
    }

    // --- Test clearLibrary ---

    @Test
    void clearLibrary_savesEmptySet() throws BackingStoreException {
        // Arrange (no stubbing needed for get as clearLibrary doesn't read)

        // Act
        libraryManager.clearLibrary();

        // Assert
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockPrefs).put(eq(PREF_LIBRARY), jsonCaptor.capture());
        verify(mockPrefs).flush();

        Set<Integer> savedIds = gson.fromJson(jsonCaptor.getValue(), new com.google.gson.reflect.TypeToken<Set<Integer>>() {}.getType());
        assertTrue(savedIds.isEmpty());
    }

    // --- Test Exception Handling ---

    @Test
    void addLibraryItem_whenFlushThrowsException_propagatesOrLogs() throws BackingStoreException {
        // Arrange: Start with empty library
        when(mockPrefs.get(PREF_LIBRARY, "[]")).thenReturn("[]");
        JsonObject gameData = new JsonObject();
        gameData.addProperty("name", "Test Game");
        gameData.addProperty("genres", "Action");
        // Configure mock to throw exception on flush
        doThrow(new BackingStoreException("Disk full")).when(mockPrefs).flush();

        // Act & Assert
        // Depending on desired behavior: either assert the exception is caught and logged
        // (and addLibraryItem returns true/false appropriately), or expect it to propagate.
        // Current implementation catches and logs. Let's verify put was called, but flush failed.
        boolean added = libraryManager.addLibraryItem(GAME_ID_1, gameData);

        assertTrue(added); // Item is added to set before flush fails
        verify(mockPrefs).put(eq(PREF_LIBRARY), anyString());
        verify(mockPrefs).flush(); // Verify flush was attempted
        // Optionally verify error log output if possible
    }

     @Test
    void removeLibraryItem_whenFlushThrowsException_propagatesOrLogs() throws BackingStoreException {
        // Arrange: Library contains GAME_ID_1
        Set<Integer> existingIds = new HashSet<>(Collections.singletonList(GAME_ID_1));
        String json = gson.toJson(existingIds);
        when(mockPrefs.get(PREF_LIBRARY, "[]")).thenReturn(json);
        doThrow(new BackingStoreException("Disk full")).when(mockPrefs).flush();

        // Act
        boolean removed = libraryManager.removeLibraryItem(GAME_ID_1);

        // Assert
        assertTrue(removed); // Item is removed from set before flush fails
        verify(mockPrefs).put(eq(PREF_LIBRARY), anyString());
        verify(mockPrefs).flush(); // Verify flush was attempted
    }

     @Test
    void clearLibrary_whenFlushThrowsException_propagatesOrLogs() throws BackingStoreException {
        // Arrange
        doThrow(new BackingStoreException("Disk full")).when(mockPrefs).flush();

        // Act
        libraryManager.clearLibrary(); // Should not throw exception itself

        // Assert
        verify(mockPrefs).put(eq(PREF_LIBRARY), eq("[]")); // Verify put was called with empty set
        verify(mockPrefs).flush(); // Verify flush was attempted
    }
}