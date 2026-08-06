package com.truevault.feature.legal.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.truevault.core.designsystem.component.TvBanner
import com.truevault.core.designsystem.component.TvBannerTone
import com.truevault.core.designsystem.component.TvLoadingState
import com.truevault.core.designsystem.component.TvTextButton
import com.truevault.core.designsystem.component.TvTopAppBar
import com.truevault.core.designsystem.theme.TvSpacing
import com.truevault.core.model.LegalBlock
import com.truevault.core.model.LegalDocumentKind
import com.truevault.core.model.LegalSection
import com.truevault.feature.legal.R

/**
 * The reader for both legal documents.
 *
 * Deliberately not a WebView. The bundled HTML exists for export and for opening outside the app,
 * but what the user reads here is structured Compose text: it is selectable, it scales with the
 * user's chosen size on top of the system font scale, its headings are announced as headings, and
 * there is no browser engine, JavaScript bridge or network stack between the reader and a document
 * they are being asked to rely on.
 *
 * Everything here works with no connection. The online link is an addition, never the only route.
 */
@Composable
fun LegalDocumentScreen(
    kind: LegalDocumentKind,
    onNavigateBack: () -> Unit,
    onOpenOnline: (String) -> Unit,
    onContact: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LegalDocumentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(kind) { viewModel.load(kind) }

    val listState = rememberLazyListState()

    // Scroll progress, reported to sighted users as a bar and to screen readers as a percentage.
    val progress by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) 0f else {
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                ((last + 1).toFloat() / total).coerceIn(0f, 1f)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TvTopAppBar(
                    title = uiState.document?.title.orEmpty(),
                    onNavigateBack = onNavigateBack,
                    actions = {
                        IconButton(
                            onClick = { viewModel.onAction(LegalDocumentAction.TextSmaller) },
                            enabled = uiState.canReduce,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.TextDecrease,
                                contentDescription = stringResource(R.string.legal_document_text_smaller),
                            )
                        }
                        IconButton(
                            onClick = { viewModel.onAction(LegalDocumentAction.TextLarger) },
                            enabled = uiState.canEnlarge,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.TextIncrease,
                                contentDescription = stringResource(R.string.legal_document_text_larger),
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.onAction(
                                    if (uiState.isSearching) {
                                        LegalDocumentAction.SearchClosed
                                    } else {
                                        LegalDocumentAction.SearchOpened
                                    },
                                )
                            },
                        ) {
                            Icon(
                                imageVector = if (uiState.isSearching) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = stringResource(
                                    if (uiState.isSearching) {
                                        R.string.legal_document_close_search
                                    } else {
                                        R.string.legal_document_search
                                    },
                                ),
                            )
                        }
                    },
                )

                if (uiState.isSearching) {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = { viewModel.onAction(LegalDocumentAction.QueryChanged(it)) },
                        label = { Text(stringResource(R.string.legal_document_search_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = TvSpacing.screenHorizontal),
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Read ${(progress * 100).toInt()} percent"
                        },
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> TvLoadingState(modifier = Modifier.padding(innerPadding))

            uiState.failed || uiState.document == null -> TvBanner(
                text = stringResource(R.string.legal_document_unavailable),
                tone = TvBannerTone.Error,
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(TvSpacing.screenHorizontal),
            )

            else -> {
                val document = uiState.document!!
                val scale = uiState.textScale

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = TvSpacing.screenHorizontal,
                        end = TvSpacing.screenHorizontal,
                        top = TvSpacing.standard,
                        bottom = TvSpacing.contentBottom,
                    ),
                    verticalArrangement = Arrangement.spacedBy(TvSpacing.standard),
                ) {
                    item(key = "header") {
                        DocumentHeader(
                            version = document.version,
                            effectiveDate = document.effectiveDate,
                            lastUpdated = document.lastUpdated,
                        )
                    }

                    if (uiState.hasNoMatches) {
                        item(key = "no-matches") {
                            Text(
                                text = stringResource(R.string.legal_document_no_matches, uiState.query),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (uiState.query.isNotBlank()) {
                        item(key = "match-count") {
                            Text(
                                text = stringResource(
                                    R.string.legal_document_matches,
                                    uiState.visibleSections.size,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    items(uiState.visibleSections, key = { it.id }) { section ->
                        SectionContent(section = section, scale = scale)
                    }

                    item(key = "actions") {
                        Spacer(Modifier.height(TvSpacing.section))
                        HorizontalDivider()
                        Spacer(Modifier.height(TvSpacing.small))

                        Text(
                            text = stringResource(R.string.legal_offline_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        val onlineUrl = uiState.onlineUrl
                        if (onlineUrl != null) {
                            TvTextButton(
                                text = stringResource(R.string.legal_open_online),
                                onClick = { onOpenOnline(onlineUrl) },
                            )
                        }

                        TvTextButton(
                            text = stringResource(R.string.legal_document_contact),
                            onClick = onContact,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentHeader(version: String, effectiveDate: String, lastUpdated: String) {
    Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.xs)) {
        if (version.isNotBlank()) {
            Text(
                text = stringResource(R.string.legal_document_version, version),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (effectiveDate.isNotBlank()) {
            Text(
                text = stringResource(R.string.legal_document_effective, effectiveDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (lastUpdated.isNotBlank()) {
            Text(
                text = stringResource(R.string.legal_document_updated, lastUpdated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionContent(section: LegalSection, scale: Float) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.small)) {
            section.heading?.let { heading ->
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleMedium * scale,
                    color = MaterialTheme.colorScheme.onBackground,
                    // Announced as a heading so a screen-reader user can jump between sections
                    // instead of reading a thirty-page document end to end.
                    modifier = Modifier.semantics { this.heading() },
                )
            }

            section.blocks.forEach { block -> BlockContent(block = block, scale = scale) }
        }
    }
}

@Composable
private fun BlockContent(block: LegalBlock, scale: Float) {
    val bodyStyle = MaterialTheme.typography.bodyMedium * scale

    when (block) {
        is LegalBlock.Paragraph -> Text(
            text = block.text,
            style = bodyStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is LegalBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.xs)) {
            block.items.forEach { item -> ListRow(marker = "•", text = item, style = bodyStyle) }
        }

        is LegalBlock.Numbered -> Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.xs)) {
            block.items.forEachIndexed { index, item ->
                ListRow(marker = "${index + 1}.", text = item, style = bodyStyle)
            }
        }

        is LegalBlock.Quote -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = TvSpacing.xs),
        ) {
            Spacer(
                Modifier
                    .width(3.dp)
                    .height(IntrinsicQuoteHeight)
                    .padding(end = TvSpacing.small),
            )
            Text(
                text = block.text,
                style = bodyStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = TvSpacing.small),
            )
        }

        is LegalBlock.Table -> TableContent(block = block, style = bodyStyle)

        LegalBlock.Divider -> HorizontalDivider(modifier = Modifier.padding(vertical = TvSpacing.small))
    }
}

@Composable
private fun ListRow(marker: String, text: String, style: androidx.compose.ui.text.TextStyle) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = marker,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Start,
        )
        Text(
            text = text,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Tables scroll horizontally inside their own container.
 *
 * A wide table must never make the page scroll sideways — on a phone that turns a legal document
 * into something the reader has to fight.
 */
@Composable
private fun TableContent(block: LegalBlock.Table, style: androidx.compose.ui.text.TextStyle) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(TvSpacing.xs),
    ) {
        block.header?.let { header ->
            Row(horizontalArrangement = Arrangement.spacedBy(TvSpacing.standard)) {
                header.forEach { cell ->
                    Text(
                        text = cell,
                        style = style.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.width(TableColumnWidth),
                    )
                }
            }
            HorizontalDivider()
        }

        block.rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TvSpacing.standard)) {
                row.forEach { cell ->
                    Text(
                        text = cell,
                        style = style,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(TableColumnWidth),
                    )
                }
            }
        }
    }
}

private val TableColumnWidth = 148.dp
private val IntrinsicQuoteHeight = 1.dp

/** Scales a text style's size without discarding its other properties. */
private operator fun androidx.compose.ui.text.TextStyle.times(scale: Float) =
    copy(fontSize = fontSize * scale, lineHeight = lineHeightOrDefault() * scale)

private fun androidx.compose.ui.text.TextStyle.lineHeightOrDefault(): TextUnit =
    if (lineHeight.isSpecified) lineHeight else fontSize * 1.5f
