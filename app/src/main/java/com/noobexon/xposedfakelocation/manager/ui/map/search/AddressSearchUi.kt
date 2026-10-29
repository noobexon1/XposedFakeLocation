package com.noobexon.xposedfakelocation.manager.ui.map.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.geo.GeocodingException
import com.noobexon.xposedfakelocation.manager.geo.Place

/** The app bar's search field; styled for the primary-colored bar like the Settings search. */
@Composable
fun AddressSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val description = stringResource(R.string.cd_search_address)
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.map_search_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = onPrimary,
            unfocusedTextColor = onPrimary,
            cursorColor = onPrimary,
            focusedPlaceholderColor = onPrimary.copy(alpha = 0.7f),
            unfocusedPlaceholderColor = onPrimary.copy(alpha = 0.7f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .semantics { contentDescription = description },
    )
}

/** Results card shown over the map while search is open; nothing is drawn while [SearchResults.Idle]. */
@Composable
fun AddressSearchResults(
    results: SearchResults,
    onPick: (Place) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (results == SearchResults.Idle) return
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column {
            when (results) {
                SearchResults.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                SearchResults.Empty -> Message(stringResource(R.string.map_search_no_results))
                is SearchResults.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Message(
                        text = stringResource(
                            if (results.kind == GeocodingException.Kind.PARSE) R.string.map_search_error_parse
                            else R.string.map_search_error_network
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
                is SearchResults.Found -> LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(results.places) { place ->
                        ListItem(
                            headlineContent = { Text(place.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = if (place.subtitle.isNotEmpty()) {
                                { Text(place.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            } else {
                                null
                            },
                            leadingContent = { Icon(Icons.Outlined.Place, contentDescription = null) },
                            modifier = Modifier.clickable { onPick(place) },
                        )
                    }
                }
                SearchResults.Idle -> Unit
            }
            Text(
                text = stringResource(R.string.map_search_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun Message(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.padding(16.dp),
    )
}
