package com.petal.browser.ui.components

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class BarPrefsTest {

    private fun createFakeSharedPreferences(initialValues: Map<String, Any?>): SharedPreferences {
        val map = initialValues.toMutableMap()
        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method: Method, args: Array<out Any?>? ->
            when (method.name) {
                "getString" -> {
                    val key = args?.get(0) as String
                    val def = args?.get(1) as? String
                    map[key] as? String ?: def
                }
                "getBoolean" -> {
                    val key = args?.get(0) as String
                    val def = args?.get(1) as? Boolean ?: false
                    map[key] as? Boolean ?: def
                }
                "toString" -> "FakeSharedPreferences"
                "hashCode" -> 42
                "equals" -> args?.get(0) === args?.get(0)
                else -> null
            }
        } as SharedPreferences
    }

    @Test
    fun testBarActionParserValid() {
        val actions = BarAction.parseActionList("TABS,NEW_TAB,MENU")
        assertEquals(3, actions.size)
        assertEquals(BarAction.TABS, actions[0])
        assertEquals(BarAction.NEW_TAB, actions[1])
        assertEquals(BarAction.MENU, actions[2])
    }

    @Test
    fun testBarActionParserIgnoresUnknownIds() {
        val actions = BarAction.parseActionList("TABS,INVALID_FOO_ACTION,MENU,UNKNOWN_BAR")
        assertEquals(2, actions.size)
        assertEquals(BarAction.TABS, actions[0])
        assertEquals(BarAction.MENU, actions[1])
    }

    @Test
    fun testBarActionParserRemovesDuplicatesKeepingOrder() {
        val actions = BarAction.parseActionList("TABS,NEW_TAB,TABS,SHARE,NEW_TAB,MENU")
        assertEquals(4, actions.size)
        assertEquals(listOf(BarAction.TABS, BarAction.NEW_TAB, BarAction.SHARE, BarAction.MENU), actions)
    }

    @Test
    fun testBarActionParserEmptyAndNull() {
        assertTrue(BarAction.parseActionList(null).isEmpty())
        assertTrue(BarAction.parseActionList("").isEmpty())
        assertTrue(BarAction.parseActionList("   ").isEmpty())
        assertTrue(BarAction.parseActionList(",, ,").isEmpty())
    }

    @Test
    fun testBarActionSerializer() {
        val actions = listOf(BarAction.TABS, BarAction.NEW_TAB, BarAction.MENU)
        assertEquals("TABS,NEW_TAB,MENU", BarAction.serializeActionList(actions))
    }

    @Test
    fun testBarPrefsEnforcesMenuPresence() {
        // SharedPreferences has no MENU in leading or trailing
        val sp = createFakeSharedPreferences(
            mapOf(
                BarPrefs.KEY_MERGE_LEADING to "TABS",
                BarPrefs.KEY_MERGE_TRAILING to "NEW_TAB,SHARE"
            )
        )
        val (leading, trailing) = BarPrefs.getSanitizedMergeActions(sp)
        assertTrue("Trailing must contain MENU when omitted", trailing.contains(BarAction.MENU))
        assertEquals(BarAction.TABS, leading[0])
        assertTrue(trailing.contains(BarAction.NEW_TAB))
    }

    @Test
    fun testBarPrefsEnforcesMax5ActionsTotal() {
        // Provide 7 actions across leading and trailing
        val sp = createFakeSharedPreferences(
            mapOf(
                BarPrefs.KEY_MERGE_LEADING to "TABS,HOME,SEARCH,BACK",
                BarPrefs.KEY_MERGE_TRAILING to "NEW_TAB,SHARE,MENU"
            )
        )
        val (leading, trailing) = BarPrefs.getSanitizedMergeActions(sp)
        val total = leading.size + trailing.size
        assertTrue("Total actions must not exceed ${BarPrefs.MAX_MERGE_ACTIONS_TOTAL}", total <= BarPrefs.MAX_MERGE_ACTIONS_TOTAL)
        assertTrue("MENU must never be removed during trimming", leading.contains(BarAction.MENU) || trailing.contains(BarAction.MENU))
    }

    @Test
    fun testBarPrefsDefaults() {
        val sp = createFakeSharedPreferences(emptyMap())
        val (leading, trailing) = BarPrefs.getSanitizedMergeActions(sp)
        assertEquals(listOf(BarAction.BACK), leading)
        assertEquals(listOf(BarAction.TABS, BarAction.MENU), trailing)
    }

    @Test
    fun testBarPrefsCaseInsensitiveIds() {
        val actions = BarAction.parseActionList("tabs,new_tab,menu")
        assertEquals(listOf(BarAction.TABS, BarAction.NEW_TAB, BarAction.MENU), actions)
    }

    private fun mutablePrefs(initial: Map<String, Any?> = emptyMap()): Pair<SharedPreferences, MutableMap<String, Any?>> {
        val state = initial.toMutableMap()
        val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "putString", "putBoolean" -> { state[args!![0] as String] = args[1]; proxy }
                "apply" -> null
                "commit" -> true
                else -> proxy
            }
        } as SharedPreferences.Editor
        val prefs = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> state[args!![0] as String] as? String ?: args[1]
                "getBoolean" -> state[args!![0] as String] as? Boolean ?: args[1]
                "contains" -> state.containsKey(args!![0] as String)
                "edit" -> editor
                else -> null
            }
        } as SharedPreferences
        return prefs to state
    }

    @Test
    fun migrationUnifiesLegacySplitBarOnce() {
        val (prefs, state) = mutablePrefs(
            mapOf(BarPrefs.KEY_ADDRESS_BAR_STYLE to BarPrefs.STYLE_CLASSIC,
                  BarPrefs.KEY_ADDRESS_BAR_POSITION to "TOP")
        )
        BarPrefs.migrateToUnifiedBottom(prefs)
        assertEquals(BarPrefs.STYLE_MERGE, state[BarPrefs.KEY_ADDRESS_BAR_STYLE])
        assertEquals("BOTTOM", state[BarPrefs.KEY_ADDRESS_BAR_POSITION])
        assertEquals("BACK", state[BarPrefs.KEY_MERGE_LEADING])
        assertEquals("TABS,MENU", state[BarPrefs.KEY_MERGE_TRAILING])
        assertEquals(BarPrefs.LAYOUT_SINGLE, state[BarPrefs.KEY_MERGE_LAYOUT])
        assertEquals(true, state[BarPrefs.KEY_UNIFIED_BOTTOM_MIGRATED])

        // After migration, the user's later manual preferences must survive restarts.
        state[BarPrefs.KEY_ADDRESS_BAR_STYLE] = BarPrefs.STYLE_CLASSIC
        state[BarPrefs.KEY_ADDRESS_BAR_POSITION] = "TOP"
        BarPrefs.migrateToUnifiedBottom(prefs)
        assertEquals(BarPrefs.STYLE_CLASSIC, state[BarPrefs.KEY_ADDRESS_BAR_STYLE])
        assertEquals("TOP", state[BarPrefs.KEY_ADDRESS_BAR_POSITION])
    }

    @Test
    fun migrationPreservesCustomToolbarActionsAndLayout() {
        val (prefs, state) = mutablePrefs(
            mapOf(
                BarPrefs.KEY_ADDRESS_BAR_STYLE to BarPrefs.STYLE_MERGE,
                BarPrefs.KEY_ADDRESS_BAR_POSITION to "BOTTOM",
                BarPrefs.KEY_MERGE_LEADING to "HOME",
                BarPrefs.KEY_MERGE_TRAILING to "TABS,MENU",
                BarPrefs.KEY_MERGE_LAYOUT to BarPrefs.LAYOUT_STACKED
            )
        )
        BarPrefs.migrateToUnifiedBottom(prefs)
        assertEquals("HOME", state[BarPrefs.KEY_MERGE_LEADING])
        assertEquals("TABS,MENU", state[BarPrefs.KEY_MERGE_TRAILING])
        assertEquals(BarPrefs.LAYOUT_STACKED, state[BarPrefs.KEY_MERGE_LAYOUT])
    }
}
