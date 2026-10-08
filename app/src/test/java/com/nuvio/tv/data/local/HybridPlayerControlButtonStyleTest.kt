package com.nuvio.tv.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class HybridPlayerControlButtonStyleTest {
    @Test fun `hybrid labels the left and centre groups and uses icons on the right`() {
        val hybrid = PlayerControlButtonStyle.HYBRID
        assertEquals(PlayerControlButtonStyle.LABELLED, hybrid.forGroup(PlayerControlGroup.LEFT))
        assertEquals(PlayerControlButtonStyle.LABELLED, hybrid.forGroup(PlayerControlGroup.CENTRE))
        assertEquals(PlayerControlButtonStyle.PREVIOUS, hybrid.forGroup(PlayerControlGroup.RIGHT))
    }

    @Test fun `icons and labels styles are the same in every group`() {
        for (group in PlayerControlGroup.entries) {
            assertEquals(PlayerControlButtonStyle.PREVIOUS, PlayerControlButtonStyle.PREVIOUS.forGroup(group))
            assertEquals(PlayerControlButtonStyle.LABELLED, PlayerControlButtonStyle.LABELLED.forGroup(group))
        }
    }

    @Test fun `hybrid layouts survive saving and loading`() {
        val layout = PlayerControlLayout.default().withStyle(PlayerControlButtonStyle.HYBRID)
        assertEquals(layout, PlayerControlLayout.decode(layout.encode()))
        assertEquals(PlayerControlButtonStyle.HYBRID, PlayerControlLayout.decode(layout.encode())!!.style)
    }
}
