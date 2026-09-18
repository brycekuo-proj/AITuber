package com.aituber.poc.character.live2d

import org.junit.Assert.assertEquals
import org.junit.Test

class Live2DCharacterProfilesLoopTest {
    @Test
    fun nextOrderIsBroadwayApprovedSequence() {
        assertEquals(Live2DCharacterProfiles.Hijiki.id, Live2DCharacterProfiles.next(Live2DCharacterProfiles.Tororo.id).id)
        assertEquals(Live2DCharacterProfiles.Haru.id, Live2DCharacterProfiles.next(Live2DCharacterProfiles.Hijiki.id).id)
        assertEquals(Live2DCharacterProfiles.LoafDog.id, Live2DCharacterProfiles.next(Live2DCharacterProfiles.Haru.id).id)
        assertEquals(Live2DCharacterProfiles.Tororo.id, Live2DCharacterProfiles.next(Live2DCharacterProfiles.LoafDog.id).id)
    }

    @Test
    fun previousWrapsFromTororoToLoafDog() {
        assertEquals(
            Live2DCharacterProfiles.LoafDog.id,
            Live2DCharacterProfiles.previous(Live2DCharacterProfiles.Tororo.id).id
        )
    }
}
