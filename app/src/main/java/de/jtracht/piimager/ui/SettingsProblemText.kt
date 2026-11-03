package de.jtracht.piimager.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import de.jtracht.piimager.presentation.describe

/** The translated text for a rejected settings confirmation. */
@Composable internal fun settingsErrorText(error: IllegalArgumentException?): String? = error?.let { LocalContext.current.describe(it) }
