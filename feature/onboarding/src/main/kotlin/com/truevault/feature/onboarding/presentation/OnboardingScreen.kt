package com.truevault.feature.onboarding.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.capabilities.model.TrueVaultProductMode
import com.truevault.core.designsystem.component.TvPreviewSurface
import com.truevault.core.designsystem.component.TvPrimaryButton
import com.truevault.core.designsystem.component.TvTextButton
import com.truevault.core.designsystem.theme.TvMotion
import com.truevault.core.designsystem.theme.TvRadius
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.feature.onboarding.R
import kotlinx.coroutines.launch

/**
 * Four screens, one idea each, and no permission request anywhere in the flow.
 *
 * The last page explains that a forgotten password means lost data *before* the user creates one,
 * because that is the most consequential property of a local-first vault and it must not be a
 * surprise discovered later.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val productMode by viewModel.productMode.collectAsStateWithLifecycle()

    OnboardingContent(
        productMode = productMode,
        onFinished = { viewModel.onFinished(onFinished) },
        modifier = modifier,
    )
}

@Composable
internal fun OnboardingContent(
    productMode: TrueVaultProductMode,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One shared introduction, plus a final screen that reflects what this device actually offers.
    val pages = OnboardingPages + finalPageFor(productMode)
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = TvSpacing.screenHorizontal),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = TvSpacing.small),
            horizontalArrangement = Arrangement.End,
        ) {
            if (!isLastPage) {
                TvTextButton(
                    text = stringResource(R.string.onboarding_skip),
                    onClick = onFinished,
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            OnboardingPage(page = pages[page])
        }

        PageIndicator(
            pageCount = pages.size,
            currentPage = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = TvSpacing.section),
        )

        TvPrimaryButton(
            text = if (isLastPage) {
                stringResource(
                    when (productMode) {
                        TrueVaultProductMode.MODERN -> R.string.onboarding_create_vault
                        TrueVaultProductMode.CORE -> R.string.onboarding_open_core
                    },
                )
            } else {
                stringResource(R.string.onboarding_next)
            },
            onClick = {
                if (isLastPage) {
                    onFinished()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Box(modifier = Modifier.height(TvSpacing.large))
    }
}

@Composable
private fun OnboardingPage(page: OnboardingPageContent) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(TvRadius.card + 8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }

        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = TvSpacing.large),
        )

        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = TvSpacing.standard),
        )
    }
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    val progressLabel = stringResource(R.string.onboarding_progress, currentPage + 1, pageCount)

    Row(
        modifier = modifier.semantics { contentDescription = progressLabel },
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(
                targetValue = if (selected) 24.dp else 8.dp,
                animationSpec = TvMotion.standardSpec(),
                label = "indicatorWidth",
            )
            val color by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                animationSpec = TvMotion.standardSpec(),
                label = "indicatorColor",
            )

            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .background(color, CircleShape),
            )
        }
    }
}

private data class OnboardingPageContent(
    val icon: ImageVector,
    val titleRes: Int,
    val bodyRes: Int,
)

private fun finalPageFor(mode: TrueVaultProductMode) = when (mode) {
    TrueVaultProductMode.MODERN -> OnboardingPageContent(
        icon = Icons.Filled.Apps,
        titleRes = R.string.onboarding_modern_title,
        bodyRes = R.string.onboarding_modern_body,
    )

    TrueVaultProductMode.CORE -> OnboardingPageContent(
        icon = Icons.Filled.Shield,
        titleRes = R.string.onboarding_core_title,
        bodyRes = R.string.onboarding_core_body,
    )
}

private val OnboardingPages = listOf(
    OnboardingPageContent(
        icon = Icons.Filled.Shield,
        titleRes = R.string.onboarding_page1_title,
        bodyRes = R.string.onboarding_page1_body,
    ),
    OnboardingPageContent(
        icon = Icons.Filled.ContentCopy,
        titleRes = R.string.onboarding_page2_title,
        bodyRes = R.string.onboarding_page2_body,
    ),
    OnboardingPageContent(
        icon = Icons.Filled.Radar,
        titleRes = R.string.onboarding_page3_title,
        bodyRes = R.string.onboarding_page3_body,
    ),
    OnboardingPageContent(
        icon = Icons.Filled.LockReset,
        titleRes = R.string.onboarding_page4_title,
        bodyRes = R.string.onboarding_page4_body,
    ),
)

@Preview(name = "Onboarding", showBackground = true, heightDp = 780)
@Composable
private fun OnboardingPreview() {
    TvPreviewSurface {
        OnboardingContent(productMode = TrueVaultProductMode.CORE, onFinished = {})
    }
}
