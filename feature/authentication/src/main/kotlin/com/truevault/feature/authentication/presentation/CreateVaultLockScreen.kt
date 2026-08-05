package com.truevault.feature.authentication.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvSectionHeader
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.feature.authentication.R

/**
 * CreateVaultLockScreen.
 *
 * Phase 0 delivers this destination as a navigable, themed shell. Its behaviour is implemented in
 * Phase 1; until then the screen states plainly that the step is not available rather than
 * showing controls that do nothing.
 */
@Composable
fun CreateVaultLockScreen(
    onVaultCreated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = TvSpacing.screenHorizontal, vertical = TvSpacing.standard),
        verticalArrangement = Arrangement.spacedBy(TvSpacing.standard),
    ) {
        TvSectionHeader(title = stringResource(R.string.create_lock_title))
        TvBanner(
            text = stringResource(R.string.create_lock_pending),
            tone = TvBannerTone.Info,
        )
    }
}
