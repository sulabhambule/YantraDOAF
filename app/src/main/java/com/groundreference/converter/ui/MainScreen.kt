package com.groundreference.converter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.groundreference.converter.ClipboardCoordinateParser
import com.groundreference.converter.GroundReferenceService
import com.groundreference.converter.RecommendationService

/**
 * MainScreen.kt
 *
 * Wide, single-screen (no-scroll overall) layout for landscape controller
 * displays, made of three columns:
 *   1. Reference Points  - My Position + Target Coordinates, each with a
 *      LIVE auto-calculated Ground Reference. This column alone scrolls
 *      internally if its content runs taller than the screen.
 *   2. Current Coordinate - fed from clipboard (Paste) or typed manually,
 *      with a LIVE Ground Reference and a LIVE "Suggestions" recommendation
 *      once both Current and Target are valid. Paste + Clear sit side by
 *      side.
 *   3. Map View - Me / Current / Target compass map, also live.
 *
 * Coordinate fields are custom compact fields (built on BasicTextField)
 * instead of the stock Material3 OutlinedTextField, so the internal
 * padding around the label/placeholder can be tightened directly rather
 * than fighting Material3's fixed default sizing.
 *
 * GroundReferenceService and RecommendationService still hold all the
 * actual logic; this file is UI + simple derived-state plumbing only.
 */

private val groundReferenceService = GroundReferenceService()
private val recommendationService = RecommendationService()

// Font sizes: compact enough that everything fits without scrolling
// (except column 1, which scrolls on its own), but still readable.
private val HEADER_SIZE = 15.sp
private val LABEL_SIZE = 11.sp
private val PLACEHOLDER_SIZE = 10.sp
private val FIELD_TEXT_SIZE = 14.sp
private val OUTPUT_TEXT_SIZE = 13.sp
private val BUTTON_TEXT_SIZE = 11.sp

/** Parsed + range-validated lat/lon pair, or null if blank/invalid. */
private data class ParsedInput(val latitude: Double, val longitude: Double, val invalid: Boolean)

private fun parseCoordinateInput(latText: String, lonText: String): ParsedInput? {
    val lat = latText.trim()
    val lon = lonText.trim()
    if (lat.isEmpty() && lon.isEmpty()) return null // nothing typed yet

    val latVal = lat.toDoubleOrNull()
    val lonVal = lon.toDoubleOrNull()
    if (latVal == null || lonVal == null) return ParsedInput(0.0, 0.0, invalid = true)
    if (latVal < -90 || latVal > 90 || lonVal < -180 || lonVal > 180) {
        return ParsedInput(0.0, 0.0, invalid = true)
    }
    return ParsedInput(latVal, lonVal, invalid = false)
}

private fun groundReferenceOrBlank(parsed: ParsedInput?): String {
    if (parsed == null) return ""
    if (parsed.invalid) return "Invalid coordinates"
    return try {
        groundReferenceService.convertLatLonToGroundReference(parsed.latitude, parsed.longitude)
    } catch (ex: Exception) {
        "Error: ${ex.message}"
    }
}

@Composable
fun MainScreen() {
    // ---------------- Reference Points (Column 1) ----------------
    var myLatitudeText by remember { mutableStateOf("") }
    var myLongitudeText by remember { mutableStateOf("") }
    var targetLatitudeText by remember { mutableStateOf("") }
    var targetLongitudeText by remember { mutableStateOf("") }

    // ---------------- Current Coordinate (Column 2) ----------------
    var currentLatitudeText by remember { mutableStateOf("") }
    var currentLongitudeText by remember { mutableStateOf("") }
    var pasteError by remember { mutableStateOf<String?>(null) }

    val clipboardManager = LocalClipboardManager.current

    // ---------------- Live derived values (no buttons needed) ----------------
    val myParsed = parseCoordinateInput(myLatitudeText, myLongitudeText)
    val targetParsed = parseCoordinateInput(targetLatitudeText, targetLongitudeText)
    val currentParsed = parseCoordinateInput(currentLatitudeText, currentLongitudeText)

    val myGroundReference = groundReferenceOrBlank(myParsed)
    val targetGroundReference = groundReferenceOrBlank(targetParsed)
    val currentGroundReference = groundReferenceOrBlank(currentParsed)

    val suggestion: String = if (currentParsed != null && !currentParsed.invalid &&
        targetParsed != null && !targetParsed.invalid
    ) {
        try {
            recommendationService.calculateRecommendation(
                currentParsed.latitude, currentParsed.longitude,
                targetParsed.latitude, targetParsed.longitude
            )
        } catch (ex: Exception) {
            "Error: ${ex.message}"
        }
    } else ""

    val mapMe = myParsed?.takeIf { !it.invalid }?.let { LatLon(it.latitude, it.longitude) }
    val mapTarget = targetParsed?.takeIf { !it.invalid }?.let { LatLon(it.latitude, it.longitude) }
    val mapCurrent = currentParsed?.takeIf { !it.invalid }?.let { LatLon(it.latitude, it.longitude) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        // =========================================================
        // COLUMN 1: Reference Points (My Position + Target)
        // Scrolls internally since it tends to run tallest.
        // =========================================================
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            ColumnCard(title = "Reference Points", scrollable = true) {

                Text("Artillary Coordinates", fontSize = LABEL_SIZE, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                CoordinateField(myLatitudeText, { myLatitudeText = it }, "My Latitude", "22.5726")
                Spacer(Modifier.height(4.dp))
                CoordinateField(myLongitudeText, { myLongitudeText = it }, "My Longitude", "88.3639")
                Spacer(Modifier.height(4.dp))
                GroundReferenceOutput(label = "Artillary - Ground Reference", value = myGroundReference)

                Spacer(Modifier.height(12.dp))

                Text("Target Coordinates", fontSize = LABEL_SIZE, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                CoordinateField(targetLatitudeText, { targetLatitudeText = it }, "Target Latitude", "22.58")
                Spacer(Modifier.height(4.dp))
                CoordinateField(targetLongitudeText, { targetLongitudeText = it }, "Target Longitude", "88.40")
                Spacer(Modifier.height(4.dp))
                GroundReferenceOutput(label = "Target - Ground Reference", value = targetGroundReference)

                Spacer(Modifier.height(10.dp))
                CompactButton(
                    text = "Clear",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        myLatitudeText = ""; myLongitudeText = ""
                        targetLatitudeText = ""; targetLongitudeText = ""
                    }
                )
            }
        }

        // =========================================================
        // COLUMN 2: Current Coordinate (clipboard or manual) + Suggestions
        // =========================================================
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            ColumnCard(title = "Shell Drop Coordinate") {

                CoordinateField(currentLatitudeText, { currentLatitudeText = it }, "Latitude", "22.5726")
                Spacer(Modifier.height(4.dp))
                CoordinateField(currentLongitudeText, { currentLongitudeText = it }, "Longitude", "88.3639")

                Spacer(Modifier.height(6.dp))
                // Paste + Clear sit side by side now, each taking half the width.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactButton(
                        text = "Paste",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val clipText = clipboardManager.getText()?.text
                            val parsed = ClipboardCoordinateParser.parse(clipText)
                            if (parsed != null) {
                                currentLatitudeText = parsed.latitude.toString()
                                currentLongitudeText = parsed.longitude.toString()
                                pasteError = null
                            } else {
                                pasteError = "Clipboard has no recognizable coordinate."
                            }
                        }
                    )
                    CompactButton(
                        text = "Clear",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            currentLatitudeText = ""; currentLongitudeText = ""; pasteError = null
                        }
                    )
                }
                pasteError?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                }

                Spacer(Modifier.height(10.dp))
                GroundReferenceOutput(label = "Ground Reference", value = currentGroundReference)

                Spacer(Modifier.height(10.dp))
                SuggestionOutput(label = "Suggestions", value = suggestion)
            }
        }

        // =========================================================
        // COLUMN 3: Map View
        // =========================================================
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            ColumnCard(title = "Map View ", fillHeight = true) {
                MapView(
                    artillery  = mapMe,
                    Shell  = mapCurrent,
                    target = mapTarget,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

/**
 * A titled card that fills the height of its parent column.
 *
 * @param scrollable if true, wraps the content column in a vertical
 *        scroll so it can exceed the visible height without clipping
 *        (used for Column 1, which tends to run tallest).
 * @param fillHeight if true, the content Column also fills all
 *        available height (used for Column 3's Map, which needs to
 *        expand to fill remaining vertical space).
 */
@Composable
private fun ColumnCard(
    title: String,
    fillHeight: Boolean = false,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    Card(modifier = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
        Column(
            modifier = (if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier)
                .padding(10.dp)
        ) {
            Text(title, fontSize = HEADER_SIZE, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/**
 * A compact coordinate input field with tight internal padding.
 *
 * Built on BasicTextField (not Material3's OutlinedTextField) so the
 * padding around the label/placeholder/typed text can be controlled
 * directly instead of relying on Material3's larger fixed defaults.
 */
@Composable
private fun CoordinateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp) // <-- tight padding, per feedback
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontSize = FIELD_TEXT_SIZE,
                color = MaterialTheme.colorScheme.onSurface
            ),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            // Plain text keyboard (not number-only) so negative values for
            // South/West latitudes and longitudes can be typed directly.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.isEmpty()) {
                        Text(
                            text = label,   // Shows "My Latitude" / "My Longitude"
                            fontSize = PLACEHOLDER_SIZE,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

/** A read-only, live-updating output box for a Ground Reference / Suggestion value. */
@Composable
private fun GroundReferenceOutput(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = LABEL_SIZE, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = value,
                fontSize = OUTPUT_TEXT_SIZE,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SuggestionOutput(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = LABEL_SIZE,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(2.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)   // Increase height as needed (100.dp, 120.dp, 150.dp...)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = value,
                fontSize = OUTPUT_TEXT_SIZE,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
                // Removed maxLines = 1 so the suggestion can wrap to multiple lines
            )
        }
    }
}

/** A small, low-profile button - sized down per feedback that buttons were too big. */
@Composable
private fun CompactButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
    ) {
        Text(text, fontSize = BUTTON_TEXT_SIZE)
    }
}