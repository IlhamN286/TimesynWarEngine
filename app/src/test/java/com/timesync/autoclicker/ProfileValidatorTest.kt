package com.timesync.autoclicker

import com.timesync.autoclicker.profile.*
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileValidatorTest {
    @Test fun acceptsValidProfile() {
        val profile = ApplicationProfile(
            name = "Test App",
            packageName = "com.example.test",
            layout = LayoutConfig(actions = listOf(ActionPoint(name = "Tap 1", x = .5f, y = .7f, order = 1)))
        )
        assertNull(ProfileValidator.validate(profile))
    }

    @Test fun rejectsInvalidPackageName() {
        assertNotNull(ProfileValidator.validate(ApplicationProfile(name = "Bad", packageName = "not a package")))
    }

    @Test fun rejectsOutOfBoundsCoordinates() {
        val profile = ApplicationProfile(
            name = "Test",
            packageName = "com.example.test",
            layout = LayoutConfig(actions = listOf(ActionPoint(x = 1.2f, y = .5f, order = 1)))
        )
        assertNotNull(ProfileValidator.validate(profile))
    }

    @Test fun rejectsDuplicateActionOrder() {
        val profile = ApplicationProfile(
            name = "Test",
            packageName = "com.example.test",
            layout = LayoutConfig(actions = listOf(
                ActionPoint(x = .2f, y = .2f, order = 1),
                ActionPoint(x = .8f, y = .8f, order = 1)
            ))
        )
        assertNotNull(ProfileValidator.validate(profile))
    }
}
