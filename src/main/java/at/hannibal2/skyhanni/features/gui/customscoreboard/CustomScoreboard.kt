//
// TODO LIST
// V2 RELEASE
//  - Soulflow API
//  - Bank API (actually maybe not, I like the current design)
//  - beacon power
//  - skyblock level
//  - more bg options (round, blurr, outline)
//  - countdown events like fishing festival + fiesta when its not on tablist
//  - CookieAPI https://discord.com/channels/997079228510117908/1162844830360146080/1195695210433351821
//  - Rng meter display
//  - option to hide coins earned
//  - color options in the purse etc lines
//  - choose the amount of decimal places in shorten nums
//  - more anchor points (alignment enums in renderutils)
//  - 24h instead of 12h for skyblock time
//  - only alert for lines that exist longer than 1s
//

package at.hannibal2.skyhanni.features.gui.customscoreboard

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.events.DebugDataCollectEvent
import at.hannibal2.skyhanni.events.GuiPositionMovedEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.LorenzTickEvent
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.RenderUtils.HorizontalAlignment
import at.hannibal2.skyhanni.utils.RenderUtils.renderStringsAlignedWidth
import at.hannibal2.skyhanni.utils.StringUtils.firstLetterUppercase
import at.hannibal2.skyhanni.utils.TabListData
import com.google.gson.JsonArray
import com.google.gson.JsonPrimitive
import net.minecraftforge.client.GuiIngameForge
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

typealias ScoreboardElementType = Pair<String, HorizontalAlignment>

class CustomScoreboard {

    private var display = emptyList<ScoreboardElementType>()
    private var cache = emptyList<ScoreboardElementType>()
    private val guiName = "Custom Scoreboard"

    @SubscribeEvent
    fun onRenderOverlay(event: GuiRenderEvent.GuiOverlayRenderEvent) {
        if (!isEnabled()) return
        if (display.isEmpty()) return

        RenderBackground().renderBackground()

        val render =
            if (!TabListData.fullyLoaded && displayConfig.cacheScoreboardOnIslandSwitch && cache.isNotEmpty()) {
                cache
            } else {
                display
            }
        config.position.renderStringsAlignedWidth(
            render,
            posLabel = guiName,
            extraSpace = displayConfig.lineSpacing - 10
        )
    }

    @SubscribeEvent
    fun onGuiPositionMoved(event: GuiPositionMovedEvent) {
        if (event.guiName == guiName) {
            if (alignmentConfig.alignRight || alignmentConfig.alignCenterVertically) {
                alignmentConfig.alignRight = false
                alignmentConfig.alignCenterVertically = false
                ChatUtils.chat("Disabled Custom Scoreboard auto-alignment.")
            }
        }
    }

    @SubscribeEvent
    fun onTick(event: LorenzTickEvent) {
        if (!isEnabled()) return

        // Creating the lines
        if (event.isMod(5)) {
            display = createLines().removeEmptyLinesFromEdges()
            if (TabListData.fullyLoaded) {
                cache = display.toList()
            }
        }

        // Remove Known Lines, so we can get the unknown ones
        UnknownLinesHandler.handleUnknownLines()
    }

    companion object {
        internal val config get() = SkyHanniMod.feature.gui.customScoreboard
        internal val displayConfig get() = config.display
        internal val alignmentConfig get() = displayConfig.alignment
        internal val arrowConfig get() = displayConfig.arrow
        internal val eventsConfig get() = displayConfig.events
        internal val mayorConfig get() = displayConfig.mayor
        internal val partyConfig get() = displayConfig.party
        internal val maxwellConfig get() = displayConfig.maxwell
        internal val informationFilteringConfig get() = config.informationFiltering
        internal val backgroundConfig get() = config.background
        internal val devConfig get() = SkyHanniMod.feature.dev
    }

    private fun createLines() = buildList<ScoreboardElementType> {
        for (element in config.scoreboardEntries) {
            val lines = element.getVisiblePair()
            if (lines.isEmpty()) continue

            // Hide consecutive empty lines
            if (
                informationFilteringConfig.hideConsecutiveEmptyLines &&
                lines.first().first == "<empty>" && lastOrNull()?.first?.isEmpty() == true
            ) {
                continue
            }

            // Adds empty lines
            if (lines.first().first == "<empty>") {
                add("" to HorizontalAlignment.LEFT)
                continue
            }

            // Does not display this line
            if (lines.any { it.first == "<hidden>" }) {
                continue
            }

            addAll(lines)
        }
    }

    private fun List<ScoreboardElementType>.removeEmptyLinesFromEdges(): List<ScoreboardElementType> {
        if (config.informationFiltering.hideEmptyLinesAtTopAndBottom) {
            return this
                .dropWhile { it.first.isEmpty() }
                .dropLastWhile { it.first.isEmpty() }
        }
        return this
    }

    // Thank you Apec for showing that the ElementType of the stupid scoreboard is FUCKING HELMET WTF
    @SubscribeEvent
    fun onRenderScoreboard(event: RenderGameOverlayEvent.Post) {
        if (event.type == RenderGameOverlayEvent.ElementType.HELMET) {
            GuiIngameForge.renderObjective = !isHideVanillaScoreboardEnabled()
        }
    }

    @SubscribeEvent
    fun onDebugDataCollect(event: DebugDataCollectEvent) {
        event.title("Custom Scoreboard")
        event.addIrrelevant {
            if (!config.enabled) {
                add("Custom Scoreboard disabled.")
            } else {
                ScoreboardElement.entries.map { element ->
                    add(
                        "${element.name.firstLetterUppercase()} - " +
                            "${element.showWhen.invoke()} - " +
                            "${element.getVisiblePair().map { it.first }}"
                    )
                }
            }
        }
    }

    private fun isEnabled() = LorenzUtils.inSkyBlock && config.enabled
    private fun isHideVanillaScoreboardEnabled() = isEnabled() && displayConfig.hideVanillaScoreboard

    @SubscribeEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        val prefix = "gui.customScoreboard"
        val displayConfigPrefix = "$prefix.displayConfig"
        val displayPrefix = "$prefix.display"

        event.move(
            28,
            "$prefix.displayConfig.showAllActiveEvents",
            "$prefix.displayConfig.eventsConfig.showAllActiveEvents"
        )

        event.move(31, "$displayConfigPrefix.arrowAmountDisplay", "$displayPrefix.arrow.amountDisplay")
        event.move(31, "$displayConfigPrefix.colorArrowAmount", "$displayPrefix.arrow.colorArrowAmount")
        event.move(31, "$displayConfigPrefix.showMagicalPower", "$displayPrefix.maxwell.showMagicalPower")
        event.move(31, "$displayConfigPrefix.compactTuning", "$displayPrefix.maxwell.compactTuning")
        event.move(31, "$displayConfigPrefix.tuningAmount", "$displayPrefix.maxwell.tuningAmount")
        event.move(31, "$displayConfigPrefix.hideVanillaScoreboard", "$displayPrefix.hideVanillaScoreboard")
        event.move(31, "$displayConfigPrefix.displayNumbersFirst", "$displayPrefix.displayNumbersFirst")
        event.move(31, "$displayConfigPrefix.showUnclaimedBits", "$displayPrefix.showUnclaimedBits")
        event.move(31, "$displayConfigPrefix.showMaxIslandPlayers", "$displayPrefix.showMaxIslandPlayers")
        event.move(31, "$displayConfigPrefix.numberFormat", "$displayPrefix.numberFormat")
        event.move(31, "$displayConfigPrefix.lineSpacing", "$displayPrefix.lineSpacing")
        event.move(
            31,
            "$displayConfigPrefix.cacheScoreboardOnIslandSwitch",
            "$displayPrefix.cacheScoreboardOnIslandSwitch"
        )
        // Categories
        event.move(31, "$displayConfigPrefix.alignment", "$displayPrefix.alignment")
        event.move(31, "$displayConfigPrefix.titleAndFooter", "$displayPrefix.titleAndFooter")
        event.move(31, "$prefix.backgroundConfig", "$prefix.background")
        event.move(31, "$prefix.informationFilteringConfig", "$prefix.informationFiltering")
        event.move(31, "$displayConfigPrefix.eventsConfig", "$displayPrefix.events")
        event.move(31, "$prefix.mayorConfig", "$displayPrefix.mayor")
        event.move(31, "$prefix.partyConfig", "$displayPrefix.party")

        event.transform(37, "$displayPrefix.events.eventEntries") { element ->
            val array = element.asJsonArray
            array.add(JsonPrimitive(ScoreboardEvents.QUEUE.name))
            array
        }
        event.transform(40, "$displayPrefix.events.eventEntries") { element ->
            val jsonArray = element.asJsonArray
            val newArray = JsonArray()

            for (jsonElement in jsonArray) {
                val stringValue = jsonElement.asString
                if (stringValue !in listOf("HOT_DOG_CONTEST", "EFFIGIES")) {
                    newArray.add(jsonElement)
                }
            }

            if (jsonArray.any { it.asString in listOf("HOT_DOG_CONTEST", "EFFIGIES") }) {
                newArray.add(JsonPrimitive(ScoreboardEvents.RIFT.name))
            }

            newArray
        }

    }
}
