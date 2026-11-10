package ai.promptstudio.tvclean.ui.design.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ai.promptstudio.tvclean.core.flows.LookEdit
import ai.promptstudio.tvclean.core.spec.HighlightChoice
import ai.promptstudio.tvclean.core.spec.LookKeys
import ai.promptstudio.tvclean.ui.design.R

// Advanced (prototype pageAdv): Lift and zoom (shown per LookGates.liftShown), the Highlight
// (home screen 3.0, glass_style), the See-through
// amount, Sky strength, Show launcher names, the settings file with Copy (the full file, dock apps
// too) and the "Not in this app" line. Everything here goes to the TV with the look; a tap only
// chooses.
@Composable
internal fun ColumnScope.LookAdvancedPage(state: LookEditorState, onEdit: (LookEdit) -> Unit, onCopied: (String) -> Unit) {
    val keys = state.keyNames
    LookBody(stringResource(R.string.tvclean_look_adv_lead), Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 16.dp))
    val r = state.rows
    if (r != null) {
        if (r.lift.visible) {
            ListGroup {
                LookSwitchRow(
                    stringResource(R.string.tvclean_look_lift), r.lift.enabled && r.lift.value == true, { onEdit(LookEdit.SetLift(it)) },
                    description = stringResource(R.string.tvclean_look_lift_desc), enabled = r.lift.enabled,
                    lines = listOfNotNull(r.lift.reason?.takeUnless { r.lift.enabled }?.let { reasonText(it) }),
                    warning = r.liftSlowRamMb?.let { lookMsg(R.string.tvclean_look_lift_low_memory, "size" to memoryWords(it)) },
                    keyName = KEY_NAMES["lift"].takeIf { keys },
                )
            }
        }
        val x = ExtraRows(state)
        if (x.highlight) {
            LookSublabel(stringResource(R.string.tvclean_look_highlight))
            LookRadioList(
                listOf(
                    RadioChoice(HighlightChoice.GLASS.id, stringResource(R.string.tvclean_look_highlight_glass), stringResource(R.string.tvclean_look_highlight_glass_desc)),
                    RadioChoice(HighlightChoice.RIM.id, stringResource(R.string.tvclean_look_highlight_rim), stringResource(R.string.tvclean_look_highlight_rim_desc)),
                    RadioChoice(HighlightChoice.HALO.id, stringResource(R.string.tvclean_look_highlight_halo), stringResource(R.string.tvclean_look_highlight_halo_desc)),
                ),
                x.acting.highlight.id, { id -> HighlightChoice.entries.firstOrNull { it.id == id }?.let { h -> x.edit(onEdit) { copy(highlight = h) } } },
                keyName = KEY_NAMES["highlight"].takeIf { keys },
            )
        }
        val seeTitle = stringResource(R.string.tvclean_look_see_amount)
        LookSublabel(seeTitle)
        val options = LookKeys.SEE_THROUGH.map { v ->
            val label = lookMsg(R.string.tvclean_look_percent, "value" to v)
            SegmentOption(v.toString(), label, label)
        }
        val seeReason = r.seeThrough.reason?.let { reasonText(it) }
        SegmentedChoice(
            options, (r.seeThrough.value ?: LookKeys.SEE_THROUGH_DEFAULT).toString(), { onEdit(LookEdit.SetSeeThrough(it.key.toInt())) },
            enabled = r.seeThrough.enabled, disabledReason = seeReason, groupLabel = seeTitle,
        )
        LookBody(
            if (r.seeThrough.enabled) stringResource(R.string.tvclean_look_see_amount_desc) else seeReason.orEmpty(),
            Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp),
        )
        if (keys) KeyNameLine(KEY_NAMES.getValue("see"), Modifier.padding(start = 4.dp))
        ListGroup(Modifier.padding(top = 14.dp)) {
            LookSliderRow(
                stringResource(R.string.tvclean_look_sky_strength), r.skyStrength.value ?: LookKeys.SKY_STRENGTH_DEFAULT, 20..100, 5,
                { onEdit(LookEdit.SetSkyStrength(it)) }, enabled = r.skyStrength.enabled,
                reason = r.skyStrength.reason?.let { reasonText(it) }, keyName = KEY_NAMES["skyStr"].takeIf { keys },
            )
        }
    }
    ListGroup(Modifier.padding(top = 14.dp)) {
        LookSwitchRow(
            stringResource(R.string.tvclean_look_key_names), keys, { onEdit(LookEdit.SetKeyNames(it)) },
            description = stringResource(R.string.tvclean_look_key_names_desc), keyName = stringResource(R.string.tvclean_look_key_names_phone_only).takeIf { keys },
        )
    }
    LookSection(stringResource(R.string.tvclean_look_file_title))
    LookBody(stringResource(R.string.tvclean_look_file_desc), Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp))
    val copy = rememberCopy()
    val title = stringResource(R.string.tvclean_look_file_title)
    val copied = stringResource(R.string.tvclean_look_file_copied)
    MonospaceBlock(
        state.settingsFile.lines(), Modifier.fillMaxWidth(), copyLabel = stringResource(R.string.tvclean_look_file_copy),
        onCopy = { copy(title, state.fullFile); onCopied(copied) },
    )
    LookBody(stringResource(R.string.tvclean_look_not_here), Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp).align(Alignment.Start))
}
