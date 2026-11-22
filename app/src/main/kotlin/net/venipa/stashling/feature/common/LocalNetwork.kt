package net.venipa.stashling.feature.common

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import net.venipa.stashling.R
import net.venipa.stashling.ds.ActionButton
import net.venipa.stashling.ds.CardTone
import net.venipa.stashling.ds.TonalCard

// Android 17 (SDK 37) blocks LAN access for apps targeting it unless this runtime permission is granted.
const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

fun Context.localNetworkMissing() = Build.VERSION.SDK_INT >= 37 && checkSelfPermission(LOCAL_NETWORK_PERMISSION) != PackageManager.PERMISSION_GRANTED

/** Shown instead of a generic "unreachable" error when the permission is missing; the button opens the app's settings. */
@Composable
fun LocalNetworkNotice(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TonalCard(modifier, tone = CardTone.Error) {
        Text(stringResource(R.string.local_network_needed))
        ActionButton(stringResource(R.string.local_network_open_settings), {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        }, tonal = true)
    }
}
