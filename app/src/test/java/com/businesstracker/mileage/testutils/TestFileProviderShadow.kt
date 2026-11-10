package com.businesstracker.mileage.testutils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File

/**
 * Robolectric shadow for androidx.core.content.FileProvider.
 * Intercepts getUriForFile in unit test environments so tests do not fail on
 * missing synthetic sandbox root mappings while ensuring production code uses
 * pure, unmodified FileProvider.
 */
@Implements(FileProvider::class)
class TestFileProviderShadow {

    companion object {
        @JvmStatic
        @Implementation
        fun getUriForFile(context: Context, authority: String, file: File): Uri {
            return Uri.parse("content://$authority/${file.name}")
        }
    }
}
