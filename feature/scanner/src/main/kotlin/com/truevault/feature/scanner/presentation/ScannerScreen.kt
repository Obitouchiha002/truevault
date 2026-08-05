package com.truevault.feature.scanner.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvEmptyState
import com.truevault.core.designsystem.component.TvPreviewSurface
import com.truevault.core.designsystem.component.TvTopAppBar
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.feature.scanner.R

/**
 * Privacy scan.
 *
 * The limitation banner is permanent, not a first-run tip: the scanner can only ever see what the
 * user has granted access to, and saying so up front is what keeps the result trustworthy.
 */
@Composable
fun ScannerScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        TvTopAppBar(title = stringResource(R.string.scanner_title))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TvSpacing.screenHorizontal, vertical = TvSpacing.standard),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.section),
        ) {
            TvBanner(
                title = stringResource(R.string.scanner_limits_title),
                text = stringResource(R.string.scanner_limits_body),
                tone = TvBannerTone.Info,
            )

            TvEmptyState(
                icon = Icons.Filled.Radar,
                title = stringResource(R.string.scanner_idle_title),
                description = stringResource(R.string.scanner_idle_body),
            )
        }
    }
}

@Preview(name = "Scanner – idle", showBackground = true, heightDp = 700)
@Composable
private fun ScannerPreview() {
    TvPreviewSurface { ScannerScreen() }
}
