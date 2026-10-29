package com.noobexon.xposedfakelocation.manager.ui.map

import com.noobexon.xposedfakelocation.data.OPENFREEMAP_DARK_STYLE_URL
import com.noobexon.xposedfakelocation.data.OPENFREEMAP_LIGHT_STYLE_URL

/** The MapLibre style to load: [customUrl] if set, else OpenFreeMap matching the theme. */
fun resolveStyleUrl(customUrl: String, dark: Boolean): String =
    customUrl.ifBlank { if (dark) OPENFREEMAP_DARK_STYLE_URL else OPENFREEMAP_LIGHT_STYLE_URL }
