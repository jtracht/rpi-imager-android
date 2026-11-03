package de.jtracht.piimager.presentation

/** The guided flow of the main screen, in task order. */
enum class WizardStep {
    Model, Image, Settings, Storage, Write;

    companion object {
        /**
         * The furthest step the user may open. Settings need a chosen image (it loads meanwhile), choosing a
         * target needs the finished image with its settings, and writing additionally needs a target.
         */
        fun furthest(imageChosen: Boolean, imageReady: Boolean, targetReady: Boolean): WizardStep = when {
            !imageChosen -> Image
            !imageReady -> Settings
            !targetReady -> Storage
            else -> Write
        }
    }
}
