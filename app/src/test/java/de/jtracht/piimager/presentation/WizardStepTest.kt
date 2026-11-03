package de.jtracht.piimager.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class WizardStepTest {
    @Test fun withoutImageOnlyModelAndImageAreReachable() {
        assertEquals(WizardStep.Image, WizardStep.furthest(imageChosen = false, imageReady = false, targetReady = false))
    }

    @Test fun chosenImageUnlocksSettingsWhileItLoads() {
        assertEquals(WizardStep.Settings, WizardStep.furthest(imageChosen = true, imageReady = false, targetReady = false))
    }

    @Test fun targetWithoutReadyImageDoesNotUnlockWriting() {
        assertEquals(WizardStep.Settings, WizardStep.furthest(imageChosen = true, imageReady = false, targetReady = true))
        assertEquals(WizardStep.Image, WizardStep.furthest(imageChosen = false, imageReady = false, targetReady = true))
    }

    @Test fun readyImageUnlocksStorage() {
        assertEquals(WizardStep.Storage, WizardStep.furthest(imageChosen = true, imageReady = true, targetReady = false))
    }

    @Test fun readyImageAndTargetUnlockWriting() {
        assertEquals(WizardStep.Write, WizardStep.furthest(imageChosen = true, imageReady = true, targetReady = true))
    }
}
