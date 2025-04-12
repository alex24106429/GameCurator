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

    private Gson gson; 
    private LibraryManager libraryManager;

    @BeforeEach
    void setUp() {
        gson = new Gson(); 

        libraryManager = new LibraryManager(mockPrefs, gson);
    }

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

        Set<Integer> ids = libraryManager.getLibraryItemIds();
        assertNotNull(ids);
        assertTrue(ids.isEmpty());

    }

    @Test
    void addLibraryItem_whenNewItem_addsIdAndSaves() throws BackingStoreException {

        when(mockPrefs.get(PREF_LIBRARY, "[]")).thenReturn("[]");
        JsonObject gameData = new JsonObject();
        gameData.addProperty("name", "Test Game");
        gameData.addProperty("genres", "Action"); 

        boolean added = libraryManager.addLibraryItem(GAME_ID_1, gameData);

        assertTrue(added);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockPrefs).put(eq(PREF_LIBRARY), jsonCaptor.capture());
        verify(mockPrefs).flush();

        Set<Integer> savedIds = gson.fromJson(jsonCaptor.getValue(), new com.google.gson.reflect.TypeToken<Set<Integer>>() {}.getType());
        assertTrue(savedIds.contains(GAME_ID_1));
        assertEquals(1, savedIds.size());

    }

     @Test
    void addLibraryItem_whenNewItemWithNullData_addsIdAndSavesWithoutCacheError() throws BackingStoreException {

        when(mockPrefs.get(PREF_LIBRARY, "[]")).thenReturn("[]");

        boolean added = libraryManager.addLibraryItem(GAME_ID_1, null); 

        assertTrue(added);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockPrefs).put(eq(PREF_LIBRARY), jsonCaptor.capture());
        verify(mockPrefs).flush();

        Set<Integer> savedIds = gson.fromJson(jsonCaptor.getValue(), new com.google.gson.reflect.TypeToken<Set<Integer>>() {}.getType());
        assertTrue(savedIds.contains(GAME_ID_1));
        assertEquals(1, savedIds.size());

    }

    @Test
    void addLibraryItem_whenExistingItem_returnsFalseAndDoesNotSave() throws BackingStoreException {

        Set<Integer> existingIds = new HashSet<>(Collections.singletonList(GAME_ID_1));
        String json = gson.toJson(existingIds);
        when(mockPrefs.get(PREF_LIBRARY, "[]")).thenReturn(json);
        JsonObject gameData = new JsonObject(); 

        boolean added = libraryManager.addLibraryItem(GAME_ID_1, gameData);

        assertFalse(added);
        verify(mockPrefs, never()).put(anyString(), anyString());
        verify(mockPrefs, never()).flush();

    }

    @Test
    void removeLibraryItem_whenItemExists_removesIdAndSaves() throws BackingStoreException {

        Set<Integer> existingIds = new HashSet<>(Arrays.asList(GAME_ID_1, GAME_ID_2));
        String json = gson.toJson(existingIds);
        when(mockPrefs.get(PREF_LIBRARY, "[]")).thenReturn(json);

        boolean removed = libraryManager.removeLibraryItem(GAME_ID_1);

        assertTrue(removed);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockPrefs).put(eq(PREF_LIBRARY), jsonCaptor.capture());
        verify(mockPrefs).flush();

        Set<Integer> savedIds = gson.fromJson(jsonCaptor.getValue(), new com.google.gson.reflect.TypeToken<Set<Integer>>() {}.getType());
        assertFalse(savedIds.contains(GAME_ID_1));
        assertTrue(savedIds.contains(GAME_ID_2));
        assertEquals(1, savedIds.size());

    }

    @Test
    void removeLibraryItem_whenItemDoesNotExist_returnsFalseAndDoesNotSave() throws BackingStoreException {

        Set<Integer> existingIds = new HashSet<>(Collections.singletonList(GAME_ID_2));
        String json = gson.toJson(existingIds);
        when(mockPrefs.get(PREF_LIBRARY, "[]")).thenReturn(json);

        boolean removed = libraryManager.removeLibraryItem(GAME_ID_1); 

        assertFalse(removed);
        verify(mockPrefs, never()).put(anyString(), anyString());
        verify(mockPrefs, never()).flush();

    }

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

    @Test
    void clearLibrary_savesEmptySet() throws BackingStoreException {

        libraryManager.clearLibrary();

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockPrefs).put(eq(PREF_LIBRARY), jsonCaptor.capture());
        verify(mockPrefs).flush();

        Set<Integer> savedIds = gson.fromJson(jsonCaptor.getValue(), new com.google.gson.reflect.TypeToken<Set<Integer>>() {}.getType());
        assertTrue(savedIds.isEmpty());
    }

    @Test
    void addLibraryItem_whenFlushThrowsException_propagatesOrLogs() throws BackingStoreException {

        when(mockPrefs.get(PREF_LIBRARY, "[]")).thenReturn("[]");
        JsonObject gameData = new JsonObject();
        gameData.addProperty("name", "Test Game");
        gameData.addProperty("genres", "Action");

        doThrow(new BackingStoreException("Disk full")).when(mockPrefs).flush();

        boolean added = libraryManager.addLibraryItem(GAME_ID_1, gameData);

        assertTrue(added); 
        verify(mockPrefs).put(eq(PREF_LIBRARY), anyString());
        verify(mockPrefs).flush(); 

    }

     @Test
    void removeLibraryItem_whenFlushThrowsException_propagatesOrLogs() throws BackingStoreException {

        Set<Integer> existingIds = new HashSet<>(Collections.singletonList(GAME_ID_1));
        String json = gson.toJson(existingIds);
        when(mockPrefs.get(PREF_LIBRARY, "[]")).thenReturn(json);
        doThrow(new BackingStoreException("Disk full")).when(mockPrefs).flush();

        boolean removed = libraryManager.removeLibraryItem(GAME_ID_1);

        assertTrue(removed); 
        verify(mockPrefs).put(eq(PREF_LIBRARY), anyString());
        verify(mockPrefs).flush(); 
    }

     @Test
    void clearLibrary_whenFlushThrowsException_propagatesOrLogs() throws BackingStoreException {

        doThrow(new BackingStoreException("Disk full")).when(mockPrefs).flush();

        libraryManager.clearLibrary(); 

        verify(mockPrefs).put(eq(PREF_LIBRARY), eq("[]")); 
        verify(mockPrefs).flush(); 
    }
}