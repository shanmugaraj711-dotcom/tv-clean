@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ai.promptstudio.tvclean.feature.setup

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ai.promptstudio.tvclean.core.flows.SetupActions
import ai.promptstudio.tvclean.core.flows.SetupExtras
import ai.promptstudio.tvclean.core.flows.SetupIntent
import ai.promptstudio.tvclean.ui.design.component.ActionColumn
import ai.promptstudio.tvclean.ui.design.component.CytBottomSheet
import ai.promptstudio.tvclean.ui.design.component.ListGroup
import ai.promptstudio.tvclean.ui.design.component.Overline
import ai.promptstudio.tvclean.ui.design.component.PrimaryButton
import ai.promptstudio.tvclean.ui.design.component.SheetHeader
import ai.promptstudio.tvclean.ui.design.component.SwitchRow
import ai.promptstudio.tvclean.ui.design.component.TertiaryButton

// RFC-HELPER-1 (test build): Customise's "Home button helper". Only where SetupMachine offers it
// (extras.helperOffer, never in a release build). Switching it on asks first (PLAN 4.2); switching
// it off needs no question. The plan on screen follows the switch.
@Composable
internal fun ColumnScope.HomeHelperGroup(extras: SetupExtras, actions: SetupActions) {
    if (!extras.helperOffer) return
    var consent by remember { mutableStateOf(false) }
    Overline(stringResource(R.string.helper_group), Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp))
    ListGroup {
        SwitchRow(
            stringResource(R.string.helper_row), extras.helperOn,
            { on -> if (on) consent = true else actions.intent(SetupIntent.HomeHelper(false)) },
            subtitle = stringResource(if (extras.helperOn) R.string.helper_row_on else R.string.helper_row_note),
        )
    }
    if (consent) {
        CytBottomSheet({ consent = false }) {
            HomeHelperConsent(onYes = { consent = false; actions.intent(SetupIntent.HomeHelper(true)) }, onNo = { consent = false })
        }
    }
}

// The consent, word for word PLAN 4.2: what stays, what the helper sees, what changes, how to stop it.
@Composable
internal fun ColumnScope.HomeHelperConsent(onYes: () -> Unit, onNo: () -> Unit) {
    SheetHeader(stringResource(R.string.helper_consent_title))
    for (id in listOf(R.string.helper_consent_keep, R.string.helper_consent_sees, R.string.helper_consent_changes, R.string.helper_consent_off)) {
        Body(stringResource(id), Modifier.padding(top = 12.dp))
    }
    ActionColumn(Modifier.padding(top = 20.dp, bottom = 16.dp)) {
        PrimaryButton(stringResource(R.string.helper_consent_yes), onYes)
        TertiaryButton(stringResource(R.string.helper_consent_no), onNo)
    }
}
